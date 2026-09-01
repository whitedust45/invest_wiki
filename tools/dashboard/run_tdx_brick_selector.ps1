[CmdletBinding()]
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$RemainingArguments)

$Runner = Join-Path (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path "modules\short_term\schedules\run_tdx_brick_selector.ps1"
& $Runner @RemainingArguments
exit $LASTEXITCODE
