# TV findings

Measured on the reference TV: Sony BRAVIA KD-55XE8505 (2017), Android 9, Wake-on-LAN enabled, IP control with Pre-Shared Key. Date: 2026-11-22.

## Power

- `setPowerStatus {"status":true}` wakes the TV from standby. `getPowerStatus` reports `active` after **~4 s**.
- `setPowerStatus {"status":false}` sends it to standby (reply `{"result":[]}`, status `standby` 3 s later).
- The REST API and ADB stay reachable in standby.

## Passing data on app launch

- `appControl` → `setActiveApp` with `{"uri": "...", "data": "<string>"}` works.
- The launched activity gets `action=MAIN`, category `LEANBACK_LAUNCHER`, no data URI, and a **byte-array extra `PostBody`** holding the `data` string as UTF-8 (umlauts arrive intact).
- Without `data`, `PostBody` is present but **empty** (0 bytes). A launch from the TV's home screen has no `PostBody`.
- **Size limit:** a `data` string of 5.7 KB arrived; 8.6 KB and larger were dropped silently (empty HTTP reply, app not launched with the data). Keep the payload under **5 KB** (about 35 events with short fields).
- App URI pattern: `com.sony.dtv.<package>.<activity class>`, e.g. `com.sony.dtv.io.github.morningtv.io.github.morningtv.MainActivity`.

## Decision

TRANSPORT=launch-data

The events travel inside the launch call. The app needs no network port. A non-empty `PostBody` marks a Shortcut start and arms the standby timer.
