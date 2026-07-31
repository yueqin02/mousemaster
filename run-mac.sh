#!/bin/sh
# Runs the macOS version of mousemaster.
# Usage: ./run-mac.sh --configuration-file=path/to/config.properties [options]
# Requires JAVA_HOME pointing at a JDK 21+, and the Accessibility permission
# for the app you run this from (System Settings > Privacy & Security >
# Accessibility).
cd "$(dirname "$0")" || exit 1
if [ -z "$JAVA_HOME" ]; then
    echo "Set JAVA_HOME to a JDK 21+ installation" >&2
    exit 1
fi
./mvnw -q compile dependency:build-classpath -Dmdep.outputFile=target/classpath.txt || exit 1
exec "$JAVA_HOME/bin/java" -cp "target/classes:$(cat target/classpath.txt)" \
    mousemaster.platform.mac.MacMain "$@"
