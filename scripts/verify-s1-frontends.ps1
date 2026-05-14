param(
    [string[]]$Apps = @("admin", "lead")
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$env:Path = @($machinePath, $userPath) -join ";"

$pnpmCommand = Get-Command pnpm.cmd -ErrorAction SilentlyContinue
if (-not $pnpmCommand) {
    $pnpmCommand = Get-Command pnpm -ErrorAction Stop
}

$appConfigs = @{
    admin = @{
        Package = "@wecom-bft/admin"
        Port = 5173
        Url = "http://127.0.0.1:5173/admin/"
        Marker = '<div id="app"></div>'
    }
    wecomSidebar = @{
        Package = "@wecom-bft/wecom-sidebar-h5"
        Port = 5174
        Url = "http://127.0.0.1:5174/h5/wecom-sidebar/"
        Marker = '<div id="app"></div>'
    }
    supplier = @{
        Package = "@wecom-bft/supplier-h5"
        Port = 5175
        Url = "http://127.0.0.1:5175/h5/supplier/"
        Marker = '<div id="app"></div>'
    }
    lead = @{
        Package = "@wecom-bft/lead-h5"
        Port = 5176
        Url = "http://127.0.0.1:5176/h5/lead/"
        Marker = '<div id="app"></div>'
    }
}

$Apps = $Apps | ForEach-Object { $_ -split "," } | Where-Object { $_ }

function Wait-Frontend {
    param(
        [string]$Url,
        [string]$Marker,
        [System.Diagnostics.Process]$Process
    )

    $deadline = (Get-Date).AddSeconds(30)
    $lastError = $null
    while ((Get-Date) -lt $deadline) {
        if ($Process.HasExited) {
            throw "Frontend dev server exited early for $Url."
        }

        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200 -and $response.Content.Contains($Marker)) {
                return
            }
            $lastError = "Unexpected response from ${Url}: HTTP $($response.StatusCode)."
        } catch {
            $lastError = $_.Exception.Message
        }

        Start-Sleep -Seconds 1
    }

    throw "Frontend dev server was not reachable at $Url. Last error: $lastError"
}

function Stop-ProcessTree {
    param(
        [int]$ProcessId
    )

    $children = Get-CimInstance Win32_Process -Filter "ParentProcessId = $ProcessId" -ErrorAction SilentlyContinue
    foreach ($child in $children) {
        Stop-ProcessTree -ProcessId $child.ProcessId
    }

    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($process -and -not $process.HasExited) {
        Stop-Process -Id $ProcessId -Force
    }
}

$startedProcesses = @()

try {
    foreach ($app in $Apps) {
        if (-not $appConfigs.ContainsKey($app)) {
            throw "Unknown frontend app '$app'. Allowed values: $($appConfigs.Keys -join ', ')."
        }

        $config = $appConfigs[$app]
        $arguments = @(
            "--filter", $config.Package,
            "exec", "vite",
            "--host", "127.0.0.1",
            "--port", [string]$config.Port,
            "--strictPort"
        )

        $process = Start-Process `
            -FilePath $pnpmCommand.Source `
            -ArgumentList $arguments `
            -WorkingDirectory $repoRoot `
            -PassThru `
            -WindowStyle Hidden

        $startedProcesses += $process
        Wait-Frontend -Url $config.Url -Marker $config.Marker -Process $process
        Write-Host "OK $app $($config.Url)"
    }
} finally {
    foreach ($process in $startedProcesses) {
        Stop-ProcessTree -ProcessId $process.Id
    }
}
