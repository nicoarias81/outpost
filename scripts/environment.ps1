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
# Quote Windows argv using the backslash-before-quote and trailing-backslash rules.
function ConvertTo-OutpostArgumentString([string[]]$Arguments) {
    $quoted = foreach ($argument in $Arguments) {
        $escaped = [regex]::Replace([string]$argument, '(\\*)"', '$1$1\"')
        $escaped = [regex]::Replace($escaped, '(\\+)$', '$1$1')
        '"' + $escaped + '"'
    }
    return ($quoted -join ' ')
}
function Get-OutpostSourceFingerprint([ValidateSet('main','androidTest')][string]$Scope='main') {
    $root = Split-Path $PSScriptRoot -Parent
    $files = @(Get-ChildItem -LiteralPath (Join-Path $root "app/src/$Scope") -Recurse -File)
    foreach ($name in @('app/build.gradle','build.gradle','settings.gradle','gradle.properties',
        'gradle/wrapper/gradle-wrapper.properties','gradle/wrapper/gradle-wrapper.jar',
        'pdfbox-lock.json','llama-revision.txt','toolchain-lock.json','model-lock.json','bonsai-lock.json','judge-lock.json',
        'scripts/build.ps1','scripts/environment.ps1')) {
        $path = Join-Path $root $name
        if (-not (Test-Path -LiteralPath $path)) { throw "Required build input is missing: $name" }
        $files += Get-Item -LiteralPath $path
    }
    $rows = foreach ($file in ($files | Sort-Object FullName -Unique)) {
        $relative = $file.FullName.Substring($root.Length + 1).Replace('\','/')
        $hash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        "$relative=$hash"
    }
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($rows -join [char]10)))).Replace('-','').ToLowerInvariant()
    } finally { $sha.Dispose() }
}
