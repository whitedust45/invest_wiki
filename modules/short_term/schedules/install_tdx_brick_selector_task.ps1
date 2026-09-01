[CmdletBinding()]
param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path,
    [string]$TdxExe = "F:\new_tdx64\TdxW.exe",
    [string]$TaskName = "TDX Brick Selector 14:40",
    [string]$PythonExe = "",
    [string]$PythonVersionArgument = ""
)

$ErrorActionPreference = "Stop"
$Runner = Join-Path $ProjectRoot "modules\short_term\schedules\run_tdx_brick_selector.ps1"

if (-not (Get-Command Register-ScheduledTask -ErrorAction SilentlyContinue)) {
    throw "当前 Windows 未提供 ScheduledTasks 模块。"
}
if (-not (Test-Path -LiteralPath $Runner -PathType Leaf)) {
    throw "未找到定时运行器: $Runner"
}
if (-not $PythonExe) {
    $pyCommand = Get-Command py -ErrorAction SilentlyContinue
    $pythonCommand = Get-Command python -ErrorAction SilentlyContinue
    if ($pyCommand) {
        $PythonExe = $pyCommand.Source
        $PythonVersionArgument = "-3"
    } elseif ($pythonCommand) {
        $PythonExe = $pythonCommand.Source
    } else {
        throw "未找到 py 或 python，请通过 -PythonExe 指定 Python 可执行文件。"
    }
}

function Quote-TaskArgument {
    param([string]$Value)

    return '"' + $Value.Replace('"', '\"') + '"'
}

$runnerArguments = @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", (Quote-TaskArgument $Runner),
    "-ProjectRoot", (Quote-TaskArgument $ProjectRoot),
    "-TdxExe", (Quote-TaskArgument $TdxExe),
    "-PythonExe", (Quote-TaskArgument $PythonExe),
    "-PythonVersionArgument", (Quote-TaskArgument $PythonVersionArgument)
) -join " "

$trigger = New-ScheduledTaskTrigger -Weekly -DaysOfWeek Monday, Tuesday, Wednesday, Thursday, Friday -At 14:40
$action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $runnerArguments
$settings = New-ScheduledTaskSettingsSet `
    -MultipleInstances IgnoreNew `
    -StartWhenAvailable:$false `
    -DisallowStartIfOnBatteries:$false `
    -StopIfGoingOnBatteries:$false
$userId = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$principal = New-ScheduledTaskPrincipal -UserId $userId -LogonType Interactive -RunLevel Limited

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Settings $settings `
    -Principal $principal `
    -Force | Out-Null

Write-Host "已创建或更新任务: $TaskName"
Write-Host "触发时间: 周一至周五 14:40；仅当前用户登录时运行；不补跑。"
