param([string]$Sdk = $env:ANDROID_HOME, [string]$Serial = 'emulator-5582')
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'This script changes network state only on a test emulator.' }
$project = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'environment.ps1')
$Sdk = Resolve-OutpostSdk $Sdk
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
function Invoke-Adb { & $adb -s $Serial @args; if ($LASTEXITCODE -ne 0) { throw "adb failed: $args" } }
if ((Invoke-Adb shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Target is not an Android emulator.' }
Invoke-Adb shell cmd connectivity airplane-mode enable
Invoke-Adb shell svc wifi disable
Invoke-Adb shell svc data disable
Invoke-Adb install -r "$project\app\build\outputs\apk\debug\app-debug.apk"
Invoke-Adb install -r "$project\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
$result = Invoke-Adb shell am instrument -w dev.outpost.app.test/dev.outpost.app.GenerationInstrumentation
$result | Write-Output
if (-not ($result -match 'PASS:')) { throw 'Instrumentation did not report success.' }
$evidence = Join-Path $project 'evidence'
New-Item -ItemType Directory -Force -Path $evidence | Out-Null
# Native stdout is copied as bytes, preserving PNG data on all PowerShell versions.
foreach ($name in @('home.png','search.png','source.png','checks.json')) {
    $psi = [System.Diagnostics.ProcessStartInfo]::new($adb)
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    foreach ($arg in @('-s',$Serial,'exec-out','run-as','dev.outpost.app','cat',"files/evidence/$name")) { $psi.ArgumentList.Add($arg) }
    $p = [System.Diagnostics.Process]::Start($psi)
    $file = [System.IO.File]::Create((Join-Path $evidence $name))
    try { $p.StandardOutput.BaseStream.CopyTo($file) } finally { $file.Dispose() }
    $p.WaitForExit()
    if ($p.ExitCode -ne 0) { throw "Cannot retrieve $name" }
}
Invoke-Adb shell am start -n dev.outpost.app/.MainActivity
