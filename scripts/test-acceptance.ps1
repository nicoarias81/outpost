param([ValidateSet('Pixel10Pro','Outpost35')][string]$Target='Pixel10Pro',[string]$OwnerRun='',[ValidateSet('all','prepare','chat','recovery')][string]$Phase='all')
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'environment.ps1')
. (Join-Path $PSScriptRoot 'test-target.ps1')
$adb=Join-Path (Resolve-OutpostSdk '') 'platform-tools/adb.exe'
$device=Resolve-OutpostTestTarget -Adb $adb -Target $Target;$serial=$device.Serial
$package='dev.outpost.app.releaseqa';$testPackage="$package.test"
function Adb { $result=& $adb -s $serial @args;if($LASTEXITCODE -ne 0){throw 'Acceptance ADB failed'};return $result }
function Hash-App([string]$Name){$path=((Adb shell pm path $Name)-join '').Trim();if(-not $path.StartsWith('package:')){throw 'Expected original app missing'};return ((Adb shell sha256sum $path.Substring(8))-split '\s+')[0]}
function Digest([string]$Path){$cmd="if test -d $Path; then find $Path -type f -exec sha256sum {} + | sort | sha256sum; else echo absent; fi";return ((Adb shell run-as dev.outpost.app sh -c "'$cmd'")-join '').Trim()}
$receipt=Get-Content -LiteralPath "$project/.local/release-build-receipt.json" -Raw|ConvertFrom-Json
if($receipt.mainSourcesSha256 -ne (Get-OutpostSourceFingerprint main)){throw 'Stale release receipt'}
foreach($entry in $receipt.artifacts){if((Get-FileHash -LiteralPath (Join-Path $project $entry.path) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256){throw 'Release artifact mismatch'}}
$id='release-acceptance-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$out=Join-Path $project "evidence/runs/$id";New-Item -ItemType Directory -Path $out|Out-Null
$record=[ordered]@{runId=$id;target=$Target;device=$device.Properties;receipt=$receipt;originalApks=@{};privateDigests=@{};phases=@();passed=$false;originalStatePreserved=$false;limits='Non-debuggable QA package and debug QA certificate. Exact production-signed acceptance is separate.'}
foreach($name in @('dev.outpost.app','dev.outpost.app.test')){$record.originalApks[$name]=Hash-App $name}
foreach($path in @('databases','shared_prefs','files/documents')){$record.privateDigests[$path]=Digest $path}
try{
    Adb install -r "$project/app/build/outputs/apk/releaseQa/app-releaseQa.apk"
    Adb install -r "$project/app/build/outputs/apk/androidTest/releaseQa/app-releaseQa-androidTest.apk"
    $steps=switch($Phase){'all'{@('prepare','chat','seed','recover')}'recovery'{@('seed','recover')}default{@($Phase)}}
    foreach($step in $steps){
        $run='accept-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
        if($step -eq 'prepare'){$OwnerRun=$run}
        if($OwnerRun -notmatch '^accept-[0-9TZ]+-[a-f0-9]{8}$'){throw 'Recorded synthetic-store owner run required'}
        if($step -eq 'chat'){
            $name='Ternary-Bonsai-4B-Q2_0_g64.gguf';$staged="/sdcard/Android/data/$package/files/$name"
            $exists=(Adb shell "if test -f $staged; then echo present; fi")-join ''
            if($exists -ne 'present'){Adb shell "run-as dev.outpost.app cat files/models/$name | toybox tee $staged > /dev/null"}
            if(((Adb shell sha256sum $staged)-split '\s+')[0] -ne '9d968b04a3c9a794897bcc744c8072fb6a061c0e42efd03c989401ddf8baef0c'){throw 'Staged Bonsai4 does not match pin'}
        }
        if($step -eq 'recover'){
            Adb shell am force-stop $package
            Adb install -r "$project/app/build/outputs/apk/releaseQa/app-releaseQa.apk"
        }
        $log=Adb shell am instrument -w -e phase "accept-$step" -e accept_run $run -e owner_run $OwnerRun "$testPackage/dev.outpost.app.ReleaseInstrumentation"
        $log|Set-Content -LiteralPath "$out/$step.log" -Encoding utf8
        $lines=@($log|Where-Object {$_ -match '^RELEASE_RESULT '});if($lines.Count -ne 1){throw 'Missing acceptance report'}
        $result=$lines[0].Substring(15)|ConvertFrom-Json
        $result|ConvertTo-Json -Depth 9|Set-Content -LiteralPath "$out/$step.json" -Encoding utf8
        Adb pull "/sdcard/Android/data/$package/files/$run" "$out/$step-device"
        $record.phases+=@{step=$step;run=$run;ownerRun=$OwnerRun;passed=$result.passed;checks=@($result.checks).Count}
        if(-not $result.passed){throw "$step failed: $($result.error)"}
        Write-Output "PASS $Target $step ($(@($result.checks).Count) controls)"
    }
    $record.passed=$true
}finally{
    $same=$true
    foreach($name in $record.originalApks.Keys){if((Hash-App $name) -ne $record.originalApks[$name]){$same=$false}}
    foreach($path in $record.privateDigests.Keys){if((Digest $path) -ne $record.privateDigests[$path]){$same=$false}}
    $after=Resolve-OutpostTestTarget -Adb $adb -Target $Target
    if(($device.RadioSettings|ConvertTo-Json -Compress) -ne ($after.RadioSettings|ConvertTo-Json -Compress)){$same=$false}
    $record.originalStatePreserved=$same;$record|ConvertTo-Json -Depth 12|Set-Content -LiteralPath "$out/run.json" -Encoding utf8
    Write-Output "Acceptance evidence: $out; owner: $OwnerRun"
    if(-not $same){throw 'Original app/data or radio state changed; inspect evidence'}
}
