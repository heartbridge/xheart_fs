@echo off

if '%1' == '--help' (
  echo xheart file server : 1.0.0
  echo --port : file server listen port, default 8585
  echo --baseDir : file store base directory, default /files/
  echo --threshold : image file compress threshold, which unit is byte, default 1048576 bytes^(1M^)
  echo --allow : the ip expression which allow to access file server
  echo --deny : the ip expression which not allow to access file server
  echo --help : print current help info
  echo --version : print file server version
  exit /B
)else if '%1' == '--version' (
  echo xheart file server : 1.0.0
  exit /B
)

setlocal enabledelayedexpansion

for %%i in (*.jar) do set jars=!jars!%%~fi;

cd ../lib
for %%i in (*.jar) do set jars=!jars!%%~fi;

set classpath=.;!jars!%classpath%
echo %classpath%
start java -cp %classpath% com.heartbridge.server.FileServer %*
endlocal