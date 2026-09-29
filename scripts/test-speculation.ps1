param([string]$Sdk=$env:ANDROID_HOME,[string]$Serial='emulator-5582',[ValidateSet('unit','pilot','benchmark','lifecycle','draft-cost','guard','profile','missions','ui','audit','energy-audit')][string]$Phase='pilot',[switch]$SkipInstall)
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
$result=Invoke-Adb shell am instrument -w -e speculation $Phase dev.outpost.app.test/dev.outpost.app.GenerationInstrumentation | ForEach-Object { Write-Host $_; $_ }
$dir=Join-Path $project 'evidence/speculation'; New-Item -ItemType Directory -Force -Path $dir | Out-Null
$name="speculation-$Phase.json"
$psi=[System.Diagnostics.ProcessStartInfo]::new($adb); $psi.UseShellExecute=$false; $psi.RedirectStandardOutput=$true
foreach($arg in @('-s',$Serial,'exec-out','run-as','dev.outpost.app','cat',"files/evidence/$name")) { $psi.ArgumentList.Add($arg) }
$process=[System.Diagnostics.Process]::Start($psi); $file=[System.IO.File]::Create((Join-Path $dir $name))
try { $process.StandardOutput.BaseStream.CopyTo($file) } finally { $file.Dispose() }
$process.WaitForExit()
if($process.ExitCode -ne 0 -or -not($result -match "PASS speculation $Phase")) { throw "Speculation $Phase failed; inspect retained evidence." }
if($Phase -eq 'ui') {
    foreach($image in @('speculation-state.png','speculation-answer.png')) {
        $copy=[System.Diagnostics.ProcessStartInfo]::new($adb); $copy.UseShellExecute=$false; $copy.RedirectStandardOutput=$true
        foreach($arg in @('-s',$Serial,'exec-out','run-as','dev.outpost.app','cat',"files/evidence/$image")) { $copy.ArgumentList.Add($arg) }
        $p=[System.Diagnostics.Process]::Start($copy); $f=[System.IO.File]::Create((Join-Path $dir $image))
        try { $p.StandardOutput.BaseStream.CopyTo($f) } finally { $f.Dispose() }
        $p.WaitForExit(); if($p.ExitCode -ne 0) { throw "Could not retrieve $image" }
    }
}
