@echo off
setlocal EnableExtensions

set "ROOT=%~dp0"
set "BACKEND_DIR=%ROOT%ara-backend"
set "MAVEN_CMD=%USERPROFILE%\.maven\apache-maven-3.9.6\bin\mvn.cmd"
set "H2_LOCK_FILE=%BACKEND_DIR%\data\ara-db.lock.db"
set "NVIDIA_API_KEY=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"
set "NVIDIA_MODEL=meta/llama-4-maverick-17b-128e-instruct"
set "NVIDIA_BASE_URL=https://integrate.api.nvidia.com/v1"

title ARA Backend
cd /d "%BACKEND_DIR%"

if exist "%H2_LOCK_FILE%" (
  del /f /q "%H2_LOCK_FILE%" >nul 2>&1
)

if exist "%MAVEN_CMD%" (
  call "%MAVEN_CMD%" clean jetty:run
) else (
  mvn clean jetty:run
)

echo.
echo Backend process ended. Press any key to close this window.
pause >nul
