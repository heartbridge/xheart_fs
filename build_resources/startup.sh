#!/bin/sh

if [ "$1" = '--help' ]; then
  echo xheart file server : 1.0.0
  echo --port      : file server listen port, default 8585
  echo --baseDir   : file store base directory, default /files/
  echo --threshold : image file compress threshold, which unit is byte, default 1048576 bytes\(1M\)
  echo --allow     : the ip expression which allow to access file server
  echo --deny      : the ip expression which not allow to access file server
  echo --help      : print current help info
  echo --version   : print file server version
  exit
elif [ "$1" = '--version' ]; then
  echo "xheart file server : 1.0.0"
  exit

fi

classpath=${classpath}

#include the jars under lib directory
for jar in `ls ../lib/*.jar`
do
  jars=$jars$jar:
done

#include current jars
for jar in `ls *.jar`
do
  jars=$jars$jar:
done

classpath=.:$jars$classpath

exec java -cp $classpath com.heartbridge.server.FileServer $*
