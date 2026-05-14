$ErrorActionPreference = "Stop"

$machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
$env:Path = @($machinePath, $userPath) -join ";"
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable("JAVA_HOME", "Machine")
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = [Environment]::GetEnvironmentVariable("JAVA_HOME", "User")
}

mvn -f backend/pom.xml test
pnpm install
pnpm build
