param(
    [ValidateSet('start', 'stop', 'status', 'cli')]
    [string]$Action = 'status',
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$RedisArgs
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$runtime = Join-Path $root 'tools\redis'
$bin = Join-Path $runtime '8.10.2\Redis-8.10.2-Windows-x64-cygwin'
$config = Join-Path $runtime 'redis.conf'
if (!(Test-Path $config)) { throw 'Local Redis is not installed. See scripts/redis.md.' }
$passwordLine = Get-Content $config | Where-Object { $_ -match '^requirepass ' }
$previousAuth = $env:REDISCLI_AUTH
$env:REDISCLI_AUTH = ($passwordLine -replace '^requirepass\s+', '').Trim()
try {
    if ($Action -eq 'start') {
        $listener = Get-NetTCPConnection -LocalPort 6379 -State Listen -ErrorAction SilentlyContinue
        if (!$listener) {
            Start-Process -FilePath (Join-Path $bin 'redis-server.exe') -ArgumentList 'redis.conf' -WorkingDirectory $runtime -WindowStyle Hidden | Out-Null
        }
        $ready = $false
        for ($i = 0; $i -lt 20; $i++) {
            $reply = & (Join-Path $bin 'redis-cli.exe') -h 127.0.0.1 -p 6379 PING 2>$null
            if ($LASTEXITCODE -eq 0 -and $reply -eq 'PONG') { $ready = $true; break }
            Start-Sleep -Milliseconds 250
        }
        if (!$ready) { throw 'Redis did not become ready. Check tools/redis/redis.log and port 6379.' }
        Write-Output 'Redis ready at 127.0.0.1:6379 (PONG)'
    } elseif ($Action -eq 'stop') {
        & (Join-Path $bin 'redis-cli.exe') -h 127.0.0.1 -p 6379 SHUTDOWN SAVE
        if ($LASTEXITCODE -ne 0) { throw 'Redis shutdown failed.' }
    } elseif ($Action -eq 'status') {
        & (Join-Path $bin 'redis-cli.exe') -h 127.0.0.1 -p 6379 PING
        if ($LASTEXITCODE -ne 0) { throw 'Redis is unavailable.' }
    } else {
        & (Join-Path $bin 'redis-cli.exe') -h 127.0.0.1 -p 6379 @RedisArgs
        if ($LASTEXITCODE -ne 0) { throw 'Redis command failed.' }
    }
} finally {
    $env:REDISCLI_AUTH = $previousAuth
}
