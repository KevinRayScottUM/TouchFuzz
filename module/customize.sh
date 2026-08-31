#!/system/bin/sh

ui_print "*******************************"
ui_print " TouchFuzz 1.0.1"
ui_print "*******************************"

DEST="$MODPATH/system/bin/fuzzctl"
BUNDLED="$MODPATH/common/fuzzctl"
EXISTING="/data/adb/modules/touchfuzz/system/bin/fuzzctl"
mkdir -p "$MODPATH/system/bin"

existing_rc=0
if [ -x "$EXISTING" ]; then "$EXISTING" >/dev/null 2>&1; existing_rc=$?; fi
if [ "$existing_rc" -eq 2 ]; then
  cp -f "$EXISTING" "$DEST" || abort "Unable to retain existing fuzzctl."
  chmod 0755 "$DEST"
  ui_print "Already exists"
else
  if [ ! -f "$BUNDLED" ]; then
    abort "Bundled arm64 helper is missing."
  fi
  cp -f "$BUNDLED" "$DEST" || abort "Unable to install fuzzctl."
  chmod 0755 "$DEST" || abort "Unable to make fuzzctl executable."
  ui_print "Bundled arm64 fuzzctl installed successfully."
fi

rm -rf "$MODPATH/common"
chmod 0755 "$MODPATH/service.sh" "$MODPATH/system/bin/touchfuzz-config"
chmod 0644 "$MODPATH/config.conf" "$MODPATH/module.prop"
ui_print "Default profile: fst2, X/Y fuzz 9"
ui_print "Install the TouchFuzz APK to tune and save values."
ui_print "No Termux, compiler, or LSPosed is required."
