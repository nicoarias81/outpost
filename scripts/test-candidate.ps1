param([string]$Sdk=$env:ANDROID_HOME,[string]$Serial='',[ValidateSet('Outpost35','Pixel10Pro')][string]$Target='Outpost35',[switch]$SkipInstall,[ValidateSet('admission','cache','pilot','heldout','timing')][string]$Phase='admission')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
. (Join-Path $project 'scripts/environment.ps1')
$adb=Join-Path (Resolve-OutpostSdk $Sdk) 'platform-tools/adb.exe'
. (Join-Path $project 'scripts/test-target.ps1')
$device=Resolve-OutpostTestTarget -Adb $adb -Target $Target -Serial $Serial
$Serial=$device.Serial
function Invoke-Adb { & $adb -s $Serial @args; if($LASTEXITCODE -ne 0){throw 'adb failed'} }
& (Join-Path $project 'eval/check-build.ps1')
$apks=@(
    @{Package='dev.outpost.app';File="$project/app/build/outputs/apk/debug/app-debug.apk"},
    @{Package='dev.outpost.app.test';File="$project/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"}
)
foreach($apk in $apks){
    if(-not $SkipInstall){Invoke-Adb install -r $apk.File}
    $paths=@(Invoke-Adb shell pm path $apk.Package)
    if($paths.Count -ne 1 -or -not $paths[0].StartsWith('package:')){throw 'APK path mismatch'}
    $actual=((Invoke-Adb shell sha256sum $paths[0].Substring(8)) -split '\s+')[0]
    if($actual -ne (Get-FileHash -LiteralPath $apk.File -Algorithm SHA256).Hash.ToLowerInvariant()){throw 'Installed APK mismatch'}
}
$runId='candidate-'+$Phase+'-'+(Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$out=Join-Path $project "evidence/runs/$runId"
New-Item -ItemType Directory -Path $out | Out-Null
$fixture=Join-Path $project 'app/src/androidTest/assets/candidates/missions-v1.json'
$lock=Join-Path $project 'app/src/androidTest/assets/candidates/spark17-lock.json'
$head=(& git -C $project rev-parse HEAD).Trim()
$identity=[ordered]@{
    runId=$runId;phase=$Phase;target=$Target;serial=if($Target -eq 'Outpost35'){$Serial}else{'registered-pixel-ending-'+$Serial.Substring($Serial.Length-4)};startedUtc=[DateTime]::UtcNow.ToString('o');gitHead=$head
    sourceStatus=@(& git -C $project status --short)
    appSha256=(Get-FileHash -LiteralPath $apks[0].File -Algorithm SHA256).Hash.ToLowerInvariant()
    testApkSha256=(Get-FileHash -LiteralPath $apks[1].File -Algorithm SHA256).Hash.ToLowerInvariant()
    mainSourcesSha256=Get-OutpostSourceFingerprint main;testSourcesSha256=Get-OutpostSourceFingerprint androidTest
    fixtureSha256=(Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash.ToLowerInvariant()
    lockSha256=(Get-FileHash -LiteralPath $lock -Algorithm SHA256).Hash.ToLowerInvariant()
    deviceProperties=$device.Properties;radioSettingsBefore=$device.RadioSettings;conditionsBefore=Get-OutpostTargetConditions -Adb $adb -Serial $Serial
    buildReceipt=(Get-Content -LiteralPath (Join-Path $project '.local/build-receipt.json') -Raw|ConvertFrom-Json)
    modelExecution=$true;scope='Pinned Spark research and Bonsai control on the recorded Android target. Product has no INTERNET permission. Phone radio settings and product selection are preserved. Same-version research APK is identified by bytes. USB charging is not a battery-life experiment.'
}
$identity|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $out 'run.json') -Encoding utf8
Copy-Item -LiteralPath $fixture -Destination (Join-Path $out 'missions-v1.json')
Copy-Item -LiteralPath $lock -Destination (Join-Path $out 'spark17-lock.json')
foreach($source in @('app/src/main/cpp/engine.cpp','app/src/main/java/dev/outpost/app/NativeEngine.java','app/src/main/java/dev/outpost/app/ChatPrompt.java','app/src/androidTest/java/dev/outpost/app/CandidateChecks.java','scripts/test-candidate.ps1','scripts/test-target.ps1')){
    $target=Join-Path $out "source/$source";New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent)|Out-Null
    Copy-Item -LiteralPath (Join-Path $project $source) -Destination $target
}
$executionError=$null;$result=@()
try{$result=Invoke-Adb shell am instrument -w -e candidate_run $runId -e candidate_phase $Phase -e candidate_target $Target dev.outpost.app.test/dev.outpost.app.GenerationInstrumentation | ForEach-Object {Write-Host $_;$_}}
catch{$executionError=$_;$_|Out-String|Set-Content -LiteralPath (Join-Path $out 'execution-error.txt') -Encoding utf8}
finally{
    $result|Set-Content -LiteralPath (Join-Path $out 'instrumentation.log') -Encoding utf8
    $psi=[System.Diagnostics.ProcessStartInfo]::new($adb);$psi.UseShellExecute=$false;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
    $psi.Arguments=ConvertTo-OutpostArgumentString @('-s',$Serial,'exec-out','run-as','dev.outpost.app','cat',"files/evidence/candidates/$runId/candidate-checks.json")
    $process=[System.Diagnostics.Process]::Start($psi);$file=[System.IO.File]::Create((Join-Path $out 'candidate-checks.json'))
    try{$process.StandardOutput.BaseStream.CopyTo($file)}finally{$file.Dispose()}
    $process.WaitForExit();if($process.ExitCode -ne 0){Write-Warning $process.StandardError.ReadToEnd()}
    try {
        $after=Resolve-OutpostTestTarget -Adb $adb -Target $Target -Serial $Serial
        [ordered]@{radioSettings=$after.RadioSettings;conditions=Get-OutpostTargetConditions -Adb $adb -Serial $Serial}|ConvertTo-Json -Depth 6|Set-Content -LiteralPath (Join-Path $out 'conditions-after.json') -Encoding utf8
    } catch { Write-Warning "Could not capture final target conditions: $_" }
    Write-Output "Evidence: $out"
}
if($executionError){throw $executionError}
$report=Get-Content -LiteralPath (Join-Path $out 'candidate-checks.json') -Raw|ConvertFrom-Json
if(-not $report.passed -or $report.runId -ne $runId -or $report.fixtureSha256 -ne $identity.fixtureSha256 -or -not($result -match "PASS candidate $Phase")){throw 'Candidate run failed or identity mismatch; inspect preserved evidence'}
