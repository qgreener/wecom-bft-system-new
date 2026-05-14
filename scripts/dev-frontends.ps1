$ErrorActionPreference = "Stop"

pnpm install

$root = (Get-Location).Path
$scripts = @("dev:admin", "dev:wecom-sidebar", "dev:supplier", "dev:lead")
$jobs = foreach ($scriptName in $scripts) {
    Start-Job -Name $scriptName -ScriptBlock {
        param($rootPath, $scriptToRun)
        Set-Location $rootPath
        pnpm run $scriptToRun
    } -ArgumentList $root, $scriptName
}

Write-Host "Frontend dev servers are starting. Press Ctrl+C to stop this script, then run Get-Job | Stop-Job if needed."
Receive-Job -Job $jobs -Wait
