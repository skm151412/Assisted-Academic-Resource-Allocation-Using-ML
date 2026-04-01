@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "ROOT=%~dp0"
set "BACKEND_DIR=%ROOT%ara-backend"
set "FRONTEND_DIR=%ROOT%ara-frontend"
set "MAVEN_CMD=%USERPROFILE%\.maven\apache-maven-3.9.6\bin\mvn.cmd"
set "H2_LOCK_FILE=%BACKEND_DIR%\data\ara-db.lock.db"
set "BACKEND_STARTER=%ROOT%Start-Backend.cmd"
set "FRONTEND_STARTER=%ROOT%Start-Frontend.cmd"

if not exist "%BACKEND_DIR%\pom.xml" (
  echo [ERROR] Backend folder not found: %BACKEND_DIR%
  goto :finish
)

if not exist "%FRONTEND_DIR%\package.json" (
  echo [ERROR] Frontend folder not found: %FRONTEND_DIR%
  goto :finish
)

if not exist "%BACKEND_STARTER%" (
  echo [ERROR] Backend starter not found: %BACKEND_STARTER%
  goto :finish
)

if not exist "%FRONTEND_STARTER%" (
  echo [ERROR] Frontend starter not found: %FRONTEND_STARTER%
  goto :finish
)

if not exist "%MAVEN_CMD%" (
  for %%I in (mvn.cmd mvn) do (
    where %%I >nul 2>&1
    if not errorlevel 1 (
      set "MAVEN_CMD=%%I"
      goto :maven_ready
    )
  )
  echo [ERROR] Maven was not found. Install Maven or keep it at:
  echo         %USERPROFILE%\.maven\apache-maven-3.9.6\bin\mvn.cmd
  goto :finish
)

:maven_ready
where npm >nul 2>&1
if errorlevel 1 (
  echo [ERROR] npm was not found. Install Node.js and try again.
  goto :finish
)

echo.
echo ==============================
echo Starting Academic Resource Allocation System
echo ==============================
echo Root: %ROOT%
echo.

call :ensure_backend
call :ensure_frontend

echo.
echo Waiting for services to come online...
call :wait_for_backend
call :backend_healthy
if errorlevel 1 (
  echo [WARN] Backend is not healthy yet. Check the "ARA Backend" window.
) else (
  echo [OK] Backend started from this launcher.
)
call :wait_for_frontend
call :frontend_healthy
if errorlevel 1 (
  echo [WARN] Frontend is not healthy yet. Check the "ARA Frontend" window.
) else (
  echo [OK] Frontend started from this launcher.
)

echo.
echo --------------------------------------
echo Backend : http://localhost:8080
echo Frontend: http://localhost:3000
echo Login   : admin / admin123
echo --------------------------------------
echo.
echo You can close this window. Backend and frontend keep running
echo in their own terminal windows.
goto :finish

:ensure_backend
call :backend_healthy
if not errorlevel 1 (
  echo [OK] Backend is already running.
  exit /b 0
)

call :free_port_if_needed 8080
call :clear_h2_lock
echo [START] Launching backend on port 8080...
start "ARA Backend" "%BACKEND_STARTER%"
exit /b 0

:ensure_frontend
call :frontend_healthy
if not errorlevel 1 (
  echo [OK] Frontend is already running.
  exit /b 0
)

call :free_port_if_needed 3000
echo [START] Launching frontend on port 3000...
start "ARA Frontend" "%FRONTEND_STARTER%"
exit /b 0

:wait_for_backend
set /a tries=0
:wait_backend_loop
call :backend_healthy
if not errorlevel 1 (
  echo [OK] Backend is ready.
  exit /b 0
)
set /a tries+=1
if !tries! GEQ 45 (
  echo [WARN] Backend did not report healthy within 90 seconds.
  exit /b 0
)
timeout /t 2 >nul
goto :wait_backend_loop

:wait_for_frontend
set /a tries=0
:wait_frontend_loop
call :frontend_healthy
if not errorlevel 1 (
  echo [OK] Frontend is ready.
  exit /b 0
)
set /a tries+=1
if !tries! GEQ 24 (
  echo [WARN] Frontend did not report healthy within 60 seconds.
  exit /b 0
)
timeout /t 2 >nul
goto :wait_frontend_loop

:backend_healthy
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:8080/api/allocations/stats' -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
exit /b %errorlevel%

:frontend_healthy
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { $r = Invoke-WebRequest -Uri 'http://localhost:3000' -UseBasicParsing -TimeoutSec 2; if ($r.StatusCode -ge 200 -and $r.StatusCode -lt 500) { exit 0 } else { exit 1 } } catch { exit 1 }" >nul 2>&1
exit /b %errorlevel%

:free_port_if_needed
set "TARGET_PORT=%~1"
set "TARGET_PID="
for /f "tokens=5" %%P in ('netstat -ano ^| findstr /r /c:":%TARGET_PORT% .*LISTENING"') do (
  set "TARGET_PID=%%P"
  goto :got_pid
)
exit /b 0

:got_pid
if not defined TARGET_PID exit /b 0
echo [INFO] Port %TARGET_PORT% is busy with PID !TARGET_PID!. Stopping it...
taskkill /PID !TARGET_PID! /T /F >nul 2>&1
timeout /t 2 >nul
exit /b 0

:clear_h2_lock
if exist "%H2_LOCK_FILE%" (
  echo [INFO] Removing stale H2 lock file...
  del /f /q "%H2_LOCK_FILE%" >nul 2>&1
)
exit /b 0

:finish
echo.
if /i "%ARA_NO_PAUSE%"=="1" exit /b 0
pause
