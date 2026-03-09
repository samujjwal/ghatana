@echo off
REM Guardian Windows Uninstall Script

setlocal enabledelayedexpansion

echo ========================================
echo Guardian Uninstall
echo ========================================
echo.

REM Check for admin privileges
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: This uninstaller requires administrator privileges.
    pause
    exit /b 1
)

set INSTALL_DIR=%ProgramFiles%\Ghatana\Guardian
set DATA_DIR=%APPDATA%\Guardian

echo Removing scheduled task...
taskkill /tn "Guardian" /f >nul 2>&1 || true
schtasks /delete /tn "Guardian" /f >nul 2>&1 || true

echo Removing shortcuts...
del "%APPDATA%\Microsoft\Windows\Start Menu\Programs\Guardian.lnk" >nul 2>&1 || true

echo Removing installation directory...
if exist "%INSTALL_DIR%" (
    rmdir /s /q "%INSTALL_DIR%"
)

echo Removing registry entries...
reg delete "HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Guardian" /f >nul 2>&1 || true

echo.
echo ========================================
echo Uninstall Complete!
echo ========================================
echo.
echo Keep data (optional)? Data location: %DATA_DIR%
echo To manually remove: rmdir "%DATA_DIR%"
echo.
pause
