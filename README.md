# TouchFuzz

TouchFuzz is a portable tuning tool for rooted Android devices that can reduce touchscreen micro-jitter and excessive sensitivity. It was created and validated on the first-generation Google Pixel Fold (`felix`), specifically its `fst2` inner touchscreen.

The project consists of a Magisk module and a companion Android application. Users do not need Termux, Clang, ADB compilation, LSPosed, Vector, or any on-device development environment. The Magisk package already contains a precompiled arm64-v8a native helper.

> **Verified on Google Pixel Fold (felix).** TouchFuzz does not claim to fix every touchscreen problem or every Android device.

## The problem TouchFuzz targets

Some digitizers continue to report small X/Y coordinate changes while a finger is almost stationary. Android can interpret those fluctuations as movement, producing symptoms such as:

- subtle page or cursor shaking;
- unstable long presses;
- excessive sensitivity during slow movement;
- visible micro-jitter when holding a finger in one place.

Linux input devices expose a `fuzz` field for absolute axes. TouchFuzz changes the fuzz values of the multitouch X/Y position axes so that very small coordinate fluctuations can be filtered more effectively.

TouchFuzz is **not guaranteed** to repair:

- a damaged digitizer or display cable;
- true ghost touches that generate independent press/release events;
- large coordinate jumps;
- charger or grounding interference;
- touch-controller firmware defects;
- kernel, display, or hardware failures.

## Safety model

The native `fuzzctl` helper has a strict write allowlist. It can change only:

- `ABS_MT_POSITION_X` (`0x35`) fuzz;
- `ABS_MT_POSITION_Y` (`0x36`) fuzz.

When it changes fuzz, it first reads the complete `input_absinfo` structure and preserves the current value, minimum, maximum, flat, resolution, and all other fields.

TouchFuzz does **not** modify:

- `mf_mode`;
- outer-screen settings unless that device is explicitly selected in Advanced mode;
- touch firmware;
- the kernel or DTBO;
- axis minimum/maximum or resolution;
- pressure, touch major/minor, flat, or other input parameters.

The boot service applies a saved profile to one matching touchscreen identity only. It never silently changes every touchscreen on a device.

## Components

### Magisk module

The module provides:

- a precompiled AArch64 `fuzzctl` binary;
- `config.conf` for persistent values and device identity;
- `service.sh` for applying the saved profile after input devices appear at boot;
- dynamic `/dev/input/event*` discovery, avoiding hardcoded event numbers;
- an English boot log at `/data/adb/modules/touchfuzz/touchfuzz.log`;
- an original-value backup used by **Restore Stock**.

The Pixel Fold default profile selects the input device named `fst2` and starts with a conservative X/Y fuzz value of 9. On first application, the original X/Y fuzz values are captured before the saved values are applied.

### Android application

The TouchFuzz app provides a single-screen interface with:

- root and module status;
- compatible multitouch-device detection;
- device name, current event path, and dynamic X/Y ranges;
- Original, Current, and Boot values;
- a 0–40 slider and numeric entry;
- linked X/Y values by default;
- separate X/Y control in advanced use;
- presets for Stock, 4, 6, 8, 9, 15, 20, 25, and 30;
- **Apply Live**, **Save for Boot**, **Restore Stock**, and **Refresh** actions;
- a top-right **Update** button that applies the selected values, saves them for boot, and refreshes Current/Boot.

The safest default is `fst2`. Other detected devices, including an outer touchscreen, are shown only after the user explicitly enables the advanced device selector.

## Requirements

- Rooted Android device with a current Magisk installation;
- arm64-v8a/AArch64 processor;
- Android 8.0 or newer for the companion app;
- an input device exposing both `ABS_MT_POSITION_X` and `ABS_MT_POSITION_Y`;
- Google Pixel Fold (`felix`) for the currently validated profile.

## Installation

