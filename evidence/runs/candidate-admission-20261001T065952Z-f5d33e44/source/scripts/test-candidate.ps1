param([string]$Sdk=$env:ANDROID_HOME,[string]$Serial='emulator-5582',[switch]$SkipInstall,[ValidateSet('admission','pilot','heldout','timing')][string]$Phase='admission')
$ErrorActionPreference='Stop'
if($Serial -ne 'emulator-5582'){throw 'Only the dedicated Outpost emulator is admitted.'}
$project=Split-Path $PSScriptRoot -Parent
. (Join-Path $project 'scripts/environment.ps1')
$adb=Join-Path (Resolve-OutpostSdk $Sdk) 'platform-tools/adb.exe'
function Invoke-Adb { & $adb -s $Serial @args; if($LASTEXITCODE -ne 0){throw 'adb failed'} }
if((Invoke-Adb shell getprop ro.kernel.qemu).Trim() -ne '1' -or (Invoke-Adb shell getprop ro.boot.qemu.avd_name).Trim() -ne 'Outpost35'){throw 'Incorrect AVD'}
if((Invoke-Adb shell getprop sys.boot_completed).Trim() -ne '1'){throw 'Emulator not booted'}
if((Invoke-Adb shell settings get global airplane_mode_on).Trim() -ne '1' -or (Invoke-Adb shell settings get global wifi_on).Trim() -ne '0' -or (Invoke-Adb shell settings get global mobile_data).Trim() -ne '0'){throw 'Offline emulator required'}
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
    runId=$runId;phase=$Phase;serial=$Serial;avd='Outpost35';startedUtc=[DateTime]::UtcNow.ToString('o');gitHead=$head
    sourceStatus=@(& git -C $project status --short)
    appSha256=(Get-FileHash -LiteralPath $apks[0].File -Algorithm SHA256).Hash.ToLowerInvariant()
    testApkSha256=(Get-FileHash -LiteralPath $apks[1].File -Algorithm SHA256).Hash.ToLowerInvariant()
    mainSourcesSha256=Get-OutpostSourceFingerprint main;testSourcesSha256=Get-OutpostSourceFingerprint androidTest
    fixtureSha256=(Get-FileHash -LiteralPath $fixture -Algorithm SHA256).Hash.ToLowerInvariant()
    lockSha256=(Get-FileHash -LiteralPath $lock -Algorithm SHA256).Hash.ToLowerInvariant()
    deviceFingerprint=(Invoke-Adb shell getprop ro.build.fingerprint).Trim();offlineState=@(1,0,0)
    buildReceipt=(Get-Content -LiteralPath (Join-Path $project '.local/build-receipt.json') -Raw|ConvertFrom-Json)
    modelExecution=$true;scope='Pinned Spark research and Bonsai control inside Outpost35. No product profile selection, physical device or storage-policy change. Same-version research APK is identified by bytes, not release label.'
}
$identity|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $out 'run.json') -Encoding utf8
Copy-Item -LiteralPath $fixture -Destination (Join-Path $out 'missions-v1.json')
Copy-Item -LiteralPath $lock -Destination (Join-Path $out 'spark17-lock.json')
foreach($source in @('app/src/main/cpp/engine.cpp','app/src/main/java/dev/outpost/app/NativeEngine.java','app/src/main/java/dev/outpost/app/ChatPrompt.java','app/src/androidTest/java/dev/outpost/app/CandidateChecks.java','scripts/test-candidate.ps1')){
    $target=Join-Path $out "source/$source";New-Item -ItemType Directory -Force -Path (Split-Path $target -Parent)|Out-Null
    Copy-Item -LiteralPath (Join-Path $project $source) -Destination $target
}
$executionError=$null;$result=@()
try{$result=Invoke-Adb shell am instrument -w -e candidate_run $runId -e candidate_phase $Phase dev.outpost.app.test/dev.outpost.app.GenerationInstrumentation | ForEach-Object {Write-Host $_;$_}}
catch{$executionError=$_;$_|Out-String|Set-Content -LiteralPath (Join-Path $out 'execution-error.txt') -Encoding utf8}
finally{
    $result|Set-Content -LiteralPath (Join-Path $out 'instrumentation.log') -Encoding utf8
    $psi=[System.Diagnostics.ProcessStartInfo]::new($adb);$psi.UseShellExecute=$false;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
    $psi.Arguments=ConvertTo-OutpostArgumentString @('-s',$Serial,'exec-out','run-as','dev.outpost.app','cat',"files/evidence/candidates/$runId/candidate-checks.json")
    $process=[System.Diagnostics.Process]::Start($psi);$file=[System.IO.File]::Create((Join-Path $out 'candidate-checks.json'))
    try{$process.StandardOutput.BaseStream.CopyTo($file)}finally{$file.Dispose()}
    $process.WaitForExit();if($process.ExitCode -ne 0){Write-Warning $process.StandardError.ReadToEnd()}
    Write-Output "Evidence: $out"
}
if($executionError){throw $executionError}
$report=Get-Content -LiteralPath (Join-Path $out 'candidate-checks.json') -Raw|ConvertFrom-Json
if(-not $report.passed -or $report.runId -ne $runId -or $report.fixtureSha256 -ne $identity.fixtureSha256 -or -not($result -match "PASS candidate $Phase")){throw 'Candidate run failed or identity mismatch; inspect preserved evidence'}
