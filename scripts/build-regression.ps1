$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
. "$project/scripts/environment.ps1"
$cfg=Get-OutpostSettings
$env:JAVA_HOME=$cfg.javaHome
$env:GRADLE_USER_HOME=$cfg.gradleHome
$env:PYTHONUTF8='1'
$main=Get-OutpostSourceFingerprint main
$tests=Get-OutpostSourceFingerprint androidTest
& $cfg.gradleExecutable -p $project --no-daemon --offline -PoutpostRegressionTest :app:assembleRegressionQa :app:assembleRegressionQaAndroidTest :app:lintRegressionQa
if($LASTEXITCODE -ne 0){throw 'Regression build failed'}
if($main -ne (Get-OutpostSourceFingerprint main) -or $tests -ne (Get-OutpostSourceFingerprint androidTest)){throw 'Inputs changed'}
$id='regression-build-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
$out=Join-Path $project ".local/$id"
New-Item -ItemType Directory -Path $out|Out-Null
$artifacts=@()
foreach($relative in @('app/build/outputs/apk/regressionQa/app-regressionQa.apk','app/build/outputs/apk/androidTest/regressionQa/app-regressionQa-androidTest.apk')){
    $file=Join-Path $project $relative
    Copy-Item -LiteralPath $file -Destination $out
    $artifacts+=@{path=$relative;sha256=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant();bytes=(Get-Item -LiteralPath $file).Length}
}
$receipt=@{runId=$id;mainSourcesSha256=$main;testSourcesSha256=$tests;artifacts=$artifacts;scope='Dedicated debuggable regression QA; synthetic data only; research native APIs for ARM tests.'}
$receipt|ConvertTo-Json -Depth 6|Set-Content -LiteralPath "$project/.local/regression-build-receipt.json" -Encoding utf8
$receipt|ConvertTo-Json -Depth 6|Set-Content -LiteralPath "$out/receipt.json" -Encoding utf8
Write-Output "Regression QA archived: $out"
