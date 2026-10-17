#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p build/tests
ADB=/bin/echo bash tools/collect-nfc-profile.sh > build/tests/profile-default.txt
if grep -q 'shell su -c' build/tests/profile-default.txt; then echo "Default collector requested root"; exit 1; fi
if grep -Eq 'setprop|settings put|pm grant|sendVendorNciMessage|svc nfc' build/tests/profile-default.txt; then echo "Collector contains a write action"; exit 1; fi
ADB=/bin/echo bash tools/collect-nfc-profile.sh --root > build/tests/profile-explicit-root.txt
grep -q 'shell su -c' build/tests/profile-explicit-root.txt
if ADB=/bin/echo bash tools/collect-nfc-profile.sh --unknown >/dev/null 2>&1; then echo "Invalid argument was accepted"; exit 1; fi
echo "Profile collector command-contract tests passed (mock only, no device)"
