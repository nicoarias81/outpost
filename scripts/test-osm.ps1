param([string]$Sdk=$env:ANDROID_HOME,[string]$Serial='emulator-5582',[switch]$SkipInstall,[switch]$Generate)
$ErrorActionPreference='Stop'
if($Serial -notmatch '^emulator-[0-9]+$') { throw 'Only emulator targets are permitted.' }
$project=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'environment.ps1')
$Sdk=Resolve-OutpostSdk $Sdk
$adb=Join-Path $Sdk 'platform-tools/adb.exe'
function Invoke-Adb { & $adb -s $Serial @args; if($LASTEXITCODE -ne 0) { throw 'adb failed' } }
if((Invoke-Adb shell getprop ro.kernel.qemu).Trim() -ne '1' -or (Invoke-Adb shell getprop ro.boot.qemu.avd_name).Trim() -ne 'Outpost35') { throw 'Dedicated Outpost emulator required.' }
if((Invoke-Adb shell getprop sys.boot_completed).Trim() -ne '1') { throw 'Emulator boot incomplete.' }
if((Invoke-Adb shell settings get global airplane_mode_on).Trim() -ne '1' -or (Invoke-Adb shell settings get global wifi_on).Trim() -ne '0' -or (Invoke-Adb shell settings get global mobile_data).Trim() -ne '0') { throw 'The evaluation emulator must be offline.' }
& (Join-Path $project 'eval/check-build.ps1')
$apks=@(
    @{Package='dev.outpost.app';File="$project/app/build/outputs/apk/debug/app-debug.apk"},
    @{Package='dev.outpost.app.test';File="$project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"}
)
foreach($apk in $apks) {
    if(-not $SkipInstall) { Invoke-Adb install -r $apk.File }
    $path=(Invoke-Adb shell pm path $apk.Package).Trim()
    if(-not $path.StartsWith('package:')) { throw 'Required APK missing.' }
    $installed=((Invoke-Adb shell sha256sum $path.Substring(8)) -split '\s+')[0]
    if($installed -ne (Get-FileHash -LiteralPath $apk.File -Algorithm SHA256).Hash.ToLowerInvariant()) { throw 'Installed APK mismatch.' }
}
$runId='osm-'+(Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$out=Join-Path $project "evidence/runs/$runId"
New-Item -ItemType Directory -Path $out | Out-Null
$grantReceiver='dev.outpost.app.test/dev.outpost.app.FixtureGrantReceiver'
try {
    $generation=if($Generate){'true'}else{'false'}
    $result=Invoke-Adb shell am instrument -w -e osm_run $runId -e osm_generate $generation dev.outpost.app.test/dev.outpost.app.GenerationInstrumentation | ForEach-Object { Write-Host $_; $_ }
} catch {
    $_ | Out-String | Set-Content -LiteralPath (Join-Path $out 'setup-error.txt') -Encoding utf8
    throw
} finally { Invoke-Adb shell am broadcast --include-stopped-packages -n $grantReceiver --es run_id $runId --es operation revoke }
$result | Set-Content -LiteralPath (Join-Path $out 'instrumentation.log') -Encoding utf8
$files=@('osm-checks.json','osm-browser.png','osm-source.png')
if($Generate){$files+='osm-chat.png'}
foreach($name in $files) {
    $psi=[System.Diagnostics.ProcessStartInfo]::new($adb)
    $psi.UseShellExecute=$false;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
    $psi.Arguments=ConvertTo-OutpostArgumentString @('-s',$Serial,'exec-out','run-as','dev.outpost.app','cat',"files/evidence/osm/$runId/$name")
    $process=[System.Diagnostics.Process]::Start($psi)
    $file=[System.IO.File]::Create((Join-Path $out $name))
    try{$process.StandardOutput.BaseStream.CopyTo($file)}finally{$file.Dispose()}
    $process.WaitForExit()
    if($process.ExitCode -ne 0){Write-Warning "No ${name}: $($process.StandardError.ReadToEnd())"}
}
[ordered]@{
    runId=$runId;serial=$Serial;modelExecution=[bool]$Generate
    appSha256=(Get-FileHash -LiteralPath $apks[0].File -Algorithm SHA256).Hash.ToLowerInvariant()
    testApkSha256=(Get-FileHash -LiteralPath $apks[1].File -Algorithm SHA256).Hash.ToLowerInvariant()
    mainSourcesSha256=Get-OutpostSourceFingerprint main
    testSourcesSha256=Get-OutpostSourceFingerprint androidTest
    scope='OSM XML/Overpass JSON parsing, typed evidence, SAF and optional local chat; synthetic data; emulator only.'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $out 'run.json') -Encoding utf8
Write-Output "Evidence: $out"
if(-not($result -match 'PASS OSM:')) { throw 'OSM checks failed; inspect retained evidence.' }
