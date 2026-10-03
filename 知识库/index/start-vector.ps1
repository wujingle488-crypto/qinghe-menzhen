$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$chroma = Join-Path $here "chroma-data"
if (-not (Test-Path (Join-Path $chroma "chroma.sqlite3"))) {
    python (Join-Path $here "index_kb.py")
}
Start-Process -FilePath "chroma" -ArgumentList @("run", "--path", $chroma, "--host", "127.0.0.1", "--port", "8000") -WorkingDirectory $here -WindowStyle Hidden
Start-Process -FilePath "python" -ArgumentList @((Join-Path $here "embed_server.py")) -WorkingDirectory $here -WindowStyle Hidden
Write-Output "Chroma http://127.0.0.1:8000  嵌入 http://127.0.0.1:8001"
