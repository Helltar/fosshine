# AGENTS.md

Root instruction file for coding agents on Fosshine, an Android app (Kotlin, Jetpack Compose) that controls a
portable power station locally — the Fossibot F1800, tested, and the F3000 of the same platform, untested —
with no cloud and no account. It applies to the whole repository; `CLAUDE.md` is just the one-line
`@AGENTS.md` import that points Claude Code here. Product docs go in `README.md` or `docs/`.

## Commands and verification

- `./gradlew testDebugUnitTest assembleDebug lintDebug` — unit tests, the debug APK, lint (errors fail it).
  Run it before finishing a code change unless the change is docs-only.
- `adb install -r app/build/outputs/apk/debug/app-debug.apk` puts it on a connected phone. A new
  `applicationId` is a new app: uninstall the old one first, or both will fight over port 8058.
- compileSdk is 36.1, so the Compose BOM stays on `2026.06.01`; 2026.08 and later need compileSdk 37.
- If a check cannot run, report the exact command and the blocker.

## How it talks to the station

The station runs its own Wi-Fi AP, `FOSS_<serial>`, with a password the vendor hard-codes for every unit,
and connects out as a TCP client to `192.168.8.10:8058` after handing out a DHCP lease. The phone plays the
server, as the vendor app's offline mode does. The AP takes one client at a time.

A frame is `u16 total, u16 kind, u16 body length`, then 6-byte entries (`u16 key`, 4 value bytes), all
little-endian, then a CRC-16/MODBUS of the entries sent high byte first. Telemetry (kind `0x0b`) arrives once
a second and is answered with a fixed ack; a write (kind `0x0c`) sets one register and gets no reply of its
own.

After power-on the station spends a minute or two in a setup phase, advertising over BLE as `FOSS_<serial>`,
and dials the phone only once that advertising has stopped. A phone that joins the AP during the setup phase
is dropped when it ends.

## Layout

- `protocol/` — `Frame` (codec and CRC), `FrameReader` (cuts the TCP stream into frames), `Telemetry` and
  `Reg` (typed reads of the registers the UI shows), `Writable` (the registers and values the app may
  write), `Options` (the timer choices), `Model` (what the serial in the AP's name says about the model).
- `link/` — `StationServer` (the 8058 server, acks, writes), `HotspotConnector` (the
  `WifiNetworkSpecifier` request for the AP), `StationBeacon` (the BLE scan that tells the setup phase),
  `StationLink` (the one app-wide link the screen and the service share, and the gate every write passes).
- `StationService` and `StationNotifications` — the `connectedDevice` foreground service that keeps the
  link while the app is closed, its status notification and the low-battery alert.
- `ui/` — one Compose screen: the connect card until the station dials, the status and settings cards after.
- `tools/` — Python helpers for working on the protocol from a PC on the station's AP: `listen.py` (the
  8058 server, logs every frame; with `--cloud`, a stand-in for the vendor's cloud on 5555), `send.py`
  (writes a register through it), `frame.py` and `regs.py` (codec and register map), `state.py` (decodes
  the latest frame of a log), `relay.py` (a logging TCP relay).

## Rules

- **Write only what the vendor was seen writing, within its ranges.** The firmware may not validate a write,
  and on the vendor's other platform a bad write has bricked a station. A register and value become writable
  once the vendor's app or its cloud is seen writing them to a station, in the app's code or in a capture of
  the link; never by guess. `Writable` is the whole list, the vendor settings screen's registers and ranges,
  and `StationLink.write` refuses anything outside it: a new writable register or value goes there, with its
  case in `WritableTest`, and the controls take their ranges from it. The cloud's schedule flags,
  `Reg.SCHEDULED_CHARGE` and `Reg.SCHEDULED_DISCHARGE`, are seen written too, but Fosshine only reads them.
  Nothing at all is written while SOC reads 0.
- **The UI shows what the station reports, never what was asked.** A write counts once telemetry reflects
  it; `StationServer.write` resends up to three times because the station does drop writes. Byte registers
  compare one byte, the wide ones (`Reg.WIDE`) sixteen. Telemetry older than a few seconds is stale: the
  controls go inactive rather than show it as the station now.
- **Only the station's network is trusted.** The server listens on every interface, so `StationServer`
  accepts a peer only from an IPv4 subnet of the AP that `HotspotConnector` requested. IPv6 stays out: the
  AP's link-local prefix is the same one every interface has.
- **Protocol changes come with a test** pinned to real frames, the way `FrameTest` pins the write frames
  the station accepted and a telemetry frame it sent.
- **`tools/send.py` checks nothing.** It writes any register with any value, so the first rule is on
  whoever runs it.
- **Strings** live in `values/` (English) and `values-uk/` (Ukrainian); names and units that are not
  translated are marked `translatable="false"`.
- **The name** is written "Fosshine", one word with only the F capitalized. Its "Foss" is the FOSS of free
  software. Fossibot is someone else's trademark: it may describe what the app works with, never appear whole
  in the app's name, and the app borrows none of its logo or colors.
