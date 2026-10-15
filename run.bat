@echo off
setlocal enabledelayedexpansion
title "CivicLens - Explainable and Accountable Complaint Resolution Platform"

:: Ensure we run from the project root directory
cd /d "%~dp0"

echo ==============================================================================
echo   CIVICLENS - EXPLAINABLE AND ACCOUNTABLE COMPLAINT RESOLUTION PLATFORM
echo ==============================================================================
echo.
echo [1/4] Checking Environment and Java Runtime...
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Java runtime not found in PATH!
    echo Please install Java 21 LTS or set JAVA_HOME.
    echo.
    pause
    exit /b 1
)

:: Check if out directory exists and has compiled classes
if not exist "out\Main.class" (
    echo [2/4] First-time setup detected. Compiling CivicLens source files...
    if not exist "out" mkdir out
    javac -d out -cp "lib/*;lib/mysql-connector-j-26.7.0/mysql-connector-j-26.7.0/*;src;out" src/civiclens/automation/*.java src/civiclens/triage/*.java src/civiclens/similarity/*.java src/civiclens/sla/*.java src/civiclens/lifecycle/*.java src/civiclens/audit/*.java src/civiclens/analytics/*.java src/*.java
    if %ERRORLEVEL% neq 0 (
        echo [ERROR] Compilation failed. Please check Java compiler output above.
        pause
        exit /b 1
    )
    echo [OK] Compilation completed successfully.
) else (
    echo [2/4] Compiled binaries verified in out/
)

echo [3/4] Initializing Database and Localhost Automation...
:: Fast 300ms socket test to see if MySQL is active on localhost:3306
powershell -NoProfile -Command "$client = New-Object System.Net.Sockets.TcpClient; try { $iar = $client.BeginConnect('127.0.0.1', 3306, $null, $null); if ($iar.AsyncWaitHandle.WaitOne(300)) { $client.EndConnect($iar); exit 0 } else { exit 1 } } catch { exit 1 } finally { $client.Close() }" >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo [DB] MySQL service detected active on localhost:3306.
) else (
    echo [DB] Localhost MySQL not detected. Zero-configuration SQLite WAL mode active.
)

:: Check if user passed arguments (e.g., --demo, --presentation-only, --server-only)
set "APP_ARGS=%*"

if "%1"=="--server-only" (
    echo [AUTOMATION] Starting standalone Localhost Presentation Server on port 8080...
    java -cp "lib/*;lib/mysql-connector-j-26.7.0/mysql-connector-j-26.7.0/*;out" civiclens.automation.PresentationServer 8080
    goto :done
)

if "%1"=="--presentation-only" (
    echo [AUTOMATION] Launching 3D Interactive Presentation on localhost:8080...
    start "" "http://localhost:8080/civiclens-presentation.html"
    goto :done
)

echo [4/4] Starting CivicLens Platform with Automated Localhost Presentation...
echo.
echo ==============================================================================
echo   SYSTEM DASHBOARDS:
echo   - 3D Interactive Presentation : http://localhost:8080/civiclens-presentation.html
echo   - Localhost Automation API    : http://localhost:8080/api/status
echo   - Desktop Application GUI     : Java Swing FlatLaf Interface (Launching...)
echo ==============================================================================
echo.

:: Launch CivicLens Core Application (which automatically hosts presentation on localhost and opens browser)
java -cp "lib/*;lib/mysql-connector-j-26.7.0/mysql-connector-j-26.7.0/*;out" Main %APP_ARGS%

:done
echo.
echo [CivicLens] Session ended. Goodbye!
endlocal
