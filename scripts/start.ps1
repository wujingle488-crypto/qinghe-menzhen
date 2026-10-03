# 在仓库根目录启动青禾门诊：准备数据库、把本地知识库写入向量索引、启动后端和前端。
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
        throw "缺少命令：$name。请先安装并加入 PATH。"
    }
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

Require-Command java
Require-Command mvn
Require-Command npm
Require-Command python

Write-Host "1/5 检查 MySQL 库 commerce_cs"
$mysqlOk = $false
try {
    mysql -u commerce -pcommerce_cs_dev -e "SELECT 1" commerce_cs 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { $mysqlOk = $true }
} catch {
    $mysqlOk = $false
}
if (-not $mysqlOk) {
    if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) {
        throw "连不上 MySQL。请安装 MySQL 8，并用 root 执行：mysql -u root -p < scripts/init-mysql.sql"
    }
    if ($env:MYSQL_ROOT_PASSWORD) {
        mysql -u root "-p$env:MYSQL_ROOT_PASSWORD" --default-character-set=utf8mb4 -e "source scripts/init-mysql.sql"
    } else {
        throw "数据库 commerce_cs 还不存在。请执行：mysql -u root -p < scripts/init-mysql.sql"
    }
}

Write-Host "2/5 安装前端依赖"
Push-Location (Join-Path $Root "commerce-cs-agent\web")
if (-not (Test-Path "node_modules")) {
    npm install
}
Pop-Location

Write-Host "3/5 用仓库里的 Markdown 构建本地向量库"
$indexDir = Join-Path $Root "知识库\index"
$chromaDb = Join-Path $indexDir "chroma-data\chroma.sqlite3"
python -m pip install -r (Join-Path $indexDir "requirements.txt")
if (-not (Test-Path $chromaDb)) {
    python (Join-Path $indexDir "index_kb.py")
}
$chromaUp = Get-NetTCPConnection -LocalPort 8000 -State Listen -ErrorAction SilentlyContinue
if (-not $chromaUp) {
    $chromaExe = (Get-Command chroma -ErrorAction SilentlyContinue)?.Source
    if (-not $chromaExe) {
        $pythonHome = Split-Path (Get-Command python).Source
        $candidate = Join-Path $pythonHome "Scripts\chroma.exe"
        if (Test-Path $candidate) { $chromaExe = $candidate }
    }
    if (-not $chromaExe) {
        throw "已安装 chromadb，但找不到 chroma 命令。请把 Python 的 Scripts 目录加入 PATH 后重试。"
    }
    Start-Process -FilePath $chromaExe -ArgumentList @("run", "--path", (Join-Path $indexDir "chroma-data"), "--host", "127.0.0.1", "--port", "8000") -WorkingDirectory $indexDir -WindowStyle Hidden
}
$embedUp = Get-NetTCPConnection -LocalPort 8001 -State Listen -ErrorAction SilentlyContinue
if (-not $embedUp) {
    Start-Process -FilePath "python" -ArgumentList @((Join-Path $indexDir "embed_server.py")) -WorkingDirectory $indexDir -WindowStyle Hidden
}

Write-Host "4/5 编译并启动后端 http://127.0.0.1:8082"
$backend = Join-Path $Root "commerce-cs-agent"
Push-Location $backend
mvn -pl domain,agent-server -am install "-DskipTests" -q
Pop-Location
$backendUp = Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue
if ($backendUp) {
    Write-Host "8082 已在监听，跳过再次启动后端。"
} else {
    Start-Process -FilePath "mvn" -ArgumentList @("-f", "agent-server/pom.xml", "spring-boot:run") -WorkingDirectory $backend -WindowStyle Minimized
}

Write-Host "5/5 启动前端 http://127.0.0.1:5173"
$web = Join-Path $backend "web"
$frontUp = Get-NetTCPConnection -LocalPort 5173 -State Listen -ErrorAction SilentlyContinue
if ($frontUp) {
    Write-Host "5173 已在监听，跳过再次启动前端。"
} else {
    Start-Process -FilePath "npm" -ArgumentList @("run", "dev") -WorkingDirectory $web -WindowStyle Minimized
}

Write-Host ""
Write-Host "后端第一次启动会把教学知识文章写入 MySQL。打开 http://127.0.0.1:5173 注册账号后，知识库页面应和仓库里的文章一致。"
Write-Host "若 8082 半分钟后仍打不开，查看弹出的 Maven 窗口里的报错。"
