param([string]$JavaHome=$env:JAVA_HOME,[string]$GradleHome=$env:GRADLE_USER_HOME,[switch]$Offline)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'environment.ps1')
$settings=Get-OutpostSettings
if(-not $JavaHome) { $JavaHome=$settings.javaHome }
if(-not $GradleHome) { $GradleHome=$settings.gradleHome }
if(-not $JavaHome -or -not(Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'Configure JAVA_HOME or javaHome in .local/developer-settings.json.' }
$env:JAVA_HOME=$JavaHome
if($GradleHome) { $env:GRADLE_USER_HOME=$GradleHome }
$gradle=Join-Path $project 'gradlew.bat'
if($settings.gradleExecutable) { $gradle=$settings.gradleExecutable }
if(-not(Test-Path -LiteralPath $gradle)) { throw 'Configured Gradle executable does not exist.' }
$vendor=Join-Path $project '.local/llama.cpp'
$revision=(& git -C $vendor rev-parse HEAD)
if($LASTEXITCODE -ne 0 -or $revision.Trim() -ne (Get-Content -LiteralPath (Join-Path $project 'llama-revision.txt') -Raw).Trim()) { throw 'Pinned backend revision mismatch or unreadable vendor repository.' }
& git -C $vendor diff --quiet HEAD --
if($LASTEXITCODE -ne 0) { throw 'Vendor source changes must be reviewed explicitly before building.' }
$pdfLock=Get-Content -Raw -LiteralPath (Join-Path $project 'pdfbox-lock.json') | ConvertFrom-Json
function Confirm-PdfDependencies {
    $cacheRoot=if($GradleHome){$GradleHome}else{Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle'}
    foreach($artifact in $pdfLock.artifacts) {
        $cache=Join-Path $cacheRoot "caches/modules-2/files-2.1/$($artifact.group)/$($artifact.artifact)/$($artifact.version)"
        $matches=@(Get-ChildItem -LiteralPath $cache -Recurse -File | Where-Object { $_.Name -eq $artifact.file -and $_.Length -eq $artifact.bytes })
        if(-not($matches | Where-Object { (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -eq $artifact.sha256 })) { throw "Pinned PDF dependency mismatch: $($artifact.file)" }
    }
}
$beforeMain=Get-OutpostSourceFingerprint main
$beforeTest=Get-OutpostSourceFingerprint androidTest
$arguments=@('-p',$project,'--no-daemon',':app:assembleDebug',':app:assembleDebugAndroidTest',':app:lintDebug')
if($Offline) { $arguments+='--offline' }
& $gradle @arguments
if($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
Confirm-PdfDependencies
if($beforeMain -ne (Get-OutpostSourceFingerprint main) -or $beforeTest -ne (Get-OutpostSourceFingerprint androidTest)) {
    throw 'Sources changed during the build; no fresh build receipt was issued.'
}
$gradleText=Get-Content -LiteralPath (Join-Path $project 'app/build.gradle') -Raw
$receipt=[ordered]@{
    schemaVersion=1
    builtAtUtc=[DateTime]::UtcNow.ToString('o')
    versionName=[regex]::Match($gradleText,"versionName\s+'([^']+)'").Groups[1].Value
    versionCode=[int][regex]::Match($gradleText,'versionCode\s+(\d+)').Groups[1].Value
    backendRevision=$revision.Trim()
    mainSourcesSha256=$beforeMain
    testSourcesSha256=$beforeTest
    appSha256=(Get-FileHash -LiteralPath (Join-Path $project 'app/build/outputs/apk/debug/app-debug.apk') -Algorithm SHA256).Hash.ToLowerInvariant()
    testApkSha256=(Get-FileHash -LiteralPath (Join-Path $project 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk') -Algorithm SHA256).Hash.ToLowerInvariant()
    scope='Successful local Gradle build and lint with unchanged captured inputs; not a signed reproducible-build attestation.'
}
$receipt | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $project '.local/build-receipt.json') -Encoding utf8
Write-Output 'Build receipt saved in .local/build-receipt.json'
