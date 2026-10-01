param([string]$Sdk=$env:ANDROID_HOME,[switch]$Generate,[switch]$CandidateAdmission,[switch]$RestoreOnly,[ValidateSet('','numeric','model','tune','confirm','controller','lifecycle','trace','profile','row-numeric','row-tune','row-confirm','row-confirm-reverse','row-lifecycle','spec-trace','spec-curve','spec-edges','attention-curve','attention-trace','attention-six','attention-trace-six','decode-six-pilot','decode-six-confirm','decode-six-lifecycle')][string]$ArmPhase='',[ValidateRange(1,8)][int]$Threads=4,[ValidateSet(1,2,4,8)][int]$Width=4,[ValidateSet(1,2,4)][int]$DecodeRows=1,[switch]$PersistentThreads,[ValidateSet('none','performance')][string]$Affinity='none',[ValidateRange(0,8)][int]$PromptThreads=0,[ValidateSet(0,16,32,64,128,256)][int]$PrefillChunk=32,[ValidateSet(0,16,32,64,128,256)][int]$DecodeChunk=64)
$ErrorActionPreference='Stop'
if($CandidateAdmission -and ($Generate -or $ArmPhase)){throw 'Choose one isolated test type'}
$project=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'environment.ps1')
. (Join-Path $PSScriptRoot 'test-target.ps1')
$adb=Join-Path (Resolve-OutpostSdk $Sdk) 'platform-tools/adb.exe'
$device=Resolve-OutpostTestTarget -Adb $adb -Target Pixel10Pro
$serial=$device.Serial
$activePath=Join-Path $project '.local/pixel-ui-active.json'
function Invoke-PixelAdb { $result=& $adb -s $serial @args; if($LASTEXITCODE -ne 0){throw 'Pixel ADB operation failed'}; return ($result -join "`n").Trim() }
function Private-Shell([string]$Command) { Invoke-PixelAdb shell run-as dev.outpost.app sh -c "'$Command'" }
function Private-Exists([string]$Path) { (Private-Shell "if test -e $Path; then echo present; fi") -eq 'present' }
function Directory-Digest([string]$Path) {
    if(-not(Private-Exists $Path)){return $null}
    if((Private-Shell "if test -L $Path; then echo symlink; fi") -eq 'symlink'){throw 'Refusing a symlinked data directory'}
    if(Private-Shell "find $Path -type l -print"){throw 'Refusing symlinks inside a data directory'}
    $line=Private-Shell "find $Path -type f -exec sha256sum {} + | sort | sha256sum"
    $hash=($line -split '\s+')[0]
    if($hash -notmatch '^[0-9a-f]{64}$'){throw 'Invalid private manifest digest'}
    return $hash
}
function Save-State {
    $state|ConvertTo-Json -Depth 8|Set-Content -LiteralPath $activePath -Encoding utf8
    $state|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $out 'isolation.json') -Encoding utf8
}
if($RestoreOnly) {
    if(-not(Test-Path -LiteralPath $activePath)){throw 'No isolation journal to restore'}
    $state=Get-Content -LiteralPath $activePath -Raw|ConvertFrom-Json
    if(-not $state.PSObject.Properties['restoredUtc']){$state|Add-Member -NotePropertyName restoredUtc -NotePropertyValue $null}
    if($state.runId -notmatch '^pixel-isolation-[0-9TZ]+-[a-f0-9]{8}$'){throw 'Invalid recovery identity'}
    if($state.status -eq 'restored'){Write-Output 'Original state was already restored.';return}
    $out=Join-Path $project "evidence/runs/$($state.runId)"
} else {
    if(Test-Path -LiteralPath $activePath){$previous=Get-Content -LiteralPath $activePath -Raw|ConvertFrom-Json;if($previous.status -ne 'restored'){throw 'Restore the pending isolation first with -RestoreOnly'}}
    & (Join-Path $project 'eval/check-build.ps1')
    $run='pixel-isolation-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')+'-'+[guid]::NewGuid().ToString('N').Substring(0,8)
    $out=Join-Path $project "evidence/runs/$run";New-Item -ItemType Directory -Path $out|Out-Null
    $state=[ordered]@{runId=$run;target='Pixel10Pro';startedUtc=[DateTime]::UtcNow.ToString('o');restoredUtc=$null;status='preparing';backup="files/$run";entries=@();scope='Opaque app-private directory renames. No personal contents leave the phone; original files are hash-verified after restoration. Test directories are retained separately, never merged.'}
    Save-State
    Copy-Item -LiteralPath $PSCommandPath -Destination (Join-Path $out 'test-pixel-chat-isolated.ps1')
}
$expectedPaths=@('databases','shared_prefs','files/documents')
$backup="files/$($state.runId)"
if($state.backup -ne $backup){throw 'Unexpected recovery directory'}
foreach($entry in $state.entries){if($entry.path -notin $expectedPaths -or $entry.key -cne ($entry.path -split '/')[-1]){throw 'Unexpected recovery entry'}}
$failure=$null
try {
    Invoke-PixelAdb shell am force-stop dev.outpost.app|Out-Null
    $appRoot=Invoke-PixelAdb shell run-as dev.outpost.app pwd
    if($appRoot -notin @('/data/user/0/dev.outpost.app','/data/data/dev.outpost.app')){throw 'Unexpected app-private root'}
    if(-not $RestoreOnly) {
        if(Private-Exists $backup){throw 'Isolation directory already exists'}
        Invoke-PixelAdb shell run-as dev.outpost.app mkdir -p $backup|Out-Null
        foreach($path in $expectedPaths) {
            $key=($path -split '/')[-1]
            $hash=Directory-Digest $path
            $entry=[ordered]@{path=$path;key=$key;existed=($null -ne $hash);beforeDigest=$hash;afterDigest=$null}
            $state.entries+=,$entry;Save-State
            if($entry.existed){Invoke-PixelAdb shell run-as dev.outpost.app mv $path "$backup/original-$key"|Out-Null}
        }
        $state.status='isolated';Save-State
        Invoke-PixelAdb shell run-as dev.outpost.app touch "$backup/active"|Out-Null
        if($ArmPhase){& (Join-Path $project 'scripts/test-arm.ps1') -Sdk $Sdk -Phase $ArmPhase -SkipInstall -Threads $Threads -Width $Width -DecodeRows $DecodeRows -PersistentThreads:$PersistentThreads -Affinity $Affinity -PromptThreads $PromptThreads -PrefillChunk $PrefillChunk -DecodeChunk $DecodeChunk}
        elseif($CandidateAdmission){& (Join-Path $project 'scripts/test-candidate.ps1') -Sdk $Sdk -Target Pixel10Pro -Phase admission -SkipInstall}
        else {& (Join-Path $project 'scripts/test-chat.ps1') -Sdk $Sdk -Target Pixel10Pro -SkipInstall -Generate:$Generate -Isolation $state.runId}
    }
} catch { $failure=$_ }
finally {
    Invoke-PixelAdb shell am force-stop dev.outpost.app|Out-Null
    foreach($entry in $state.entries) {
        $original="$backup/original-$($entry.key)";$temporary="$backup/test-$($entry.key)"
        if(Private-Exists $original) {
            if(Private-Exists $temporary){throw "Recovery destination exists; preserve all directories and inspect $backup"}
            if(Private-Exists $entry.path){Invoke-PixelAdb shell run-as dev.outpost.app mv $entry.path $temporary|Out-Null}
            Invoke-PixelAdb shell run-as dev.outpost.app mv $original $entry.path|Out-Null
        } elseif(-not $entry.existed -and (Private-Exists $entry.path)) {
            if(Private-Exists $temporary){throw 'Test-data recovery destination exists; inspect before moving'}
            Invoke-PixelAdb shell run-as dev.outpost.app mv $entry.path $temporary|Out-Null
        }
        $entry.afterDigest=Directory-Digest $entry.path
        if($entry.afterDigest -ne $entry.beforeDigest){throw 'Restored data digest mismatch; preserve the isolation journal'}
        Save-State
    }
    if(Private-Exists "$backup/active"){Invoke-PixelAdb shell run-as dev.outpost.app rm "$backup/active"|Out-Null}
    $state.status='restored';$state.restoredUtc=[DateTime]::UtcNow.ToString('o');Save-State
    Write-Output "Original app data restored and verified. Isolation evidence: $out"
}
if($failure){throw $failure}