1. Download `TouchFuzz-Magisk-v1.0.1.zip` and `TouchFuzz-v1.0.1.apk` from the GitHub Release.
2. Open Magisk, choose **Modules → Install from storage**, and select the Magisk ZIP.
3. Reboot the device.
4. Install the APK normally or sideload it.
5. Open TouchFuzz and grant root access when requested.
6. Tap **Refresh** and confirm that `fst2 (Pixel Fold inner display)` is selected.
7. Choose a preset or enter a value, then tap **Apply Live**, **Save for Boot**, or the top-right **Update** button.

Do not flash the Source ZIP. It is provided only for source review and development.

## Controls and value behavior

| Control | Behavior |
| --- | --- |
| Refresh | Rechecks root, module status, devices, current values, and saved boot values. |
| Apply Live | Applies X/Y immediately. The change lasts until reboot unless also saved. |
| Save for Boot | Saves the selected device identity and X/Y values, then applies them immediately. |
| Update | Applies the selected preset/manual values, saves them for boot, and refreshes the displayed values. |
| Restore Stock | Restores the original captured X/Y values. It refuses to guess if the backup is unavailable. |

Higher fuzz is not automatically better. Excessively high values can make fine movement feel less responsive. Start with 8 or 9 and increase gradually only if necessary.

## Persistent files

After installation, the important module files are:

```text
/data/adb/modules/touchfuzz/config.conf
/data/adb/modules/touchfuzz/touchfuzz.log
/data/adb/modules/touchfuzz/system/bin/fuzzctl
```

Example configuration:

```sh
DEVICE_NAME='fst2'
DEVICE_PATH='/dev/input/event3'
FUZZ_X='9'
FUZZ_Y='9'
ORIGINAL_X='0'
ORIGINAL_Y='0'
```

The saved event path is informational. At boot, TouchFuzz rediscovers the matching device name because Android event numbers can change.

## Windows CMD verification

Locate the event currently assigned to `fst2`:

```bat
adb shell "su -c 'for e in /dev/input/event*; do n=$(cat /sys/class/input/${e##*/}/device/name 2>/dev/null); [ "$n" = "fst2" ] && echo $e; done'"
```

If the result is `/dev/input/event3`, inspect its reported position axes:

```bat
adb shell "su -c 'getevent -pl /dev/input/event3'" | findstr /I "POSITION_X POSITION_Y"
```

Read X and Y directly through the bundled helper:

```bat
adb shell "su -c '/data/adb/modules/touchfuzz/system/bin/fuzzctl /dev/input/event3 0x35'"
adb shell "su -c '/data/adb/modules/touchfuzz/system/bin/fuzzctl /dev/input/event3 0x36'"
```

Read the saved profile and boot log:

```bat
adb shell "su -c 'cat /data/adb/modules/touchfuzz/config.conf'"
adb shell "su -c 'cat /data/adb/modules/touchfuzz/touchfuzz.log'"
```

## Uninstallation

Remove TouchFuzz from Magisk and reboot. Runtime input parameters are recreated by the driver during reboot, so removing the module does not permanently alter touch firmware, the kernel, or the DTBO. Uninstall the APK separately if it is no longer needed.

## Building from source

The repository contains:

```text
app/                  Android app and native fuzzctl source
module/               Magisk module scripts and configuration
build.gradle          Android build configuration
settings.gradle       Gradle project configuration
```

Build requirements:

- Android Studio or Gradle 9.5;
- Android SDK 37;
- Android NDK 28.2.13676358;
- CMake 3.22.1;
- Java 17 or newer.

The repository intentionally excludes the private release-signing keystore. Contributors should sign local APKs with their own development or release key.

## Project status

- App: `1.0.1`
- Magisk module: `1.0.1`
- Verified device: Google Pixel Fold (`felix`), inner touchscreen `fst2`
- Application ID: `dev.touchfuzz.app`

Bug reports should include the device model, Android build, Magisk version, selected input-device name, `config.conf`, and `touchfuzz.log`. Review logs before sharing them publicly.
