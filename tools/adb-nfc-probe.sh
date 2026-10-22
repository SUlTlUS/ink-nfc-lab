#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mode="${1:-inspect}"
if [[ $# -gt 1 || ( "$mode" != inspect && "$mode" != read && "$mode" != roundtrip && "$mode" != probe && "$mode" != restore ) ]]; then
  echo "Usage: bash tools/adb-nfc-probe.sh [inspect|read|roundtrip|probe|restore]" >&2
  exit 2
fi
sdk_path="${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}"
java_path="${JAVA_HOME:?Set JAVA_HOME}"
adb_path="${ADB:-adb}"
tools_path="$sdk_path/build-tools/36.0.0"
android_jar="$sdk_path/platforms/android-36/android.jar"
export PATH="$java_path/bin:$PATH"
mkdir -p build/shell-probe/classes build/shell-probe/dex
javac --release 8 -encoding UTF-8 -cp "$android_jar" -d build/shell-probe/classes src/dev/inknfclab/ShellNfcProbe.java src/dev/inknfclab/Protocol.java src/dev/inknfclab/PollingConfigSession.java src/dev/inknfclab/ShellTagReader.java
jar cf build/shell-probe/classes.jar -C build/shell-probe/classes .
"$tools_path/d8" --min-api 26 --lib "$android_jar" --output build/shell-probe/dex build/shell-probe/classes.jar
(cd build/shell-probe/dex && zip -q ../shell-probe.jar classes.dex)
"$adb_path" push build/shell-probe/shell-probe.jar /data/local/tmp/ink-shell-probe.jar >/dev/null
if [[ "$mode" != inspect ]]; then
  "$adb_path" shell "CLASSPATH=/data/local/tmp/ink-shell-probe.jar app_process /system/bin dev.inknfclab.ShellNfcProbe $mode"
else
  "$adb_path" shell 'CLASSPATH=/data/local/tmp/ink-shell-probe.jar app_process /system/bin dev.inknfclab.ShellNfcProbe'
fi
