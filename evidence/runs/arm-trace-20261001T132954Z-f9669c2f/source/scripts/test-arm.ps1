param([string]$Sdk=$env:ANDROID_HOME,[switch]$SkipInstall,[ValidateSet('numeric','model','tune','confirm','controller','lifecycle','trace')][string]$Phase='numeric',[ValidateRange(1,8)][int]$Threads=4,[ValidateSet(1,2,4,8)][int]$Width=4,[ValidateSet(1,2,4)][int]$DecodeRows=1,[switch]$PersistentThreads,[ValidateSet('none','performance')][string]$Affinity='none',[ValidateRange(0,8)][int]$PromptThreads=0)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'environment.ps1')
. (Join-Path $PSScriptRoot 'test-target.ps1')
$adb=Join-Path (Resolve-OutpostSdk $Sdk) 'platform-tools/adb.exe'
$device=Resolve-OutpostTestTarget -Adb $adb -Target Pixel10Pro
$serial=$device.Serial
function Invoke-ArmAdb { & $adb -s $serial @args; if($LASTEXITCODE -ne 0){throw 'Pixel ADB operation failed'} }
& (Join-Path $project 'eval/check-build.ps1')
$apks=@(@{package='dev.outpost.app';file='app/build/outputs/apk/debug/app-debug.apk'},@{package='dev.outpost.app.test';file='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'})
foreach($apk in $apks){
    $file=Join-Path $project $apk.file;if(-not $SkipInstall){Invoke-ArmAdb install -r $file}
    $path=(Invoke-ArmAdb shell pm path $apk.package).Trim();if(-not $path.StartsWith('package:')){throw 'Missing APK'}
    if(((Invoke-ArmAdb shell sha256sum $path.Substring(8)) -split '\s+')[0] -ne (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()){throw 'Installed APK mismatch'}
}
Invoke-ArmAdb shell am force-stop dev.outpost.app
Invoke-ArmAdb shell input keyevent KEYCODE_WAKEUP

$run='arm-'+$Phase+'-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$out=Join-Path $project "evidence/runs/$run";New-Item -ItemType Directory -Path $out|Out-Null
[ordered]@{runId=$run;phase=$Phase;target='Pixel10Pro';startedUtc=[DateTime]::UtcNow.ToString('o');gitHead=(& git -C $project rev-parse HEAD).Trim();sourceStatus=@(& git -C $project status --short);buildReceipt=(Get-Content -LiteralPath "$project/.local/build-receipt.json" -Raw|ConvertFrom-Json);deviceProperties=$device.Properties;radioSettings=$device.RadioSettings;conditionsBefore=Get-OutpostTargetConditions -Adb $adb -Serial $serial;threads=$Threads;width=$Width;decodeRows=$DecodeRows;scope='Foreground ARM parity/performance research; per-call thermal/screen gates, unchanged model/prompt/output budget. No personal app content is read by the replacement Activity.'}|ConvertTo-Json -Depth 9|Set-Content -LiteralPath (Join-Path $out 'run.json') -Encoding utf8
foreach($name in @('app/src/main/cpp/q2_kernel.c','app/src/main/cpp/q2_kernel.h','app/src/main/cpp/q2_arm.c','app/src/main/cpp/q2_arm.h','app/src/main/cpp/q2_batch.c','app/src/main/cpp/CMakeLists.txt','app/src/main/cpp/engine.cpp','app/src/main/java/dev/outpost/app/NativeEngine.java','app/src/main/java/dev/outpost/app/ChatPrompt.java','app/src/androidTest/java/dev/outpost/app/ArmKernelChecks.java','app/src/androidTest/java/dev/outpost/app/GenerationInstrumentation.java','scripts/test-arm.ps1')){
    $destination=Join-Path $out "source/$name";New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent)|Out-Null;Copy-Item -LiteralPath (Join-Path $project $name) -Destination $destination
}
if($PromptThreads -eq 0){$PromptThreads=$Threads}
$poolFlag=if($PersistentThreads){'true'}else{'false'}
$result=@();$failure=$null
try{$result=Invoke-ArmAdb shell am instrument -w -e arm_run $run -e arm_phase $Phase -e arm_threads $Threads -e arm_width $Width -e arm_rows $DecodeRows -e arm_pools $poolFlag -e arm_affinity $Affinity -e arm_prompt_threads $PromptThreads dev.outpost.app.test/dev.outpost.app.GenerationInstrumentation | ForEach-Object {Write-Host $_;$_}}
catch{$failure=$_;$_|Out-String|Set-Content -LiteralPath (Join-Path $out 'execution-error.txt') -Encoding utf8}
finally {
    $result|Set-Content -LiteralPath (Join-Path $out 'instrumentation.log') -Encoding utf8
    $psi=[Diagnostics.ProcessStartInfo]::new($adb);$psi.UseShellExecute=$false;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
    $psi.Arguments=ConvertTo-OutpostArgumentString @('-s',$serial,'exec-out','run-as','dev.outpost.app','cat',"files/evidence/arm/$run/arm-checks.json")
    $process=[Diagnostics.Process]::Start($psi);$file=[IO.File]::Create((Join-Path $out 'arm-checks.json'))
    try{$process.StandardOutput.BaseStream.CopyTo($file)}finally{$file.Dispose()};$process.WaitForExit()
    if($process.ExitCode -ne 0){Write-Warning $process.StandardError.ReadToEnd()}
    Get-OutpostTargetConditions -Adb $adb -Serial $serial|ConvertTo-Json -Depth 5|Set-Content -LiteralPath (Join-Path $out 'conditions-after.json') -Encoding utf8
    Invoke-ArmAdb shell am force-stop dev.outpost.app
    Write-Output "Evidence: $out"
}
if($failure){throw $failure}
$report=Get-Content -LiteralPath (Join-Path $out 'arm-checks.json') -Raw|ConvertFrom-Json
if(-not $report.passed -or $report.runId -ne $run -or -not($result -match "PASS ARM $Phase")){throw 'ARM run failed; inspect preserved evidence'}
