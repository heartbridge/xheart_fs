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
cd `dirname $0`

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

#check if should run in daemon modal
for arg in "$@"
do
echo "$arg" 
if [ "$arg"='--daemon=on' ]; then
  daemon=true
  break
fi
done
 
echo "daemon"

if [ $daemon ]; then
  java -cp $classpath com.github.heartbridge.fs.Application $*
else
  (java -cp $classpath com.github.heartbridge.fs.Application $* &)
fi
