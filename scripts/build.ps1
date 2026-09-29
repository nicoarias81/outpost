param([string]$JavaHome = $env:JAVA_HOME, [string]$GradleHome = $env:GRADLE_USER_HOME, [switch]$Offline)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'environment.ps1')
$settings = Get-OutpostSettings
if (-not $JavaHome) { $JavaHome = $settings.javaHome }
if (-not $GradleHome) { $GradleHome = $settings.gradleHome }
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'Set JAVA_HOME, pass -JavaHome, or configure javaHome in .local/developer-settings.json.' }
$env:JAVA_HOME = $JavaHome
if ($GradleHome) { $env:GRADLE_USER_HOME = $GradleHome }
$gradle = Join-Path $project 'gradlew.bat'
if ($settings.gradleExecutable) { $gradle = $settings.gradleExecutable }
if (-not (Test-Path -LiteralPath $gradle)) { throw 'Configured Gradle executable does not exist.' }
$arguments = @('-p', $project, '--no-daemon', ':app:assembleDebug', ':app:assembleDebugAndroidTest', ':app:lintDebug')
if ($Offline) { $arguments += '--offline' }
& $gradle @arguments
if ($LASTEXITCODE -ne 0) { throw "Gradle failed with exit code $LASTEXITCODE" }
