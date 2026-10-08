@echo off
cd /d "%~dp0"
echo Starting Qinghe Clinic (portable)...
echo Missing tools will be auto-installed (winget / download). First run may take a while.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-portable.ps1"
if errorlevel 1 (
  echo.
  echo Start failed. Check errors above.
  pause
  exit /b 1
)
echo.
echo Done. Open http://127.0.0.1:5173
pause
