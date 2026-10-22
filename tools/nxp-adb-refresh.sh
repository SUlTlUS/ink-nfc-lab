#!/usr/bin/env bash
set -euo pipefail

adb_bin="${ADB:-adb}"
package="dev.inknfclab"
image="/sdcard/Android/data/$package/files/nxp-image.bin"
stop="/sdcard/Android/data/$package/files/nxp-stop"

"$adb_bin" get-state >/dev/null
apk="$("$adb_bin" shell pm path "$package" | sed -n 's/^package://p' | tr -d '\r' | head -n 1)"
if [[ -z "$apk" ]]; then
  echo "未找到 Ink NFC Lab，请先安装 App。" >&2
  exit 1
fi
"$adb_bin" shell test -s "$image" || {
  echo "未找到待刷图像；请在 App 选择图片，再选择 NXP / ADB 准备。" >&2
  exit 1
}
"$adb_bin" shell rm -f "$stop"
echo "使用 ADB Shell 启动 NXP Q-Poll；请保持手机解锁和驱动板贴合。"
"$adb_bin" shell "CLASSPATH='$apk' /system/bin/app_process /system/bin dev.inknfclab.NxpQPollWorker '$image' '$stop'"
