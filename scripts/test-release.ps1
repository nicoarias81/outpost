param([switch]$Generate)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'environment.ps1')
. (Join-Path $PSScriptRoot 'test-target.ps1')
$adb=Join-Path (Resolve-OutpostSdk '') 'platform-tools/adb.exe'
$device=Resolve-OutpostTestTarget -Adb $adb -Target Outpost35
$serial=$device.Serial
function Invoke-ReleaseAdb { $value=& $adb -s $serial @args; if($LASTEXITCODE -ne 0){throw 'Release QA ADB operation failed'}; return $value }
$receipt=Get-Content -LiteralPath "$project/.local/release-build-receipt.json" -Raw|ConvertFrom-Json
if($receipt.mainSourcesSha256 -ne (Get-OutpostSourceFingerprint main)){throw 'Rebuild release: sources do not match receipt'}
foreach($artifact in $receipt.artifacts){if((Get-FileHash -LiteralPath (Join-Path $project $artifact.path) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $artifact.sha256){throw 'Release APK bytes changed'}}
$id='release-qa-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$out=Join-Path $project "evidence/runs/$id";New-Item -ItemType Directory -Path $out|Out-Null
$original=@{}
foreach($package in @('dev.outpost.app','dev.outpost.app.test')){
    $path=((Invoke-ReleaseAdb shell pm path $package)-join '').Trim().Substring(8)
    $original[$package]=((Invoke-ReleaseAdb shell sha256sum $path)-split '\s+')[0]
}
$report=[ordered]@{runId=$id;target='Outpost35';device=$device.Properties;receipt=$receipt;passed=$false;originalApks=$original;originalApksUnchanged=$false;phases=@();limits='Non-debuggable QA package with production native bytes/assets; debug QA certificate and separate application ID. Exact production signature acceptance remains pending.'}
try {
    Invoke-ReleaseAdb install -r "$project/app/build/outputs/apk/releaseQa/app-releaseQa.apk"
    Invoke-ReleaseAdb install -r "$project/app/build/outputs/apk/androidTest/releaseQa/app-releaseQa-androidTest.apk"
    $phases=@('smoke');if($Generate){$phases+='generate'}
    foreach($phase in $phases){
        if($phase -eq 'generate'){
            $name='Ternary-Bonsai-1.7B-Q2_0_g64.gguf'
            $staged="/sdcard/Android/data/dev.outpost.app.releaseqa/files/$name"
            $present=Invoke-ReleaseAdb shell "if test -f $staged; then echo present; fi"
            if($present -ne 'present'){Invoke-ReleaseAdb shell "run-as dev.outpost.app cat files/models/$name | toybox tee $staged > /dev/null"}
            $hash=((Invoke-ReleaseAdb shell sha256sum $staged)-split '\s+')[0]
            if($hash -ne '6d0ecb3d9055969b5cde332b6fdb60e67ed3599e9f73e56b977731e1467e5c91'){throw 'QA staged model hash mismatch'}
        }
        $log=Invoke-ReleaseAdb shell am instrument -w -e phase $phase dev.outpost.app.releaseqa.test/dev.outpost.app.ReleaseInstrumentation
        $log|Set-Content -LiteralPath "$out/$phase.log" -Encoding utf8
        $line=@($log|Where-Object {$_ -match '^RELEASE_RESULT '})
        if($line.Count -ne 1){throw "No structured result for $phase"}
        $result=$line[0].Substring(15)|ConvertFrom-Json
        $result|ConvertTo-Json -Depth 8|Set-Content -LiteralPath "$out/$phase.json" -Encoding utf8
        if(-not $result.passed){throw "Release $phase failed: $($result.error)"}
        $report.phases+=@{phase=$phase;checks=@($result.checks).Count;passed=$true}
        Write-Output "PASS release $phase ($(@($result.checks).Count) controls)"
    }
    $report.passed=$true
} finally {
    $unchanged=$true
    foreach($package in $original.Keys){$path=((Invoke-ReleaseAdb shell pm path $package)-join '').Trim().Substring(8);if(((Invoke-ReleaseAdb shell sha256sum $path)-split '\s+')[0] -ne $original[$package]){$unchanged=$false}}
    $report.originalApksUnchanged=$unchanged
    $after=Resolve-OutpostTestTarget -Adb $adb -Target Outpost35
    $report.radioSettingsUnchanged=($after.RadioSettings|ConvertTo-Json -Compress) -eq ($device.RadioSettings|ConvertTo-Json -Compress)
    $report|ConvertTo-Json -Depth 10|Set-Content -LiteralPath "$out/run.json" -Encoding utf8
    if(-not $unchanged -or -not $report.radioSettingsUnchanged){throw 'Original emulator state changed'}
    Write-Output "Release QA evidence: $out"
}
