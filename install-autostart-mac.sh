#!/bin/sh
# Installs mousemaster as a macOS login item (LaunchAgent).
#
# Copies a self-contained runtime (JDK + classes + jars + configuration) to
# ~/Applications/mousemaster so the agent keeps working after this checkout is
# deleted, then loads ~/Library/LaunchAgents/com.davin.mousemaster.plist.
#
# Usage: JAVA_HOME=/path/to/jdk21 ./install-autostart-mac.sh [config-file]
# Uninstall: launchctl bootout gui/$(id -u)/com.davin.mousemaster
#            rm -rf ~/Applications/mousemaster ~/Library/LaunchAgents/com.davin.mousemaster.plist
set -e
cd "$(dirname "$0")"

CONFIG=${1:-configuration/davin.properties}
INSTALL_DIR="$HOME/Applications/mousemaster"
PLIST="$HOME/Library/LaunchAgents/com.davin.mousemaster.plist"
LABEL=com.davin.mousemaster

if [ -z "$JAVA_HOME" ]; then
    echo "Set JAVA_HOME to the JDK 21+ to bundle" >&2
    exit 1
fi
if [ -z "$MOUSEMASTER_QT_LIB_PATH" ]; then
    echo "Set MOUSEMASTER_QT_LIB_PATH to a Qt 6.8 lib directory (the mode" >&2
    echo "indicator needs it)" >&2
    exit 1
fi
if [ ! -f "$CONFIG" ]; then
    echo "Configuration file not found: $CONFIG" >&2
    exit 1
fi

echo "Building..."
./mvnw -q compile dependency:copy-dependencies \
    -DoutputDirectory=target/lib -DincludeScope=runtime

echo "Installing to $INSTALL_DIR..."
launchctl bootout "gui/$(id -u)/$LABEL" 2>/dev/null || true
rm -rf "$INSTALL_DIR"
mkdir -p "$INSTALL_DIR/configuration"
# ditto rather than cp: it preserves the JDK's code signature, which macOS keys
# the Accessibility grant on.
ditto "$JAVA_HOME" "$INSTALL_DIR/jdk"
ditto target/classes "$INSTALL_DIR/classes"
ditto target/lib "$INSTALL_DIR/lib"
cp "$CONFIG" "$INSTALL_DIR/configuration/$(basename "$CONFIG")"

# Only the frameworks the indicator needs, not all of Qt (2.8GB vs ~95MB).
mkdir -p "$INSTALL_DIR/qt/lib" "$INSTALL_DIR/qt/plugins"
for framework in QtCore QtGui QtWidgets QtDBus; do
    ditto "$MOUSEMASTER_QT_LIB_PATH/$framework.framework" \
        "$INSTALL_DIR/qt/lib/$framework.framework"
done
ditto "$MOUSEMASTER_QT_LIB_PATH/../plugins/platforms" \
    "$INSTALL_DIR/qt/plugins/platforms"

mkdir -p "$HOME/Library/LaunchAgents"
cat > "$PLIST" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>Label</key>
    <string>$LABEL</string>
    <key>ProgramArguments</key>
    <array>
        <string>$INSTALL_DIR/jdk/bin/java</string>
        <!-- Qt, like all Cocoa UI, must run on the process's first thread. -->
        <string>-XstartOnFirstThread</string>
        <string>-cp</string>
        <string>$INSTALL_DIR/classes:$INSTALL_DIR/lib/*</string>
        <string>mousemaster.platform.mac.MacMain</string>
        <string>--configuration-file=$INSTALL_DIR/configuration/$(basename "$CONFIG")</string>
        <!-- Not debug: it logs every keystroke to StandardOutPath. -->
        <string>--log-level=info</string>
    </array>
    <key>WorkingDirectory</key>
    <string>$INSTALL_DIR</string>
    <key>EnvironmentVariables</key>
    <dict>
        <key>MOUSEMASTER_QT_LIB_PATH</key>
        <string>$INSTALL_DIR/qt/lib</string>
        <key>QT_PLUGIN_PATH</key>
        <string>$INSTALL_DIR/qt/plugins</string>
    </dict>
    <key>RunAtLoad</key>
    <true/>
    <key>ProcessType</key>
    <string>Interactive</string>
    <key>StandardOutPath</key>
    <string>$HOME/Library/Logs/mousemaster.log</string>
    <key>StandardErrorPath</key>
    <string>$HOME/Library/Logs/mousemaster.log</string>
</dict>
</plist>
EOF

# enable: bootstrap refuses to load a service that was disabled by hand.
launchctl enable "gui/$(id -u)/$LABEL"
launchctl bootstrap "gui/$(id -u)" "$PLIST"
echo "Installed. mousemaster starts at login and is running now."
echo "Grant Accessibility to $INSTALL_DIR/jdk/bin/java if prompted."
echo "Log: ~/Library/Logs/mousemaster.log"
echo "Config (hot-reloads on save): $INSTALL_DIR/configuration/$(basename "$CONFIG")"
