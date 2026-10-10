@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle is not installed on PATH.
  echo Open this folder in Android Studio, install SDK Platform 35, and use the IDE's Gradle tooling.
  echo Alternatively install Gradle 8.9, rerun this script, and let it create the wrapper.
  pause
  exit /b 1
)
gradle wrapper --gradle-version 8.9
if errorlevel 1 exit /b 1
call gradlew.bat :app:assembleDebug
if errorlevel 1 exit /b 1
echo APK: app\build\outputs\apk\debug\app-debug.apk
endlocal
