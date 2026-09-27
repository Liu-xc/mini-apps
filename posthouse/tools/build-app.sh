#!/bin/zsh
# 驿站 posthouse — 组装 macOS .app bundle（无需 Xcode 工程，SwiftPM + 手工 bundle）
# 用法: tools/build-app.sh [--release]
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT=$(pwd)
CONFIG="debug"
[[ "${1:-}" == "--release" ]] && CONFIG="release"

echo "▸ swift build -c $CONFIG"
swift build -c "$CONFIG"

BIN=".build/$CONFIG/Posthouse"
APP="build/Posthouse.app"
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS"

cat > "$APP/Contents/Info.plist" <<'PLIST'
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleName</key>              <string>Posthouse</string>
    <key>CFBundleDisplayName</key>       <string>驿站</string>
    <key>CFBundleIdentifier</key>        <string>com.liuxc.posthouse</string>
    <key>CFBundleVersion</key>           <string>0.1.0</string>
    <key>CFBundleShortVersionString</key><string>0.1.0</string>
    <key>CFBundleExecutable</key>        <string>Posthouse</string>
    <key>CFBundlePackageType</key>       <string>APPL</string>
    <key>LSMinimumSystemVersion</key>    <string>14.0</string>
    <key>LSUIElement</key>               <true/>
    <key>NSPrincipalClass</key>          <string>NSApplication</string>
    <key>NSHumanReadableCopyright</key>  <string>© 2026 Liu-xc</string>
</dict>
</plist>
PLIST

echo -n "APPL????" > "$APP/Contents/PkgInfo"
cp "$BIN" "$APP/Contents/MacOS/Posthouse"
codesign --force -s - "$APP" 2>/dev/null || true

echo "✅ $APP"
