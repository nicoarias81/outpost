param([string]$Sdk=$env:ANDROID_HOME,[string]$Serial='emulator-5582',
    [ValidateSet('baseline','candidate','both')][string]$Phase='baseline',
    [ValidateSet('qwen15','bonsai17','bonsai4')][string]$Model='bonsai4',
    [string]$Manifest='eval/fixtures-v4.json',[string]$Fixtures='',
    [ValidateSet(1,2,4,8)][int]$Width=4,[int]$MaxTokens=96,[switch]$SkipInstall)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'environment.ps1')
$Sdk=Resolve-OutpostSdk $Sdk
$python=(Get-OutpostSettings).pythonExecutable
if(-not $python) { $python=(Get-Command python -ErrorAction Stop).Source }
$project=Split-Path $PSScriptRoot -Parent
$variants=if($Phase -eq 'both') { 'baseline,candidate' } else { $Phase }
$arguments=@((Join-Path $project 'eval/run.py'),'--sdk',$Sdk,'--serial',$Serial,'--manifest',$Manifest,
    '--model',$Model,'--variants',$variants,'--width',"$Width",'--max-tokens',"$MaxTokens")
if($Fixtures) { $arguments+=@('--fixtures',$Fixtures) }
if($SkipInstall) { $arguments+='--skip-install' }
& $python @arguments
if($LASTEXITCODE -ne 0) { throw 'Evaluation execution failed; inspect the new run directory.' }
