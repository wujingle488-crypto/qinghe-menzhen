# Start Qinghe Clinic: check DB, build local KB index, start backend and frontend.
$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..")
Set-Location $Root

function Import-DotEnv {
    $envFile = Join-Path $Root ".env"
    if (-not (Test-Path $envFile)) {
        return
    }
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            return
        }
        $name, $value = $line.Split("=", 2)
        Set-Item -Path "Env:$($name.Trim())" -Value $value.Trim()
    }
}

function Require-Command([string]$name) {
    if (-not (Get-Command $name -ErrorAction SilentlyContinue)) {
        throw "Missing command: $name. Install it and add to PATH."
    }
}

function Find-IndexDir {
    $dirs = Get-ChildItem -Path $Root -Directory -ErrorAction SilentlyContinue
    foreach ($dir in $dirs) {
        $candidate = Join-Path $dir.FullName "index"
        if (Test-Path (Join-Path $candidate "requirements.txt")) {
            return $candidate
        }
    }
    throw "Knowledge base index folder not found under repo root."
}

Import-DotEnv

if (-not $env:JAVA_HOME -and (Test-Path "D:\DevelopTools\jdk21\jdk21\bin\java.exe")) {
    $env:JAVA_HOME = "D:\DevelopTools\jdk21\jdk21"
}
if ($env:JAVA_HOME) {
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
}
if (-not (Get-Command mvn -ErrorAction SilentlyContinue) -and (Test-Path "D:\DevelopTools\apache-maven-3.9.11\bin\mvn.cmd")) {
    $env:Path = "D:\DevelopTools\apache-maven-3.9.11\bin;" + $env:Path
}

# Prefer known MySQL installs when mysql is not on PATH.
$mysqlCandidates = @(
    "C:\Program Files\MySQL\MySQL Server 8.4\bin",
    "C:\Program Files\MySQL\MySQL Server 8.0\bin",
    "C:\Program Files\MySQL\MySQL Server 9.0\bin"
)
if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) {
    foreach ($bin in $mysqlCandidates) {
        if (Test-Path (Join-Path $bin "mysql.exe")) {
            $env:Path = "$bin;" + $env:Path
            break
        }
    }
}

Require-Command java
Require-Command mvn
Require-Command npm
Require-Command python

Write-Host "1/5 Checking MySQL database commerce_cs"
$mysqlCmd = Get-Command mysql -ErrorAction SilentlyContinue
if (-not $mysqlCmd) {
    throw 'Cannot find mysql.exe. Install MySQL 8 client tools or add MySQL bin to PATH.'
}
# Avoid PowerShell treating mysql password warnings on stderr as terminating errors.
$mysqlOk = $false
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
cmd /c "`"$($mysqlCmd.Source)`" -u commerce --password=commerce_cs_dev -e `"SELECT 1`" commerce_cs >nul 2>nul"
if ($LASTEXITCODE -eq 0) { $mysqlOk = $true }
$ErrorActionPreference = $prevEap
if (-not $mysqlOk) {
    if ($env:MYSQL_ROOT_PASSWORD) {
        cmd /c "`"$($mysqlCmd.Source)`" -u root --password=$env:MYSQL_ROOT_PASSWORD --default-character-set=utf8mb4 -e `"source scripts/init-mysql.sql`""
        cmd /c "`"$($mysqlCmd.Source)`" -u commerce --password=commerce_cs_dev -e `"SELECT 1`" commerce_cs >nul 2>nul"
        if ($LASTEXITCODE -ne 0) {
            throw 'Failed to initialize database commerce_cs.'
        }
    } else {
        throw 'Database commerce_cs is missing. Run: mysql -u root -p < scripts/init-mysql.sql'
    }
}

Write-Host "2/5 Installing frontend dependencies"
Push-Location (Join-Path $Root "commerce-cs-agent\web")
if (-not (Test-Path "node_modules")) {
    npm install
}
Pop-Location

Write-Host "3/5 Building local vector index from Markdown"
$indexDir = Find-IndexDir
$chromaDb = Join-Path $indexDir "chroma-data\chroma.sqlite3"
python -m pip install -r (Join-Path $indexDir "requirements.txt")
if (-not (Test-Path $chromaDb)) {
    python (Join-Path $indexDir "index_kb.py")
}
$chromaUp = Get-NetTCPConnection -LocalPort 8000 -State Listen -ErrorAction SilentlyContinue
if (-not $chromaUp) {
    $chromaCmd = Get-Command chroma -ErrorAction SilentlyContinue
    $chromaExe = $null
    if ($chromaCmd) { $chromaExe = $chromaCmd.Source }
    if (-not $chromaExe) {
        $pythonHome = Split-Path (Get-Command python).Source
        $candidate = Join-Path $pythonHome "Scripts\chroma.exe"
        if (Test-Path $candidate) { $chromaExe = $candidate }
    }
    if (-not $chromaExe) {
        throw "chromadb is installed but chroma command was not found. Add Python Scripts to PATH."
    }
    Start-Process -FilePath $chromaExe -ArgumentList @("run", "--path", (Join-Path $indexDir "chroma-data"), "--host", "127.0.0.1", "--port", "8000") -WorkingDirectory $indexDir -WindowStyle Hidden
}
$embedUp = Get-NetTCPConnection -LocalPort 8001 -State Listen -ErrorAction SilentlyContinue
if (-not $embedUp) {
    Start-Process -FilePath "python" -ArgumentList @((Join-Path $indexDir "embed_server.py")) -WorkingDirectory $indexDir -WindowStyle Hidden
}

Write-Host "4/5 Building and starting backend http://127.0.0.1:8082"
$backend = Join-Path $Root "commerce-cs-agent"
Push-Location $backend
mvn -pl domain,agent-server -am install "-DskipTests" -q
Pop-Location
# 代码改完后必须换新进程；占用 8082 时先停旧后端，避免继续跑旧 class。
$backendUp = Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue
if ($backendUp) {
    Write-Host "Stopping existing backend on 8082 so new build can take effect."
    $backendUp | ForEach-Object {
        Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue
    }
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object { $_.CommandLine -match 'CsApplication|agent-server[/\\]pom|spring-boot:run' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Seconds 2
}
$mvnExe = (Get-Command mvn.cmd -ErrorAction SilentlyContinue)
if (-not $mvnExe) { $mvnExe = Get-Command mvn }
Start-Process -FilePath $mvnExe.Source -ArgumentList @("-f", "agent-server/pom.xml", "spring-boot:run") -WorkingDirectory $backend -WindowStyle Minimized

Write-Host "5/5 Starting frontend http://127.0.0.1:5173"
$web = Join-Path $backend "web"
$frontUp = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
if ($frontUp) {
    Write-Host "Port 5173 already listening, skip frontend start."
} else {
    # On Windows Start-Process needs npm.cmd, not the extensionless npm shim.
    $npmExe = (Get-Command npm.cmd -ErrorAction SilentlyContinue)
    if (-not $npmExe) { $npmExe = Get-Command npm }
    Start-Process -FilePath $npmExe.Source -ArgumentList @("run", "dev") -WorkingDirectory $web -WindowStyle Minimized
}

Write-Host ""
Write-Host "Backend will seed teaching articles on first start."
Write-Host "Open http://127.0.0.1:5173 and register an account."
Write-Host "If 8082 is still down after ~30s, check the Maven window for errors."
