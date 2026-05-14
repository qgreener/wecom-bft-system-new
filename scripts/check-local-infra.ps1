param(
    [string]$EnvFile = ".env.example",
    [switch]$CreateStoragePath,
    [switch]$ReportOnly
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $repoRoot $EnvFile

function Read-EnvFile {
    param([string]$Path)

    $values = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $values
    }

    Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            return
        }

        $key, $value = $line.Split("=", 2)
        $values[$key.Trim()] = $value.Trim()
    }

    return $values
}

function Get-ConfigValue {
    param(
        [hashtable]$Values,
        [string]$Name,
        [string]$DefaultValue
    )

    if ($Values.ContainsKey($Name) -and $Values[$Name]) {
        return $Values[$Name]
    }
    return $DefaultValue
}

function Test-TcpPort {
    param(
        [string]$HostName,
        [int]$Port
    )

    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $connect = $client.BeginConnect($HostName, $Port, $null, $null)
        if (-not $connect.AsyncWaitHandle.WaitOne([TimeSpan]::FromSeconds(2))) {
            return $false
        }
        $client.EndConnect($connect)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Add-Result {
    param(
        [System.Collections.Generic.List[object]]$Results,
        [string]$Name,
        [bool]$Ok,
        [string]$Detail,
        [bool]$Required = $true
    )

    $Results.Add([pscustomobject]@{
        Name = $Name
        Status = if ($Ok) { "OK" } elseif ($Required) { "MISSING" } else { "OPTIONAL" }
        Required = $Required
        Detail = $Detail
    })
}

$values = Read-EnvFile -Path $envPath
$mysqlHost = Get-ConfigValue $values "MYSQL_HOST" "127.0.0.1"
$mysqlPort = [int](Get-ConfigValue $values "MYSQL_PORT" "3306")
$redisHost = Get-ConfigValue $values "REDIS_HOST" "127.0.0.1"
$redisPort = [int](Get-ConfigValue $values "REDIS_PORT" "6379")
$storagePath = Get-ConfigValue $values "FILE_UPLOAD_BASE_PATH" "./storage/files"
$resolvedStoragePath = if ([System.IO.Path]::IsPathRooted($storagePath)) {
    $storagePath
} else {
    Join-Path $repoRoot $storagePath
}

if ($CreateStoragePath -and -not (Test-Path -LiteralPath $resolvedStoragePath)) {
    New-Item -ItemType Directory -Path $resolvedStoragePath | Out-Null
}

$results = [System.Collections.Generic.List[object]]::new()

Add-Result $results "mysql-tcp" (Test-TcpPort $mysqlHost $mysqlPort) "$mysqlHost`:$mysqlPort"
Add-Result $results "redis-tcp" (Test-TcpPort $redisHost $redisPort) "$redisHost`:$redisPort"
Add-Result $results "file-storage-path" (Test-Path -LiteralPath $resolvedStoragePath) $resolvedStoragePath
Add-Result $results "nginx-template" (Test-Path -LiteralPath (Join-Path $repoRoot "deploy/nginx/wecom-bft.conf.template")) "deploy/nginx/wecom-bft.conf.template" $false
Add-Result $results "vite-dev-proxy" (Test-Path -LiteralPath (Join-Path $repoRoot "frontend/admin/vite.config.ts")) "frontend/*/vite.config.ts" $false

$results | Format-Table -AutoSize

$missingRequired = $results | Where-Object { $_.Required -and $_.Status -ne "OK" }
if ($missingRequired -and -not $ReportOnly) {
    throw "Required local infrastructure is not ready: $($missingRequired.Name -join ', ')."
}
