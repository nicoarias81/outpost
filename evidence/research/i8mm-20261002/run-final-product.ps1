param([string[]]$Phases=@('i8mm-numeric','i8mm-lifecycle','stack-confirm'),[int]$PrefillChunk=32,[int]$DecodeChunk=0)
$ErrorActionPreference='Stop'
$OutpostProject='E:\projects\outpost'
Set-Location -LiteralPath $OutpostProject
. ./scripts/environment.ps1
. ./scripts/test-target.ps1
$OutpostAdb=Join-Path (Resolve-OutpostSdk '') 'platform-tools/adb.exe'
$OutpostPhone=Resolve-OutpostTestTarget -Adb $OutpostAdb -Target Pixel10Pro
$OutpostBaseline=Join-Path $OutpostProject '.local/spec-study/baseline'
$OutpostBaselineReceipt=Get-Content (Join-Path $OutpostBaseline 'build-receipt.json') -Raw|ConvertFrom-Json
if((Get-Content .local/pixel-ui-active.json -Raw|ConvertFrom-Json).status -ne 'restored'){throw 'Restore pending private state before installing'}
$OutpostPackages=@(@{name='dev.outpost.app';file='app-debug.apk';hash=$OutpostBaselineReceipt.appSha256;trial='app/build/outputs/apk/debug/app-debug.apk'},@{name='dev.outpost.app.test';file='app-debug-androidTest.apk';hash=$OutpostBaselineReceipt.testApkSha256;trial='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'})
function Read-InstalledHash([string]$Package) {
    $p=(& $OutpostAdb -s $OutpostPhone.Serial shell pm path $Package).Trim()
    if($LASTEXITCODE -ne 0 -or -not $p.StartsWith('package:')){throw 'Expected installed APK'}
    $hash=((& $OutpostAdb -s $OutpostPhone.Serial shell sha256sum $p.Substring(8))-split '\s+')[0]
    if($LASTEXITCODE -ne 0 -or $hash -notmatch '^[a-f0-9]{64}$'){throw 'Cannot identify installed APK'}
    return $hash
}
function Install-TrialApk([string]$File,[switch]$Restore) {
    if($Restore){& $OutpostAdb -s $OutpostPhone.Serial install -r -d $File}else{& $OutpostAdb -s $OutpostPhone.Serial install -r $File}
    if($LASTEXITCODE -ne 0){throw 'Preserve data; APK installation failed'}
}
foreach($p in $OutpostPackages){if((Read-InstalledHash $p.name) -ne $p.hash){throw 'Installed APK changed since the saved baseline; inspect before replacing it'}}
$OutpostRecord=Join-Path $OutpostProject ('evidence/research/i8mm-20261002/trial-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'.json')
New-Item -ItemType Directory -Force -Path (Split-Path $OutpostRecord -Parent)|Out-Null
$OutpostRecordState=[ordered]@{startedUtc=[DateTime]::UtcNow.ToString('o');phases=$Phases;prefillChunk=$PrefillChunk;decodeChunk=$DecodeChunk;buildReceipt=(Get-Content .local/build-receipt.json -Raw|ConvertFrom-Json);restored=$false}
$OutpostRecordState|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $OutpostRecord -Encoding utf8
try {
    foreach($p in $OutpostPackages){Install-TrialApk $p.trial}
    foreach($phase in $Phases){
        if($phase -notin @('i8mm-numeric','i8mm-lifecycle','stack-confirm')){throw 'Unexpected experiment phase'}
        ./scripts/test-pixel-chat-isolated.ps1 -ArmPhase $phase -Threads 4 -PromptThreads 6 -Width 8 -DecodeRows 4 -PersistentThreads -PrefillChunk $PrefillChunk -DecodeChunk $DecodeChunk
    }
} finally {
    foreach($p in $OutpostPackages){Install-TrialApk (Join-Path $OutpostBaseline $p.file) -Restore;if((Read-InstalledHash $p.name) -ne $p.hash){throw 'Restored APK bytes do not match the saved baseline'}}
    $OutpostRecordState.restored=$true
    $OutpostRecordState.restoredUtc=[DateTime]::UtcNow.ToString('o')
    $OutpostRecordState.isolation=Get-Content .local/pixel-ui-active.json -Raw|ConvertFrom-Json
    $OutpostRecordState|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $OutpostRecord -Encoding utf8
    Write-Output 'Validated baseline APKs restored; inspect the recorded isolation status.'
}
