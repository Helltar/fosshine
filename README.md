<p align="center">
  <img src="https://helltar.com/projects/fosshine/logo.svg" width="128" alt="Fosshine">
</p>

<h1 align="center">Fosshine</h1>

<p align="center">
  Control a <b>Fossibot F1800</b> power station from your phone, over its own Wi-Fi.<br>
  No cloud, no account, no internet.
</p>

<p align="center">
  <a href="https://github.com/Helltar/fosshine/releases/latest"><b>Download the APK</b></a>
</p>

<img src="https://helltar.com/projects/fosshine/screenshots/screenshot_overview.png" width="26%" align="left" alt="Battery, input and output power"><img src="https://helltar.com/projects/fosshine/screenshots/screenshot_controls.png" width="26%" align="left" alt="Outputs, light and charging settings"><img src="https://helltar.com/projects/fosshine/screenshots/screenshot_settings.png" width="26%" align="left" alt="Timers and device settings"><br clear="all">

An open-source alternative to the vendor's **Fossibot+** app. The name is FOSS plus "shine", said "fo-shine".

## Features

- Charge, time left, battery temperature, input and output power
- AC, DC and USB outputs, and the light
- Charge power and limits, DC charging current, UPS or ECO mode
- Timers, screen brightness, key sound, output memory
- A status notification and a low-battery alert while the app is closed
- English and Ukrainian

## Connecting

1. Turn the station on.
2. Tap **Connect**, and pick `FOSS_…` if Android shows a list of networks.
3. Wait: after power-on the station takes a minute or two, then the app connects by itself.

The station lets in one phone at a time, so leave the offline mode of Fossibot+ first. Android 10 or later.

The permissions are optional. Location is how Android lets an app read a Wi-Fi name, and the station's
serial number is in it; nearby devices tells the app that the station is still starting up.

## Stations

| Model | |
|---|---|
| F1800 | tested |
| F3000 | same protocol, untested: a report is welcome |
| F2400 | a different platform, not supported |

## Safety

Fosshine writes only what the settings screen of Fossibot+ writes, within its ranges. It is not made or
endorsed by the vendor and comes with no warranty.

## Building

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`AGENTS.md` covers the code and the protocol. `tools/` holds the Python scripts the protocol was worked out
with; `send.py` there writes any register unchecked and can damage a station.

---

Fossibot is a trademark of its owner. Fosshine is an independent project, not affiliated with it.
