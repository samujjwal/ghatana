@echo off
REM Guardian Windows Installation Script
REM Installs Guardian and dependencies

setlocal enabledelayedexpansion

echo ========================================
echo Guardian Installation for Windows
echo ========================================
echo.

REM Check for admin privileges
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Error: This installer requires administrator privileges.
    echo Please run as Administrator.
    pause
    exit /b 1
)

REM Define installation directory
set INSTALL_DIR=%ProgramFiles%\Ghatana\Guardian
set DATA_DIR=%APPDATA%\Guardian

echo Creating installation directories...
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"
if not exist "%DATA_DIR%" mkdir "%DATA_DIR%"

echo Copying application files...
REM Copy parent-dashboard
xcopy /E /I /Y "..\apps\parent-dashboard\dist\*" "%INSTALL_DIR%\dashboard\" >nul 2>&1
if %errorlevel% neq 0 (
    echo Warning: Failed to copy dashboard files
)

REM Copy browser extension
xcopy /E /I /Y "..\apps\browser-extension\dist\chrome\*" "%INSTALL_DIR%\browser-extension\" >nul 2>&1
if %errorlevel% neq 0 (
    echo Warning: Failed to copy browser extension files
)

REM Create shortcut to dashboard
echo Creating shortcuts...
powershell -Command "$WshShell = New-Object -ComObject WScript.Shell; $Shortcut = $WshShell.CreateShortcut('%APPDATA%\Microsoft\Windows\Start Menu\Programs\Guardian.lnk'); $Shortcut.TargetPath = 'explorer.exe'; $Shortcut.Arguments = '%INSTALL_DIR%\dashboard\index.html'; $Shortcut.Save()"

REM Create Windows scheduled task to start Guardian on login
echo Creating auto-start task...
powershell -Command "Register-ScheduledTask -TaskName 'Guardian' -Trigger (New-ScheduledTaskTrigger -AtLogon) -Action (New-ScheduledTaskAction -Execute 'explorer.exe' -Argument '%INSTALL_DIR%\dashboard\index.html') -RunLevel Highest -Force" >nul 2>&1

REM Create uninstall entry in Add/Remove Programs
echo Creating uninstall entry...
reg add "HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Guardian" ^
    /v "DisplayName" /d "Guardian" /f >nul 2>&1
reg add "HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Guardian" ^
    /v "DisplayVersion" /d "1.0.0" /f >nul 2>&1
reg add "HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Guardian" ^
    /v "UninstallString" /d "%INSTALL_DIR%\uninstall.bat" /f >nul 2>&1

echo.
echo ========================================
echo Installation Complete!
echo ========================================
echo.
echo Guardian has been installed to: %INSTALL_DIR%
echo Data directory: %DATA_DIR%
echo.
echo Next steps:
echo   1. Look for "Guardian" in your Start Menu
echo   2. Guardian will automatically start on next login
echo   3. Open the dashboard to configure settings
echo.
pause
