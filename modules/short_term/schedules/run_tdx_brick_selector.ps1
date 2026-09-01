[CmdletBinding()]
param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path,
    [string]$TdxExe = "F:\new_tdx64\TdxW.exe",
    [string]$PythonExe = "py",
    [string]$PythonVersionArgument = "-3",
    [int]$PollSeconds = 30,
    [string]$StartTime = "14:40",
    [string]$DeadlineTime = "14:55"
)

$ErrorActionPreference = "Stop"
$DateKey = Get-Date -Format "yyyy-MM-dd"
$OutputRoot = Join-Path $ProjectRoot "data\tdx-brick-selector"
$ReportDirectory = Join-Path $OutputRoot "reports"
$LogDirectory = Join-Path $OutputRoot "logs"
$ResultPath = Join-Path $ReportDirectory "$DateKey.md"
$LogPath = Join-Path $LogDirectory "$DateKey.log"
$SelectorScript = Join-Path $ProjectRoot "modules\short_term\strategies\brick.py"
$TdxRoot = Split-Path -Parent $TdxExe
$Deadline = (Get-Date).Date.Add([TimeSpan]::Parse($DeadlineTime))

New-Item -ItemType Directory -Force -Path $ReportDirectory, $LogDirectory | Out-Null

function Write-RunLog {
    param([string]$Message)

    $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $Message"
    $line | Tee-Object -FilePath $LogPath -Append
}

function Invoke-Selector {
    param([string[]]$SelectorArguments)

    $commandArguments = @()
    if ($PythonVersionArgument) {
        $commandArguments += $PythonVersionArgument
    }
    $commandArguments += $SelectorScript
    $commandArguments += $SelectorArguments

    $output = & $PythonExe @commandArguments 2>&1
    $exitCode = $LASTEXITCODE
    foreach ($line in $output) {
        $line.ToString() | Tee-Object -FilePath $LogPath -Append
    }
    return $exitCode
}

try {
    Write-RunLog "定时运行器启动，计划时点 $StartTime，截止时点 $DeadlineTime。"
    if ((Get-Date) -gt $Deadline) {
        Write-RunLog "当前时间已超过截止时点，取消当天运行。"
        exit 1
    }
    if (-not (Test-Path -LiteralPath $SelectorScript -PathType Leaf)) {
        Write-RunLog "未找到筛选脚本: $SelectorScript"
        exit 1
    }
    if (-not (Get-Process -Name "TdxW" -ErrorAction SilentlyContinue)) {
        if (-not (Test-Path -LiteralPath $TdxExe -PathType Leaf)) {
            Write-RunLog "未找到通达信程序: $TdxExe"
            exit 1
        }
        Write-RunLog "未检测到 TdxW.exe，正在启动 $TdxExe。"
        Start-Process -FilePath $TdxExe -WorkingDirectory $TdxRoot
    } else {
        Write-RunLog "检测到正在运行的 TdxW.exe，复用当前客户端。"
    }

    $ready = $false
    while ((Get-Date) -le $Deadline) {
        Write-RunLog "执行 TQ 预检。"
        $preflightCode = Invoke-Selector @(
            "--tdx-path", $TdxRoot,
            "--concept-limit", "5",
            "--preflight"
        )
        if ($preflightCode -eq 0) {
            $ready = $true
            Write-RunLog "TQ 预检通过。"
            break
        }
        Write-RunLog "TQ 尚未就绪，退出码 $preflightCode；$PollSeconds 秒后重试。"
        Start-Sleep -Seconds $PollSeconds
    }

    if (-not $ready) {
        Write-RunLog "截止 $DeadlineTime 前 TQ 未就绪，当天不补跑。"
        exit 1
    }

    Write-RunLog "开始执行通达信砖型图筛选。"
    $selectorCode = Invoke-Selector @(
        "--tdx-path", $TdxRoot,
        "--engine", "both",
        "--concept-limit", "5",
        "--run-time", $StartTime,
        "--output", $ResultPath
    )
    Write-RunLog "筛选结束，退出码 $selectorCode，结果文件: $ResultPath"
    exit $selectorCode
} catch {
    Write-RunLog "运行器异常: $($_.Exception.Message)"
    exit 1
}
