#!/bin/sh

##############################################################################
# Gradle start up script for POSIX systems
##############################################################################

APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
DIRNAME=`dirname "$0"`
APP_HOME=`pwd -P`

# Resolve links: $0 may be a link
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`/"$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`/" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use to start the JVM.
if [ -n "$JAVA_HOME" ] ; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

# Download gradle-wrapper.jar if it doesn't exist
if [ ! -e "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Downloading gradle-wrapper.jar..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    curl -L -o "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
        "https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar" 2>/dev/null || \
    wget -O "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" \
        "https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar" 2>/dev/null || \
    echo "Warning: Could not download gradle-wrapper.jar. Using bundled version if available."
fi

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
