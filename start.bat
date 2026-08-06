@echo off
setlocal
title MC Puzzle - Paper 1.20.1

set "START_SCRIPT=%~dp0server\start.ps1"
set "EULA_FILE=%~dp0server\eula.txt"

if not exist "%START_SCRIPT%" goto missing_script

findstr /x /c:"eula=true" "%EULA_FILE%" >nul 2>&1
if not errorlevel 1 goto start_server

echo.
echo Mojang EULA agreement has not been confirmed.
echo https://aka.ms/MinecraftEULA
echo.
choice /C YN /N /M "Do you agree to the EULA and want to start the server? [Y/N] "
if errorlevel 2 goto cancelled
if errorlevel 1 goto accept_eula
goto cancelled

:accept_eula
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%START_SCRIPT%" -AcceptEula -MinMemory 2G -MaxMemory 4G
goto finished

:start_server
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%START_SCRIPT%" -MinMemory 2G -MaxMemory 4G
goto finished

:missing_script
echo Start script not found: "%START_SCRIPT%"
set "RESULT=1"
goto failed

:cancelled
echo The server was not started because the EULA was not accepted.
set "RESULT=0"
goto failed

:finished
set "RESULT=%ERRORLEVEL%"
if "%RESULT%"=="0" exit /b 0

:failed
echo.
if not "%RESULT%"=="0" echo Server startup failed. Review the messages above.
pause
exit /b %RESULT%
