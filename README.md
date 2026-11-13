<p align="center">
  <img src="https://helltar.com/projects/fosshine/logo.svg" width="128" alt="Fosshine">
</p>

<h1 align="center">Fosshine</h1>

An Android app for the **Fossibot F1800** portable power station and an alternative to the vendor's **Fossibot+**
app: it shows what the station is doing and changes its settings over the station's own Wi-Fi, with no
cloud, no account and no internet.

The name is FOSS, as in free and open-source software, plus "shine", and is said "fo-shine".

<img src="https://helltar.com/projects/fosshine/screenshots/screenshot_overview.png" width="26%" align="left" alt="Battery, input and output power"><img src="https://helltar.com/projects/fosshine/screenshots/screenshot_controls.png" width="26%" align="left" alt="Outputs, light and charging settings"><img src="https://helltar.com/projects/fosshine/screenshots/screenshot_settings.png" width="26%" align="left" alt="Timers and device settings"><br clear="all">

## What it does

- Shows the charge, the time left, the battery temperature, and the power coming in (AC, solar) and going
  out (AC, DC, USB).
- Switches the AC, DC and USB outputs and the light (on, SOS, strobe).
- Sets the charge power, the charge ceiling and discharge floor, the DC charging current, and UPS or ECO
  mode.
- Sets the timers: screen off, station shutdown, and each output's idle-off.
- Sets the screen brightness, the key sound and the output memory.
- Stays connected while the app is closed, with the charge and power in a notification and an alert when
  the battery falls to 20 % while not charging.

The interface is in English and Ukrainian.

## Which stations

Tested on the Fossibot F1800. The F3000 uses the same vendor app, Wi-Fi and protocol, so it most likely
works too, with its own charge power range of up to 2000 W, but nobody has tried it yet: if you have one,
a report is welcome. The F2400 is a different platform and does not work.

## Connecting

Fosshine needs Android 10 or later. It talks to the station the way the Fossibot+ app's offline mode does:
the phone joins the station's own Wi-Fi network, `FOSS_<serial>`, and the station connects to the phone.

1. Turn the station on.
2. Tap **Connect**. If Android shows a list of networks, pick `FOSS_…`.
3. Wait. After power-on the station needs a minute or two before it accepts a phone; the app shows the
   wait and connects by itself once the Bluetooth icon blinks on the station's display.

On phones that support two Wi-Fi connections at once, the regular Wi-Fi stays up beside the station's.

The station lets in one phone at a time. If it does not connect, leave the Fossibot+ app's offline mode
and disconnect other phones from the station.

### Permissions

All three are optional; the app connects without them.

- **Location** — Android reveals the name of a Wi-Fi network only to an app with this permission, and the
  station's serial number is in that name. Fosshine reads no location. Without it the app cannot tell an
  F3000 from an F1800 and uses the F1800's ranges.
- **Nearby devices** — lets the app hear, over Bluetooth, that the station is still starting up. It only
  listens and never connects over Bluetooth.
- **Notifications** — the status notification and the low-battery alert.

## What it writes to the station

Only what the Fossibot+ app's own settings screen writes, within that screen's ranges, and nothing while
the battery reads 0 %, as that app does. Every write goes through one check (`protocol/Writable.kt`), and
every control shows the value the station reports back, never the one that was asked for.

This matters because the station's firmware may accept a value it cannot handle. Fosshine is not made or
endorsed by the vendor and comes with no warranty: you use it at your own risk.

## Building

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`AGENTS.md` describes the code, the protocol and the rules a change has to keep.

### Protocol tools

`tools/` holds the Python scripts the protocol was worked out with, for a PC joined to the station's Wi-Fi
as `192.168.8.10`: `listen.py` plays the phone's part and logs every frame, `state.py` decodes the latest
one, `send.py` writes a register through a running `listen.py`, and `relay.py` logs a TCP session passing
through it.

`send.py` writes any register with any value and checks nothing. A value the vendor's app never writes
may damage the station.

## Trademark

Fossibot is a trademark of its owner. Fosshine is an independent project, not affiliated with or endorsed
by it; the name is used only to say which stations the app works with.
