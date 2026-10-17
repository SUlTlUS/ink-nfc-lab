#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
java_path="${JAVA_HOME:?Set JAVA_HOME to your JDK directory}"
[[ "$(shasum -a 256 testdata/astronaut.png | awk '{print $1}')" == "88431cd9653ccd539741b555fb0a46b61558b301d4110412b5bc28b5e3ea6cb5" ]] || { echo "Astronaut fixture hash mismatch"; exit 1; }
[[ "$(shasum -a 256 testdata/calibration.png | awk '{print $1}')" == "724d2efe4f64c4b576c4d255c1e32b92efe81d1119aeb935c4d349ad34ef4493" ]] || { echo "Calibration fixture hash mismatch"; exit 1; }
bash test.sh
"$java_path/bin/java" -Djava.awt.headless=true -cp build/tests tools/AlgorithmLab.java \
  build/algorithm-lab testdata/calibration.png testdata/astronaut.png
echo "Open build/algorithm-lab/index.html; metrics are offline proxies, not a display calibration."
