#!/system/bin/sh

MODDIR=${0%/*}
LOG="$MODDIR/touchfuzz.log"
HELPER="$MODDIR/system/bin/fuzzctl"
CONFIG="$MODDIR/config.conf"

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$LOG"; }
axis_fuzz() { "$HELPER" "$1" "$2" 2>/dev/null | sed -n 's/.*fuzz=\([-0-9]*\).*/\1/p'; }

: > "$LOG"
log "TouchFuzz boot service started."

count=0
while [ "$count" -lt 60 ] && [ ! -d /dev/input ]; do
  sleep 1
  count=$((count + 1))
done

if [ ! -x "$HELPER" ]; then log "Error: fuzzctl is missing or not executable."; exit 1; fi
if [ ! -f "$CONFIG" ]; then log "Error: config.conf is missing."; exit 1; fi
. "$CONFIG"

if [ -z "$DEVICE_NAME" ]; then log "No saved device identity; nothing was changed."; exit 0; fi

TARGET=""
count=0
while [ "$count" -lt 60 ] && [ -z "$TARGET" ]; do
  for event in /dev/input/event*; do
    [ -e "$event" ] || continue
    base=${event##*/}
    name=$(cat "/sys/class/input/$base/device/name" 2>/dev/null)
    [ "$name" = "$DEVICE_NAME" ] || continue
    "$HELPER" "$event" 0x35 >/dev/null 2>&1 || continue
    "$HELPER" "$event" 0x36 >/dev/null 2>&1 || continue
    TARGET="$event"
    break
  done
  [ -n "$TARGET" ] || sleep 1
  count=$((count + 1))
done

if [ -z "$TARGET" ]; then log "Error: saved direct multitouch device '$DEVICE_NAME' was not found."; exit 1; fi
BEFORE_X=$(axis_fuzz "$TARGET" 0x35); BEFORE_Y=$(axis_fuzz "$TARGET" 0x36)
log "Detected device: path=$TARGET name=$DEVICE_NAME."
log "Requested fuzz: X=$FUZZ_X Y=$FUZZ_Y. Before: X=$BEFORE_X Y=$BEFORE_Y."

if [ -z "$ORIGINAL_X" ] || [ -z "$ORIGINAL_Y" ]; then
  if "$MODDIR/system/bin/touchfuzz-config" save "$TARGET" "$DEVICE_NAME" "$FUZZ_X" "$FUZZ_Y" >/dev/null 2>&1; then
    . "$CONFIG"
    log "Captured original fuzz profile: X=$ORIGINAL_X Y=$ORIGINAL_Y."
  else
    log "Error: unable to capture the original fuzz profile; no values were changed."
    exit 1
  fi
fi

if "$HELPER" "$TARGET" 0x35 "$FUZZ_X" >/dev/null 2>&1 && "$HELPER" "$TARGET" 0x36 "$FUZZ_Y" >/dev/null 2>&1; then
  AFTER_X=$(axis_fuzz "$TARGET" 0x35); AFTER_Y=$(axis_fuzz "$TARGET" 0x36)
  log "Applied successfully. After: X=$AFTER_X Y=$AFTER_Y."
else
  log "Error: failed to apply the saved fuzz values."
  exit 1
fi
