#!/usr/bin/env bash
# Runs the Compose UI suite on a running emulator shaped like a Pixel 10 Pro
# (1280 × 2856 px, ~412 dp wide), then pulls the screenshots the tests saved.
set -uo pipefail

SDK="${1:-36}"
OUT="${2:-device-shots}"

adb shell wm size 1280x2856
adb shell wm density 497
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 1

mkdir -p "$OUT"
./gradlew --no-daemon connectedDebugAndroidTest -Psift.sdk="$SDK" \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true 2>&1 | tee "$OUT/gradle.log"
status=${PIPESTATUS[0]}

adb pull /sdcard/Android/data/com.sift.photos/files/shots "$OUT" || true
adb logcat -d > "$OUT/logcat.txt" || true
exit $status
