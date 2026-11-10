#!/bin/sh
# Gradle startup script for POSIX systems

DIR=$(cd "$(dirname "$0")" && pwd)
WRAPPER_JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"

if [ -f "$WRAPPER_JAR" ]; then
    exec java -jar "$WRAPPER_JAR" "$@"
elif command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
else
    echo "LNXV Build Helper:"
    echo "Gradle wrapper jar is not bundled. Opening this project in Android Studio (version 2024.1+) will"
    echo "automatically bootstrap Gradle 8.7 as configured in gradle/wrapper/gradle-wrapper.properties."
    echo "Alternatively, install Gradle on your host machine and run 'gradle wrapper' to generate the jar."
    exit 1
fi
