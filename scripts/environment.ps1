# Machine paths are configured explicitly or in ignored .local/developer-settings.json.
function Get-OutpostSettings {
    $settingsPath = Join-Path (Split-Path $PSScriptRoot -Parent) '.local/developer-settings.json'
    if (Test-Path -LiteralPath $settingsPath) { return Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json }
    return [pscustomobject]@{}
}
function Resolve-OutpostSdk([string]$Sdk) {
    if (-not $Sdk) { $Sdk = (Get-OutpostSettings).sdk }
    if (-not $Sdk -or -not (Test-Path -LiteralPath (Join-Path $Sdk 'platform-tools/adb.exe'))) {
        throw 'Set ANDROID_HOME, pass -Sdk, or set sdk in .local/developer-settings.json to an Android SDK with platform-tools.'
    }
    return (Resolve-Path -LiteralPath $Sdk).Path
}
# Windows PowerShell 5.1 has no ProcessStartInfo.ArgumentList, so callers build the argument string.
# Every argument is quoted, so a value containing a space cannot split into two arguments.
function ConvertTo-OutpostArgumentString([string[]]$Arguments) {
    $quoted = foreach ($argument in $Arguments) { '"' + ([string]$argument).Replace('"', '\"') + '"' }
    return ($quoted -join ' ')
}
