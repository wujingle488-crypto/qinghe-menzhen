# Ensure java / mvn / npm / python / mysql exist; install missing ones on Windows.
# Dot-source from start scripts. Tools land under %LOCALAPPDATA%\QingheClinic\tools when needed.
$ErrorActionPreference = "Stop"

if (-not $script:QingheToolsRoot) {
    $script:QingheToolsRoot = Join-Path $env:LOCALAPPDATA "QingheClinic\tools"
}
if (-not $script:QinghePinnedPath) {
    $script:QinghePinnedPath = New-Object System.Collections.Generic.List[string]
}

function Refresh-EnvPath {
    $machine = [System.Environment]::GetEnvironmentVariable("Path", "Machine")
    $user = [System.Environment]::GetEnvironmentVariable("Path", "User")
    $env:Path = (@($machine, $user) | Where-Object { $_ }) -join ";"
    if ($env:JAVA_HOME) {
        $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
    }
    # Later checks refresh Machine/User PATH and would otherwise drop tools
    # that were only added for this process (portable Maven, Node, Python, MySQL).
    foreach ($dir in @($script:QinghePinnedPath)) {
        if ($dir -and (Test-Path $dir)) {
            $env:Path = "$dir;" + $env:Path
        }
    }
}

function Test-HasCmd([string]$Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Add-PathFront([string]$Dir) {
    if (-not $Dir -or -not (Test-Path $Dir)) { return }
    $full = [System.IO.Path]::GetFullPath($Dir).TrimEnd('\')
    $alreadyPinned = @($script:QinghePinnedPath | Where-Object {
        $_.TrimEnd('\').Equals($full, [StringComparison]::OrdinalIgnoreCase)
    })
    if ($alreadyPinned.Count -eq 0) {
        $script:QinghePinnedPath.Insert(0, $full)
    }
    $onPath = @($env:Path -split ";" | Where-Object {
        $_ -and $_.TrimEnd('\').Equals($full, [StringComparison]::OrdinalIgnoreCase)
    })
    if ($onPath.Count -gt 0) { return }
    $env:Path = "$full;" + $env:Path
}

function Get-Winget {
    return Get-Command winget -ErrorAction SilentlyContinue
}

function Install-WingetId {
    param(
        [Parameter(Mandatory = $true)][string]$Id,
        [string]$ExtraArgs = ""
    )
    $winget = Get-Winget
    if (-not $winget) {
        throw "winget not found. Install App Installer from Microsoft Store, then retry."
    }
    Write-Host "  winget install $Id ..."
    $argLine = "install --id $Id -e --accept-package-agreements --accept-source-agreements --disable-interactivity"
    if ($ExtraArgs) { $argLine = "$argLine $ExtraArgs" }
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    cmd /c "`"$($winget.Source)`" $argLine"
    $code = $LASTEXITCODE
    $ErrorActionPreference = $prev
    # 0 = success, -1978335189 / 0x8A15002B often means already installed
    if ($code -ne 0 -and $code -ne -1978335189) {
        Write-Host "  winget exit code: $code (will verify command after PATH refresh)"
    }
    Refresh-EnvPath
}

function Get-WebFile {
    param(
        [Parameter(Mandatory = $true)][string]$Url,
        [Parameter(Mandatory = $true)][string]$OutFile
    )
    $dir = Split-Path -Parent $OutFile
    if (-not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    Write-Host "  downloading $Url"
    try {
        Invoke-WebRequest -Uri $Url -OutFile $OutFile -UseBasicParsing
    } catch {
        # Some environments block Invoke-WebRequest progress / TLS quirks; fall back to curl.exe
        if (Get-Command curl.exe -ErrorAction SilentlyContinue) {
            & curl.exe -L --fail -o $OutFile $Url
            if ($LASTEXITCODE -ne 0) { throw "Download failed: $Url" }
        } else {
            throw
        }
    }
}

function Expand-ZipClean {
    param(
        [Parameter(Mandatory = $true)][string]$ZipPath,
        [Parameter(Mandatory = $true)][string]$DestDir
    )
    if (Test-Path $DestDir) {
        Remove-Item -Recurse -Force $DestDir
    }
    $stage = Join-Path $script:QingheToolsRoot ("_extract_" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $stage -Force | Out-Null
    try {
        Expand-Archive -Path $ZipPath -DestinationPath $stage -Force
        $inner = Get-ChildItem -Path $stage -Directory | Select-Object -First 1
        if (-not $inner) { throw "Zip contained no directory: $ZipPath" }
        New-Item -ItemType Directory -Path (Split-Path -Parent $DestDir) -Force | Out-Null
        Move-Item -Path $inner.FullName -Destination $DestDir
    } finally {
        if (Test-Path $stage) { Remove-Item -Recurse -Force $stage -ErrorAction SilentlyContinue }
    }
}

function Find-JavaHome {
    $candidates = @()
    if ($env:JAVA_HOME) { $candidates += $env:JAVA_HOME }
    $candidates += @(
        (Get-ChildItem "C:\Program Files\Microsoft" -Filter "jdk-21*" -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
        (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Filter "jdk-21*" -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
        (Get-ChildItem "C:\Program Files\Java" -Filter "jdk-21*" -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
        (Get-ChildItem "C:\Program Files\Microsoft" -Filter "jdk-*" -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    )
    foreach ($jdkHome in $candidates) {
        if ($jdkHome -and (Test-Path (Join-Path $jdkHome "bin\java.exe"))) {
            return $jdkHome
        }
    }
    return $null
}

function Ensure-Java {
    Refresh-EnvPath
    $jdkHome = Find-JavaHome
    if ($jdkHome) {
        $env:JAVA_HOME = $jdkHome
        Add-PathFront (Join-Path $jdkHome "bin")
    }
    if (Test-HasCmd "java") {
        Write-Host "  java: OK"
        return
    }
    Write-Host "  java: missing, installing Microsoft OpenJDK 21..."
    Install-WingetId "Microsoft.OpenJDK.21"
    $jdkHome = Find-JavaHome
    if ($jdkHome) {
        $env:JAVA_HOME = $jdkHome
        Add-PathFront (Join-Path $jdkHome "bin")
    }
    if (-not (Test-HasCmd "java")) {
        throw "Java still missing after install. Reopen the terminal or install JDK 21 and add it to PATH."
    }
    Write-Host "  java: installed"
}

function Ensure-Maven {
    Refresh-EnvPath
    if (Test-HasCmd "mvn") {
        Write-Host "  mvn: OK"
        return
    }
    $ver = "3.9.9"
    $mavenHome = Join-Path $script:QingheToolsRoot "apache-maven-$ver"
    $mvnCmd = Join-Path $mavenHome "bin\mvn.cmd"
    if (-not (Test-Path $mvnCmd)) {
        Write-Host "  mvn: missing, downloading Apache Maven $ver..."
        $zip = Join-Path $script:QingheToolsRoot "apache-maven-$ver-bin.zip"
        $urls = @(
            "https://dlcdn.apache.org/maven/maven-3/$ver/binaries/apache-maven-$ver-bin.zip",
            "https://archive.apache.org/dist/maven/maven-3/$ver/binaries/apache-maven-$ver-bin.zip"
        )
        $ok = $false
        foreach ($url in $urls) {
            try {
                Get-WebFile -Url $url -OutFile $zip
                $ok = $true
                break
            } catch {
                Write-Host "  mirror failed: $url"
            }
        }
        if (-not $ok) { throw "Maven download failed. Check network and retry." }
        Expand-ZipClean -ZipPath $zip -DestDir $mavenHome
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
    }
    Add-PathFront (Join-Path $mavenHome "bin")
    if (-not (Test-HasCmd "mvn")) {
        throw "Maven still missing after install."
    }
    Write-Host "  mvn: installed ($mavenHome)"
}

function Ensure-Node {
    Refresh-EnvPath
    $nodeDirs = @(
        (Join-Path $env:ProgramFiles "nodejs"),
        (Join-Path ${env:ProgramFiles(x86)} "nodejs")
    )
    foreach ($d in $nodeDirs) { Add-PathFront $d }
    if (Test-HasCmd "npm") {
        Write-Host "  npm: OK"
        return
    }
    Write-Host "  npm: missing, installing Node.js LTS..."
    Install-WingetId "OpenJS.NodeJS.LTS"
    foreach ($d in $nodeDirs) { Add-PathFront $d }
    Refresh-EnvPath
    if (-not (Test-HasCmd "npm")) {
        throw "npm still missing after Node.js install. Reopen the terminal and retry."
    }
    Write-Host "  npm: installed"
}

function Ensure-Python {
    Refresh-EnvPath
    $pyDirs = @(
        (Join-Path $env:LOCALAPPDATA "Programs\Python\Python312"),
        (Join-Path $env:LOCALAPPDATA "Programs\Python\Python311"),
        (Join-Path $env:LOCALAPPDATA "Programs\Python\Python313"),
        (Join-Path $env:ProgramFiles "Python312"),
        (Join-Path $env:ProgramFiles "Python311")
    )
    foreach ($d in $pyDirs) {
        Add-PathFront $d
        Add-PathFront (Join-Path $d "Scripts")
    }
    if (Test-HasCmd "python") {
        Write-Host "  python: OK"
        return
    }
    Write-Host "  python: missing, installing Python 3.12..."
    Install-WingetId "Python.Python.3.12" -ExtraArgs '--scope user'
    Refresh-EnvPath
    foreach ($d in $pyDirs) {
        Add-PathFront $d
        Add-PathFront (Join-Path $d "Scripts")
    }
    # Windows Store alias stub sometimes registers "python" but fails; prefer real install path.
    if (-not (Test-HasCmd "python")) {
        foreach ($d in $pyDirs) {
            $exe = Join-Path $d "python.exe"
            if (Test-Path $exe) {
                Add-PathFront $d
                Add-PathFront (Join-Path $d "Scripts")
                break
            }
        }
    }
    if (-not (Test-HasCmd "python")) {
        throw "Python still missing after install. Reinstall with Add to PATH, or reopen the terminal."
    }
    Write-Host "  python: installed"
}

function Find-MySqlBin {
    if (Test-HasCmd "mysql") {
        return (Split-Path -Parent (Get-Command mysql).Source)
    }
    $candidates = @(
        "C:\Program Files\MySQL\MySQL Server 8.4\bin",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin",
        "C:\Program Files\MySQL\MySQL Server 9.0\bin",
        "C:\Program Files\MariaDB 11.4\bin",
        "C:\Program Files\MariaDB 11.8\bin",
        "C:\Program Files\MariaDB 10.11\bin",
        (Join-Path $script:QingheToolsRoot "mysql\bin")
    )
    foreach ($bin in $candidates) {
        if (Test-Path (Join-Path $bin "mysql.exe")) { return $bin }
    }
    return $null
}

function Test-TcpPortOpen([int]$Port) {
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect("127.0.0.1", $Port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne(800, $false)
        if ($ok -and $client.Connected) {
            $client.EndConnect($iar)
            $client.Close()
            return $true
        }
        $client.Close()
    } catch { }
    return $false
}

function Start-PortableMySql {
    param([Parameter(Mandatory = $true)][string]$MySqlHome)

    $bin = Join-Path $MySqlHome "bin"
    $data = Join-Path $MySqlHome "data"
    $mysqld = Join-Path $bin "mysqld.exe"
    if (-not (Test-Path $mysqld)) {
        throw "Portable MySQL missing mysqld.exe: $mysqld"
    }

    if (-not (Test-Path (Join-Path $data "mysql"))) {
        Write-Host "  initializing portable MySQL data directory (root password empty)..."
        New-Item -ItemType Directory -Path $data -Force | Out-Null
        $prev = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & $mysqld --datadir="$data" --basedir="$MySqlHome" --initialize-insecure --console
        $ErrorActionPreference = $prev
    }

    if (-not (Test-TcpPortOpen 3306)) {
        Write-Host "  starting portable MySQL on 127.0.0.1:3306..."
        Start-Process -FilePath $mysqld -ArgumentList @(
            "--datadir=$data",
            "--basedir=$MySqlHome",
            "--port=3306",
            "--bind-address=127.0.0.1"
        ) -WorkingDirectory $MySqlHome -WindowStyle Hidden
        $deadline = (Get-Date).AddSeconds(45)
        while ((Get-Date) -lt $deadline) {
            if (Test-TcpPortOpen 3306) { break }
            Start-Sleep -Milliseconds 500
        }
        if (-not (Test-TcpPortOpen 3306)) {
            throw "Portable MySQL failed to start within 45s. Is port 3306 already in use?"
        }
    }
}

function Ensure-MySql {
    Refresh-EnvPath
    $bin = Find-MySqlBin
    if ($bin) {
        Add-PathFront $bin
        if (Test-TcpPortOpen 3306) {
            Write-Host "  mysql: OK"
            return
        }
        # Client exists but server may be stopped; try Windows service, else portable home.
        $svc = Get-Service -Name "MySQL*", "MariaDB*" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($svc -and $svc.Status -ne "Running") {
            try {
                Start-Service $svc.Name
                Write-Host "  mysql: started service $($svc.Name)"
                return
            } catch {
                Write-Host "  mysql: could not start service (need admin?). will try portable if needed."
            }
        }
        $portableHome = Join-Path $script:QingheToolsRoot "mysql"
        if (Test-Path (Join-Path $portableHome "bin\mysqld.exe")) {
            Start-PortableMySql -MySqlHome $portableHome
            Add-PathFront (Join-Path $portableHome "bin")
            Write-Host "  mysql: portable server running"
            return
        }
        if (Test-HasCmd "mysql") {
            Write-Host "  mysql: client OK (ensure server is running on 3306)"
            return
        }
    }

    Write-Host "  mysql: missing, installing portable MySQL 8.0..."
    $ver = "8.0.40"
    $mysqlHome = Join-Path $script:QingheToolsRoot "mysql"
    $mysqld = Join-Path $mysqlHome "bin\mysqld.exe"
    if (-not (Test-Path $mysqld)) {
        $zip = Join-Path $script:QingheToolsRoot "mysql-$ver-winx64.zip"
        $urls = @(
            "https://cdn.mysql.com/archives/mysql-8.0/mysql-$ver-winx64.zip",
            "https://cdn.mysql.com/Downloads/MySQL-8.0/mysql-$ver-winx64.zip"
        )
        $ok = $false
        foreach ($url in $urls) {
            try {
                Get-WebFile -Url $url -OutFile $zip
                $ok = $true
                break
            } catch {
                Write-Host "  mirror failed: $url"
            }
        }
        if (-not $ok) {
            Write-Host "  portable MySQL download failed, trying winget Oracle.MySQL..."
            Install-WingetId "Oracle.MySQL"
            $bin = Find-MySqlBin
            if ($bin) {
                Add-PathFront $bin
                Write-Host "  mysql: installed via winget (if DB connect fails, run scripts/init-mysql.sql as root)"
                return
            }
            throw "MySQL auto-install failed. Install MySQL 8 manually and retry."
        }
        Expand-ZipClean -ZipPath $zip -DestDir $mysqlHome
        Remove-Item $zip -Force -ErrorAction SilentlyContinue
    }
    Start-PortableMySql -MySqlHome $mysqlHome
    Add-PathFront (Join-Path $mysqlHome "bin")
    if (-not (Test-HasCmd "mysql")) {
        throw "mysql client still missing after install."
    }
    Write-Host "  mysql: portable installed and running (root password empty)"
}

function Ensure-QingheTools {
    Write-Host "0/5 Checking tools (auto-install if missing)"
    New-Item -ItemType Directory -Path $script:QingheToolsRoot -Force | Out-Null
    Ensure-Java
    Ensure-Maven
    Ensure-Node
    Ensure-Python
    Ensure-MySql
    Refresh-EnvPath
    Write-Host "  tools ready."
}

function Test-CommerceDbReady {
    $mysqlCmd = Get-Command mysql -ErrorAction SilentlyContinue
    if (-not $mysqlCmd) { return $false }
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $mysqlCmd.Source -u commerce --password=commerce_cs_dev -e "SELECT 1" commerce_cs 1>$null 2>$null
    $ok = ($LASTEXITCODE -eq 0)
    $ErrorActionPreference = $prevEap
    return $ok
}

function Initialize-CommerceDatabase {
    param([Parameter(Mandatory = $true)][string]$RepoRoot)

    if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) {
        throw "Cannot find mysql.exe after tool setup."
    }
    if (Test-CommerceDbReady) { return }

    $initSql = Join-Path $RepoRoot "scripts\init-mysql.sql"
    $attempts = New-Object System.Collections.Generic.List[object]
    if ($env:MYSQL_ROOT_PASSWORD) {
        $attempts.Add(@("-u", "root", "--password=$env:MYSQL_ROOT_PASSWORD")) | Out-Null
    }
    # Portable MySQL from ensure-tools uses empty root password.
    $attempts.Add(@("-u", "root", "--password=")) | Out-Null
    $attempts.Add(@("-u", "root")) | Out-Null

    $mysqlExe = (Get-Command mysql).Source
    foreach ($rootArgs in $attempts) {
        Write-Host "  initializing commerce_cs via root..."
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $allArgs = @($rootArgs) + @("--default-character-set=utf8mb4", "-e", "source $($initSql.Replace('\', '/'))")
        & $mysqlExe @allArgs 1>$null 2>$null
        $ErrorActionPreference = $prevEap
        if (Test-CommerceDbReady) { return }
    }

    throw "Database commerce_cs is missing. Set MYSQL_ROOT_PASSWORD in .env, or run scripts/init-mysql.sql as MySQL root."
}
