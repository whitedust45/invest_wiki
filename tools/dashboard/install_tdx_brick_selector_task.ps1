[CmdletBinding()]
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$RemainingArguments)

$Installer = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path "modules\short_term\schedules\install_tdx_brick_selector_task.ps1"
& $Installer @RemainingArguments
exit $LASTEXITCODE
