@echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%

set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

if not exist "%CLASSPATH%" (
  echo ERROR: Gradle Wrapper JAR is missing: %CLASSPATH%
  echo Use the repository's GitHub Actions workflow, which provisions Gradle 8.9 directly.
  exit /b 1
)

java -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
if %ERRORLEVEL% NEQ 0 exit /b %ERRORLEVEL%
endlocal
