#!/bin/sh
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
