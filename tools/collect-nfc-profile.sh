#!/usr/bin/env bash
set -euo pipefail
adb_path="${ADB:-adb}"
root=false
if [[ "${1:-}" == "--root" ]]; then root=true; shift; fi
if [[ $# -gt 0 ]]; then echo "Usage: ADB=/path/to/adb ANDROID_SERIAL=serial bash $0 [--root]" >&2; exit 2; fi
"$adb_path" get-state >/dev/null
echo "Ink Studio NFC capability profile (read-only)"
echo "No UID, tag trace, wallet AID, serial, private NCI command or system modification is collected."
for property in ro.product.manufacturer ro.product.model ro.product.device ro.build.version.release \
  ro.build.version.sdk ro.product.cpu.abilist ro.vendor.se.chip.model ro.vendor.nfc.st_rf_v2; do
  printf '%s=' "$property"
  "$adb_path" shell getprop "$property" | tr -d '\r'
done
echo "--- NFC service state (filtered) ---"
"$adb_path" shell 'dumpsys nfc 2>/dev/null | grep -E "^mState=|^mAlwaysOnState=|^mScreenState=|^Native Proprietary Caps="' || true
echo "--- Public service names ---"
"$adb_path" shell 'service list 2>/dev/null | grep -i nfc' || true
echo "--- NFC service package paths ---"
"$adb_path" shell 'pm path com.android.nfc 2>/dev/null; pm path com.google.android.nfc 2>/dev/null' || true
if "$root"; then
  echo "--- Explicitly requested root read-only config summary ---"
  "$adb_path" shell su -c 'for path in /odm/etc/libnfc-hal-st.conf /vendor/etc/libnfc-hal-st.conf /vendor/etc/libnfc-nxp.conf /odm/etc/libnfc-nci.conf; do
    if [ -r "$path" ]; then
      echo "FILE=$path"
      sha256sum "$path"
      grep -E "^NCI_HAL_MODULE|^STNFC_FW_|^NXP_NFC_DEV_NODE|^NXP_NFC_CHIP" "$path" || true
    fi
  done' || { echo "Root config read unavailable; no changes attempted." >&2; exit 1; }
else
  echo "Root not requested. Run with --root only if you want to authorize config reads."
fi
