param([string]$Sdk=$env:ANDROID_HOME,[string]$Serial='emulator-5582',[ValidateSet('calibrate','batch','cache','missions')][string]$Phase='calibrate',[switch]$SkipInstall)
$ErrorActionPreference='Stop'
if($Serial -notmatch '^emulator-\d+$') { throw 'Only emulator targets are permitted.' }
$project=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'environment.ps1')
$Sdk = Resolve-OutpostSdk $Sdk
$adb=Join-Path $Sdk 'platform-tools/adb.exe'
function Invoke-Adb { & $adb -s $Serial @args; if($LASTEXITCODE -ne 0) { throw "adb failed: $args" } }
if((Invoke-Adb shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Target is not an emulator.' }
Invoke-Adb shell cmd connectivity airplane-mode enable
Invoke-Adb shell svc wifi disable
Invoke-Adb shell svc data disable
if(-not $SkipInstall) {
    Invoke-Adb install -r "$project/app/build/outputs/apk/debug/app-debug.apk"
    Invoke-Adb install -r "$project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
}
$result=Invoke-Adb shell am instrument -w -e runtime $Phase dev.outpost.app.test/dev.outpost.app.GenerationInstrumentation | ForEach-Object { Write-Host $_; $_ }
$dir=Join-Path $project 'evidence/strata'; New-Item -ItemType Directory -Force -Path $dir | Out-Null
$name="runtime-$Phase.json"
$psi=[System.Diagnostics.ProcessStartInfo]::new($adb); $psi.UseShellExecute=$false; $psi.RedirectStandardOutput=$true
$psi.Arguments = "-s $Serial exec-out run-as dev.outpost.app cat `"files/evidence/$name`""
$process=[System.Diagnostics.Process]::Start($psi)
$file=[System.IO.File]::Create((Join-Path $dir $name))
try { $process.StandardOutput.BaseStream.CopyTo($file) } finally { $file.Dispose() }
$process.WaitForExit()
if($process.ExitCode -ne 0 -or -not($result -match "PASS runtime $Phase")) { throw "Runtime $Phase failed; evidence retained." }
