[CmdletBinding()]
param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

$Root = (Resolve-Path $PSScriptRoot).Path
$Python = Get-Command py -ErrorAction SilentlyContinue
if ($Python) {
    & $Python.Source -3 (Join-Path $Root "invest.py") @Arguments
} else {
    & python (Join-Path $Root "invest.py") @Arguments
}
exit $LASTEXITCODE
