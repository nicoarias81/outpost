param([string]$Sdk=$env:ANDROID_HOME,[string]$Serial='emulator-5582',[string[]]$Profiles=@('qwen15','bonsai17','bonsai4'),[string]$UiProfile='',[switch]$SkipInstall,[switch]$UiOnly,[switch]$KeepSelected,[switch]$SelectOnly)
$ErrorActionPreference='Stop'
if($Serial -notmatch '^emulator-\d+$') { throw 'Only emulator targets are permitted.' }
$project=Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'environment.ps1')
$Sdk = Resolve-OutpostSdk $Sdk
$adb=Join-Path $Sdk 'platform-tools\adb.exe'
function Invoke-Adb { & $adb -s $Serial @args; if($LASTEXITCODE -ne 0) { throw "adb failed: $args" } }
if((Invoke-Adb shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Target is not an emulator.' }
Invoke-Adb shell cmd connectivity airplane-mode enable
Invoke-Adb shell svc wifi disable
Invoke-Adb shell svc data disable
Invoke-Adb install -r "$project\app\build\outputs\apk\debug\app-debug.apk"
Invoke-Adb install -r "$project\app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk"
$models=(Get-Content -LiteralPath "$project\bonsai-lock.json" -Raw | ConvertFrom-Json).models
if($UiOnly -or $SelectOnly) { $Profiles=@(); if(-not $UiProfile) { throw 'UiOnly/SelectOnly requires UiProfile' } }
function Run-Check([string]$Operation,[string]$Success) {
    $result=Invoke-Adb shell am instrument -w -e ternary $Operation dev.outpost.app.test/dev.outpost.app.GenerationInstrumentation | ForEach-Object { Write-Host $_; $_ }
    if(-not($result -match $Success)) { throw "Failed: $Operation" }
}
function Copy-Evidence([string]$Name) {
    $psi=[System.Diagnostics.ProcessStartInfo]::new($adb); $psi.UseShellExecute=$false; $psi.RedirectStandardOutput=$true
    $psi.Arguments = "-s $Serial exec-out run-as dev.outpost.app cat `"files/evidence/$Name`""
    $p=[System.Diagnostics.Process]::Start($psi); $file=[System.IO.File]::Create((Join-Path $project "evidence\$Name"))
    try { $p.StandardOutput.BaseStream.CopyTo($file) } finally { $file.Dispose() }
    $p.WaitForExit(); if($p.ExitCode -ne 0) { throw "Cannot copy $Name" }
}
foreach($profile in $Profiles) {
    if($profile -notin @('qwen15','bonsai17','bonsai4')) { throw 'Unknown model profile' }
    if($profile -ne 'qwen15' -and -not $SkipInstall) {
        $model=$models | Where-Object { $_.id -eq $profile }
        $file=Join-Path $project ".local\models\$($model.file)"
        if((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLower() -ne $model.sha256) { throw 'Model hash mismatch' }
        Invoke-Adb shell mkdir -p /sdcard/Android/data/dev.outpost.app/files
        Invoke-Adb push $file "/sdcard/Android/data/dev.outpost.app/files/test-$profile.gguf"
        Run-Check "install:$profile" 'INSTALLED'
    }
    Run-Check "compare:$profile" 'PASS comparison'
    Copy-Evidence "bonsai-$profile.json"
}
if($SelectOnly) {
    Run-Check "select:$UiProfile" 'PASS selected'
    Copy-Evidence 'models.png'
} elseif($UiProfile) {
    if($UiProfile -notin @('qwen15','bonsai17','bonsai4')) { throw 'Unknown UI profile' }
    $operation=if($KeepSelected) { "ui-use:$UiProfile" } else { "ui:$UiProfile" }
    Run-Check $operation 'PASS UI'
    Copy-Evidence "bonsai-$UiProfile.png"
    Copy-Evidence "bonsai-ui-$UiProfile.json"
    Copy-Evidence 'models.png'
    if($KeepSelected -and $UiProfile -eq 'bonsai4') {
        Copy-Evidence 'bonsai-warm-bonsai4.json'
        Copy-Evidence 'bonsai-warm-bonsai4.png'
        Copy-Evidence 'bonsai-trim-bonsai4.json'
    }
}
Invoke-Adb shell am start -n dev.outpost.app/.MainActivity
