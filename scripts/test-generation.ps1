param([string]$Sdk = $env:ANDROID_HOME, [string]$Serial = 'emulator-5582')
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'Only emulator serials are permitted.' }
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
$model = Get-Content -LiteralPath "$project\model-lock.json" -Raw | ConvertFrom-Json
$file = "$project\.local\models\$($model.file)"
if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLower() -ne $model.sha256) { throw 'Model hash mismatch.' }
Invoke-Adb shell mkdir -p /sdcard/Android/data/dev.outpost.app/files
Invoke-Adb push $file /sdcard/Android/data/dev.outpost.app/files/test-model.gguf
$result = Invoke-Adb shell am instrument -w -e generation true dev.outpost.app.test/dev.outpost.app.GenerationInstrumentation
$result | Write-Output
if (-not ($result -match 'PASS:')) { throw 'Generation tests failed; inspect generation-failure.json and logcat.' }
foreach ($name in @('generation.png','generation-checks.json')) {
    $psi = [System.Diagnostics.ProcessStartInfo]::new($adb)
    $psi.UseShellExecute = $false; $psi.RedirectStandardOutput = $true
    $psi.Arguments = "-s $Serial exec-out run-as dev.outpost.app cat `"files/evidence/$name`""
    $p = [System.Diagnostics.Process]::Start($psi)
    $output = [System.IO.File]::Create((Join-Path $project "evidence\$name"))
    try { $p.StandardOutput.BaseStream.CopyTo($output) } finally { $output.Dispose() }
    $p.WaitForExit(); if ($p.ExitCode -ne 0) { throw "Cannot retrieve $name" }
}
Invoke-Adb shell am start -n dev.outpost.app/.MainActivity
