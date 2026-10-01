# Explicitly admitted runtime targets; device registration is local and never inferred from ADB ordering.
function Resolve-OutpostTestTarget([string]$Adb,[ValidateSet('Outpost35','Pixel10Pro')][string]$Target='Outpost35',[string]$Serial='') {
    $root=Split-Path $PSScriptRoot -Parent
    if($Target -eq 'Pixel10Pro') {
        $path=Join-Path $root '.local/pixel10-target.json'
        if(-not(Test-Path -LiteralPath $path)){throw 'Register the specifically authorized Pixel in .local/pixel10-target.json; see docs/pixel10-testing.md.'}
        $registered=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json
        if($registered.model -cne 'Pixel 10 Pro' -or $registered.authorization -cne 'owner-request-2026-10-01' -or $registered.serial -notmatch '^[A-Za-z0-9]{6,40}$'){throw 'Invalid Pixel registration'}
        if(-not $Serial){$Serial=$registered.serial}
        if($Serial -cne $registered.serial){throw 'Serial does not match the specifically authorized Pixel'}
    } else {
        if(-not $Serial){$Serial='emulator-5582'}
        if($Serial -ne 'emulator-5582'){throw 'Only the dedicated Outpost emulator is admitted'}
    }
    function Read-Adb { $value=& $Adb -s $Serial @args; if($LASTEXITCODE -ne 0){throw "ADB target unavailable: $Target"}; return ($value -join "`n").Trim() }
    if((Read-Adb get-state) -ne 'device'){throw 'ADB authorization/connection required'}
    $props=[ordered]@{}
    foreach($key in @('ro.kernel.qemu','ro.boot.qemu.avd_name','ro.product.manufacturer','ro.product.model','ro.product.device','ro.product.cpu.abi','ro.build.version.release','ro.build.version.sdk','ro.build.version.security_patch','ro.build.fingerprint','ro.soc.manufacturer','ro.soc.model','sys.boot_completed')){$props[$key]=Read-Adb shell getprop $key}
    if($props['sys.boot_completed'] -ne '1'){throw 'Target not booted'}
    if($Target -eq 'Outpost35') {
        if($props['ro.kernel.qemu'] -ne '1' -or $props['ro.boot.qemu.avd_name'] -ne 'Outpost35' -or $props['ro.product.cpu.abi'] -ne 'x86_64'){throw 'Incorrect dedicated AVD'}
    } elseif($props['ro.kernel.qemu'] -eq '1' -or $props['ro.product.manufacturer'] -cne 'Google' -or $props['ro.product.model'] -cne 'Pixel 10 Pro' -or $props['ro.product.cpu.abi'] -ne 'arm64-v8a') {throw 'Registered target is not a physical Google Pixel 10 Pro / ARM64'}
    $radios=[ordered]@{}
    foreach($key in @('airplane_mode_on','wifi_on','mobile_data')){$radios[$key]=Read-Adb shell settings get global $key}
    if($Target -eq 'Outpost35' -and ($radios.airplane_mode_on -ne '1' -or $radios.wifi_on -ne '0' -or $radios.mobile_data -ne '0')){throw 'Offline emulator required'}
    return [pscustomobject]@{Serial=$Serial;Target=$Target;Properties=$props;RadioSettings=$radios}
}

function Get-OutpostTargetConditions([string]$Adb,[string]$Serial) {
    $battery=& $Adb -s $Serial shell dumpsys battery
    if($LASTEXITCODE -ne 0){throw 'Cannot read battery conditions'}
    $memory=& $Adb -s $Serial shell cat /proc/meminfo
    if($LASTEXITCODE -ne 0){throw 'Cannot read memory conditions'}
    $space=& $Adb -s $Serial shell df -k /data
    if($LASTEXITCODE -ne 0){throw 'Cannot read storage conditions'}
    return [ordered]@{capturedUtc=[DateTime]::UtcNow.ToString('o');battery=@($battery|Where-Object {$_ -match '^\s*(AC powered|USB powered|Wireless powered|Dock powered|status|health|present|level|scale|voltage|temperature|technology):'});memory=@($memory|Where-Object {$_ -match '^(MemTotal|MemFree|MemAvailable|SwapTotal|SwapFree):'});dataSpace=@($space)}
}
