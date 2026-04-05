@echo off
cd /d "%~dp0"
echo Building Vital Popups with local Gradle...
echo.
gradle build
set EXITCODE=%ERRORLEVEL%
echo.
if %EXITCODE%==0 (
  echo BUILD SUCCEEDED.
  echo Open this folder for the jar:
  echo %~dp0build\libs
  start "" "%~dp0build\libs"
) else (
  echo BUILD FAILED with exit code %EXITCODE%.
  echo Check the output above.
)
echo.
pause
