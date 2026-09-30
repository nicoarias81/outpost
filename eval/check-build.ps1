$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
. (Join-Path $root 'scripts/environment.ps1')
$receipt=Get-Content -LiteralPath (Join-Path $root '.local/build-receipt.json') -Raw | ConvertFrom-Json
if($receipt.mainSourcesSha256 -ne (Get-OutpostSourceFingerprint main) -or
    $receipt.testSourcesSha256 -ne (Get-OutpostSourceFingerprint androidTest)) {
    throw 'Source files differ from the successful build receipt. Rebuild before evaluation.'
}
if($receipt.appSha256 -ne (Get-FileHash -LiteralPath (Join-Path $root 'app/build/outputs/apk/debug/app-debug.apk') -Algorithm SHA256).Hash.ToLowerInvariant() -or
    $receipt.testApkSha256 -ne (Get-FileHash -LiteralPath (Join-Path $root 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk') -Algorithm SHA256).Hash.ToLowerInvariant()) {
    throw 'APK files differ from the successful build receipt.'
}
Write-Output 'Build receipt matches current source and app/test APK bytes.'
