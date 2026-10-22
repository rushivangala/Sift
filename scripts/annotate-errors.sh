#!/usr/bin/env bash
# Turns the interesting lines of a Gradle log into one GitHub error annotation, so failures can be
# read from the check-run API without downloading logs.
LOG="${1:-build.log}"
[ -f "$LOG" ] || { echo "::error title=No log::$LOG not found"; exit 0; }
{
  grep -E "^e: |error:|ERROR|FAILED|What went wrong|Could not|> |Exception|AssertionError|expected|Caused by" "$LOG" | grep -v "^> Task" | head -80
  echo "----- tail -----"
  tail -40 "$LOG"
} > /tmp/annot.txt
msg=$(sed -e 's/%/%25/g' -e 's/\r//g' /tmp/annot.txt | awk 'BEGIN{ORS="%0A"}{print}')
echo "::error title=Build log excerpt::${msg:0:60000}"

# App crashes from the device log, if any.
LOGCAT="$(dirname "$LOG")/logcat.txt"
if [ -f "$LOGCAT" ] && grep -q "FATAL EXCEPTION\|ANR in com.sift" "$LOGCAT"; then
  crash=$(grep -A40 "FATAL EXCEPTION\|ANR in com.sift" "$LOGCAT" | head -120 | sed -e 's/%/%25/g' -e 's/\r//g' | awk 'BEGIN{ORS="%0A"}{print}')
  echo "::error title=App crash in device log::${crash:0:60000}"
fi
