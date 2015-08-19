@echo off
setlocal enabledelayedexpansion

for %%i in (*.jar) do set jars=!jars!%%~fi;

cd ../lib
for %%i in (*.jar) do set jars=!jars!%%~fi;

set classpath=.;!jars!%classpath%
echo %classpath%
start java -cp %classpath% com.heartbridge.server.FileServer
endlocal