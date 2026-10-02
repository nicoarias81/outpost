param([switch]$Offline)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'environment.ps1')
$settings=Get-OutpostSettings
$env:JAVA_HOME=$settings.javaHome
$env:GRADLE_USER_HOME=$settings.gradleHome
$env:PYTHONUTF8='1'
$vendor=Join-Path $project '.local/llama.cpp'
$revision=& git -C $vendor rev-parse HEAD
if($LASTEXITCODE -ne 0 -or $revision.Trim() -ne (Get-Content -LiteralPath "$project/llama-revision.txt" -Raw).Trim()){throw 'Backend revision mismatch'}
& git -C $vendor diff --quiet HEAD --
if($LASTEXITCODE -ne 0){throw 'Backend source must remain pinned'}
$lock=Get-Content -LiteralPath "$project/pdfbox-lock.json" -Raw|ConvertFrom-Json
foreach($artifact in $lock.artifacts){
    $cache=Join-Path $settings.gradleHome "caches/modules-2/files-2.1/$($artifact.group)/$($artifact.artifact)/$($artifact.version)"
    $matches=@(Get-ChildItem -LiteralPath $cache -Recurse -File|Where-Object {$_.Name -eq $artifact.file -and $_.Length -eq $artifact.bytes})
    if(-not($matches|Where-Object {(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -eq $artifact.sha256})){throw "Dependency mismatch: $($artifact.file)"}
}
$before=Get-OutpostSourceFingerprint main
$argsList=@('-p',$project,'--no-daemon','-PoutpostReleaseTest',':app:assembleRelease',':app:bundleRelease',':app:assembleReleaseQa',':app:assembleReleaseQaAndroidTest',':app:lintRelease')
if($Offline){$argsList+='--offline'}
& $settings.gradleExecutable @argsList
if($LASTEXITCODE -ne 0){throw 'Release build or lint failed'}
if($before -ne (Get-OutpostSourceFingerprint main)){throw 'Inputs changed during release build'}
$id='release-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$out=Join-Path $project ".local/$id"
New-Item -ItemType Directory -Path $out|Out-Null
& $settings.pythonExecutable "$project/eval/audit-release.py" --project $project --output "$out/audit.json"
if($LASTEXITCODE -ne 0){throw 'Release artifact audit failed'}
$files=@('app/build/outputs/apk/release/app-release-unsigned.apk','app/build/outputs/bundle/release/app-release.aab','app/build/outputs/apk/releaseQa/app-releaseQa.apk','app/build/outputs/apk/androidTest/releaseQa/app-releaseQa-androidTest.apk')
$artifacts=@()
foreach($file in $files){
    $path=Join-Path $project $file
    Copy-Item -LiteralPath $path -Destination $out
    $artifacts+=@{path=$file;bytes=(Get-Item -LiteralPath $path).Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()}
}
$record=[ordered]@{schemaVersion=1;runId=$id;builtAtUtc=[DateTime]::UtcNow.ToString('o');mainSourcesSha256=$before;backendRevision=$revision.Trim();artifacts=$artifacts;scope='Unsigned production candidate; debug-signed non-debuggable QA twin. Not approved for distribution or exact signed-device acceptance.'}
$record|ConvertTo-Json -Depth 5|Set-Content -LiteralPath "$out/receipt.json" -Encoding utf8
$record|ConvertTo-Json -Depth 5|Set-Content -LiteralPath "$project/.local/release-build-receipt.json" -Encoding utf8
Write-Output "Release candidate archived at $out"
