#!/bin/sh
DIRNAME=`dirname "$0"`
APP_HOME=`basename "$DIRNAME"`
APP_BASE_NAME=`basename "$0"`
JAVACMD="java"
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/bin/sh" ] ; then
        JAVACMD="$JAVA_HOME/bin/sh"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
fi
exec "$JAVACMD" -jar "$DIRNAME/gradle/wrapper/gradle-wrapper.jar" "$@"
