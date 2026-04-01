@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "ROOT=%~dp0"
set "H2_LOCK_FILE=%ROOT%ara-backend\data\ara-db.lock.db"

echo ==============================
echo Stopping Academic Resource Allocation System
echo ==============================
echo.

call :stop_port 8080 Backend
call :stop_port 3000 Frontend
call :clear_h2_lock

echo.
echo Done.
echo.
if /i "%ARA_NO_PAUSE%"=="1" exit /b 0
pause
exit /b 0

:stop_port
set "TARGET_PORT=%~1"
set "TARGET_NAME=%~2"
set "TARGET_PID="

for /f "tokens=5" %%P in ('netstat -ano ^| findstr /r /c:":%TARGET_PORT% .*LISTENING"') do (
  set "TARGET_PID=%%P"
  goto :got_pid
)

echo [OK] %TARGET_NAME% is not running on port %TARGET_PORT%.
exit /b 0

:got_pid
if not defined TARGET_PID (
  echo [OK] %TARGET_NAME% is not running on port %TARGET_PORT%.
  exit /b 0
)

echo [STOP] %TARGET_NAME% on port %TARGET_PORT% ^(PID !TARGET_PID!^)
taskkill /PID !TARGET_PID! /T /F >nul 2>&1

if errorlevel 1 (
  echo [WARN] Could not stop %TARGET_NAME% on port %TARGET_PORT%.
) else (
  echo [OK] %TARGET_NAME% stopped.
)

exit /b 0

:clear_h2_lock
if exist "%H2_LOCK_FILE%" (
  timeout /t 2 >nul
  del /f /q "%H2_LOCK_FILE%" >nul 2>&1
  if exist "%H2_LOCK_FILE%" (
    echo [WARN] H2 lock file is still present: %H2_LOCK_FILE%
  ) else (
    echo [OK] Removed backend H2 lock file.
  )
) else (
  echo [OK] No backend H2 lock file to remove.
)
exit /b 0
