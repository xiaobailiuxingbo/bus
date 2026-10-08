param([string]$RedisHomePath = 'D:/dev/Redis-8.10.1-Windows-x64-cygwin-with-Service')

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $projectRoot '.local/redis.conf'
if (-not (Test-Path -LiteralPath $configPath)) { throw 'Local Redis configuration not found.' }
if (Get-NetTCPConnection -LocalPort 6380 -State Listen -ErrorAction SilentlyContinue) {
    Write-Output 'Port 6380 already has a listener; no additional instance started.'
    return
}
# redis-server.exe uses Cygwin paths for command-line configuration files.
$normalizedConfigPath = $configPath -replace '\\', '/'
$cygwinConfigPath = '/cygdrive/' + $normalizedConfigPath.Substring(0, 1).ToLowerInvariant() + $normalizedConfigPath.Substring(2)
$process = Start-Process -FilePath (Join-Path $RedisHomePath 'redis-server.exe') -ArgumentList $cygwinConfigPath -WorkingDirectory $RedisHomePath -WindowStyle Hidden -PassThru
Write-Output "Redis start requested on 127.0.0.1:6380 (PID $($process.Id))."
