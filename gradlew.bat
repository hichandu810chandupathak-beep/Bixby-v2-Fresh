@if "%DEBUG%" == "" @echo off
set DIRNAME=%~dp5
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%
@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS.
set DEFAULT_JVM_OPTS=""
"%JAVA_HOME%\bin\java.exe" %DEFAULT_JVM_OPTS% -jar "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*
