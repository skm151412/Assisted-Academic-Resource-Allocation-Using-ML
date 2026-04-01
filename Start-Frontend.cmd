@echo off
setlocal EnableExtensions

set "ROOT=%~dp0"
set "FRONTEND_DIR=%ROOT%ara-frontend"

title ARA Frontend
cd /d "%FRONTEND_DIR%"
call npm start

echo.
echo Frontend process ended. Press any key to close this window.
pause >nul
