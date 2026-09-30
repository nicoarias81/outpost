param([switch]$Verify)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
$gradle=Join-Path $project 'app/build.gradle'
if(-not(Test-Path -LiteralPath $gradle)) { throw "Cannot find $gradle" }
$text=[IO.File]::ReadAllText($gradle)
$name=[regex]::Match($text,"versionName\s+'([^']+)'").Groups[1].Value
$code=[regex]::Match($text,'versionCode\s+(\d+)').Groups[1].Value
if(-not $name -or -not $code) { throw 'Cannot read the application version.' }
$artifact="outpost-$name-emulator-debug.apk"
$dist=Join-Path $project 'dist'
$target=Join-Path $dist $artifact
$sidecar="$target.sha256"
if($Verify) {
    if(-not(Test-Path -LiteralPath $target)) { throw "No published artifact for $name." }
    if(-not(Test-Path -LiteralPath $sidecar)) { throw "No checksum sidecar beside $artifact." }
    $expected=(Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
    $line=[IO.File]::ReadAllText($sidecar).Trim()
    if($line -notmatch '^([0-9a-f]{64})  (.+)$' -or $Matches[1] -ne $expected -or $Matches[2] -ne $artifact) {
        throw 'Checksum sidecar disagrees with the artifact hash or filename.'
    }
    Write-Output "verified $artifact"
    Write-Output "sha256 $expected"
    exit 0
}
. (Join-Path $PSScriptRoot 'environment.ps1')
$apk=Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk'
$receiptPath=Join-Path $project '.local/build-receipt.json'
if(-not(Test-Path -LiteralPath $apk) -or -not(Test-Path -LiteralPath $receiptPath)) { throw 'Run scripts/build.ps1 successfully before publishing.' }
$receipt=Get-Content -LiteralPath $receiptPath -Raw | ConvertFrom-Json
$hash=(Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
if($receipt.versionName -ne $name -or $receipt.versionCode -ne [int]$code -or $receipt.appSha256 -ne $hash -or
    $receipt.mainSourcesSha256 -ne (Get-OutpostSourceFingerprint main)) {
    throw 'The app APK does not match the successful build receipt and current production inputs. Rebuild first.'
}
if(Test-Path -LiteralPath $target) {
    if((Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hash) {
        throw 'A different artifact already exists for this version. Preserve it and use a new version.'
    }
}
New-Item -ItemType Directory -Force -Path $dist | Out-Null
Copy-Item -LiteralPath $apk -Destination $target -Force
[IO.File]::WriteAllText($sidecar,("$hash  $artifact" + [char]10))
$receipt | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $dist "outpost-$name-build.json") -Encoding utf8
Write-Output "published $artifact (versionCode $code)"
Write-Output "sha256 $hash"
