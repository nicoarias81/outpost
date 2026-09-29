param([string]$Sdk = $env:ANDROID_HOME, [int]$Port = 5582, [switch]$Visible)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'environment.ps1')
$Sdk = Resolve-OutpostSdk $Sdk
$image = Join-Path $Sdk 'system-images\android-35\default\x86_64'
if (-not (Test-Path "$image\system.img")) { throw 'Install system-images;android-35;default;x86_64 in your SDK first.' }
$local = Join-Path $project '.local'
$avds = Join-Path $local 'avd'
$avd = Join-Path $avds 'Outpost35.avd'
New-Item -ItemType Directory -Force -Path $avd | Out-Null
@('avd.ini.encoding=UTF-8', "path=$avd", 'target=android-35') | Set-Content -LiteralPath "$avds\Outpost35.ini"
if (-not (Test-Path "$avd\config.ini")) {
    @('avd.ini.encoding=UTF-8','hw.cpu.arch=x86_64','hw.cpu.ncore=4','hw.ramSize=4096','hw.lcd.width=1080','hw.lcd.height=2400','hw.lcd.density=420','hw.keyboard=yes','hw.gpu.enabled=yes','hw.gpu.mode=swiftshader_indirect','disk.dataPartition.size=10G',"image.sysdir.1=$image",'tag.id=default','abi.type=x86_64','hw.mainKeys=no','hw.audioInput=no','hw.camera.back=none','hw.camera.front=none') | Set-Content -LiteralPath "$avd\config.ini"
}
$env:ANDROID_AVD_HOME = $avds
$env:ANDROID_HOME = $Sdk
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
$devices = & $adb devices
if ($devices -match "emulator-$Port\s+device") { Write-Output "Emulator already running: emulator-$Port"; exit 0 }
$arguments = @('-avd','Outpost35','-port',"$Port",'-no-audio','-no-snapshot','-gpu','swiftshader_indirect')
if (-not $Visible) { $arguments += '-no-window' }
$processOptions = @{ FilePath="$Sdk\emulator\emulator.exe"; ArgumentList=$arguments; RedirectStandardOutput="$local\emulator.stdout.log"; RedirectStandardError="$local\emulator.stderr.log"; PassThru=$true }
if (-not $Visible) { $processOptions.WindowStyle = 'Hidden' }
Start-Process @processOptions | Select-Object Id, ProcessName
