param([string]$Sdk=$env:ANDROID_HOME,[switch]$Functional)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'environment.ps1')
. (Join-Path $PSScriptRoot 'test-target.ps1')
$adb=Join-Path (Resolve-OutpostSdk $Sdk) 'platform-tools/adb.exe'
$device=Resolve-OutpostTestTarget -Adb $adb -Target Outpost35
$serial=$device.Serial
function Invoke-RegressionAdb { & $adb -s $serial @args; if($LASTEXITCODE -ne 0){throw 'Regression ADB operation failed'} }
function Pull-Private([string]$Remote,[string]$Local) {
    $psi=[Diagnostics.ProcessStartInfo]::new($adb);$psi.UseShellExecute=$false;$psi.RedirectStandardOutput=$true;$psi.RedirectStandardError=$true
    $psi.Arguments=ConvertTo-OutpostArgumentString @('-s',$serial,'exec-out','run-as','dev.outpost.app','cat',$Remote)
    $process=[Diagnostics.Process]::Start($psi);$file=[IO.File]::Create($Local)
    try{$process.StandardOutput.BaseStream.CopyTo($file)}finally{$file.Dispose()};$process.WaitForExit()
    if($process.ExitCode -ne 0){throw $process.StandardError.ReadToEnd()}
}
& (Join-Path $project 'eval/check-build.ps1')
$id='x86-regression-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$out=Join-Path $project "evidence/runs/$id";$backup=Join-Path $project ".local/$id";New-Item -ItemType Directory -Path $out,$backup|Out-Null
$packages=@(@{name='dev.outpost.app';file='app/build/outputs/apk/debug/app-debug.apk'},@{name='dev.outpost.app.test';file='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'})
foreach($pkg in $packages){
    $path=(Invoke-RegressionAdb shell pm path $pkg.name).Trim();if(-not $path.StartsWith('package:')){throw 'Expected existing emulator package'}
    $pkg.oldHash=((Invoke-RegressionAdb shell sha256sum $path.Substring(8)) -split '\s+')[0]
    $pkg.backup=Join-Path $backup "$($pkg.name).apk";Invoke-RegressionAdb pull $path.Substring(8) $pkg.backup
    if((Get-FileHash -LiteralPath $pkg.backup -Algorithm SHA256).Hash.ToLowerInvariant() -ne $pkg.oldHash){throw 'APK backup mismatch'}
}
$record=[ordered]@{runId=$id;target='Outpost35';device=$device.Properties;buildReceipt=(Get-Content -LiteralPath "$project/.local/build-receipt.json" -Raw|ConvertFrom-Json);phases=@();passed=$false;originalApksRestored=$false;originalApks=@($packages|ForEach-Object {[ordered]@{package=$_.name;sha256=$_.oldHash}})}
try {
    foreach($pkg in $packages){Invoke-RegressionAdb install -r (Join-Path $project $pkg.file)}
    $phases=@('numeric','dispatch','batch','decoder4');if($Functional){$phases+='functional'}
    foreach($phase in $phases) {
        $name=if($phase -eq 'functional'){'checks.json'}else{"kernel-$phase.json"}
        $present=Invoke-RegressionAdb shell run-as dev.outpost.app sh -c "'if test -e files/evidence/$name; then echo present; fi'"
        if($present -eq 'present'){Pull-Private "files/evidence/$name" (Join-Path $out "prior-$name")}
        $log=if($phase -eq 'functional'){Invoke-RegressionAdb shell am instrument -w dev.outpost.app.test/dev.outpost.app.GenerationInstrumentation | ForEach-Object {Write-Host $_;$_}}else{Invoke-RegressionAdb shell am instrument -w -e kernel $phase dev.outpost.app.test/dev.outpost.app.GenerationInstrumentation | ForEach-Object {Write-Host $_;$_}}
        $log|Set-Content -LiteralPath (Join-Path $out "$phase.log") -Encoding utf8
        Pull-Private "files/evidence/$name" (Join-Path $out $name)
        if(-not($log -match 'PASS')){throw "Failed x86 $phase; prior and new evidence retained"}
        $record.phases+=@{phase=$phase;passed=$true}
    }
    $record.passed=$true
} finally {
    foreach($pkg in $packages){Invoke-RegressionAdb install -r -d $pkg.backup;$path=(Invoke-RegressionAdb shell pm path $pkg.name).Trim().Substring(8);if(((Invoke-RegressionAdb shell sha256sum $path) -split '\s+')[0] -ne $pkg.oldHash){throw 'Original emulator APK restoration failed'}}
    $record.originalApksRestored=$true;$record|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $out 'run.json') -Encoding utf8
    Write-Output "Evidence: $out; original emulator APKs restored."
}
