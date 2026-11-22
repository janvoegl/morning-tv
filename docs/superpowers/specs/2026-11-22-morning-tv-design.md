# Morning TV — design

Date: 2026-11-22
Status: approved 2026-11-22 (builder check done)

## Why

The user wants the bedroom TV to act as a morning screen. When the regular workday alarm on the iPhone rings, the TV turns on and shows what is needed before leaving for work: today's weather and today's appointments. After 30 minutes the TV goes back to standby by itself. This is a base; more features come later.

The project is **open source**. Anyone with a Sony Android TV and an iPhone should be able to set it up with their own config. No personal data of the author is in the repo.

## Goals

- Only one chosen iPhone alarm triggers it (picked in the Shortcuts app, not every alarm).
- Only works when the user is at home (iPhone on home Wi-Fi). Away: nothing happens, no error pop-up.
- TV wakes from standby, opens the app; the app greets the user and shows weather + today's appointments.
- After 30 minutes (configurable) the app puts the TV into standby.
- Easy to extend with more panels later.
- Other people can build, configure and install it from the README.

## Non-goals (for now)

- No other info panels (news, traffic, etc.).
- No remote interaction beyond leaving the app.
- No server, no extra device, no cloud service of our own.
- No Play Store release. Sideload over ADB.
- No support for non-Sony TVs (the power and app-launch commands are Sony's REST API).

## Anti-goals (a failure even if it "works")

- TV wakes when the user is not at home.
- The app takes over or switches off the TV while someone is already using it.
- An Apple password or account token ends up on the TV.
- A port on the TV that anyone on the LAN can use without a secret.
- Personal data (address, location, IPs, names, keys, tokens) committed to the repo.

## Constraints

**Target TV:** Sony BRAVIA Android TV with IP control. Reference device: KD-55XE8505 (2017).
- Android 9 (API 28), 32-bit ARM (armeabi-v7a), ~1.6 GB free, D-pad remote only.
- Old TVs get no more security updates → keep attack surface small.

**Sony REST API** (checked on the reference TV, 2026-11-22):
- Needs "IP control" with a Pre-Shared Key set on the TV. Auth header `X-Auth-PSK`.
- `POST /sony/system` `getPowerStatus` → `active` / `standby`, works while in standby.
- `getWolMode` → enabled on the reference TV. `setPowerStatus` (`{"status": true|false}`) is expected to wake/sleep it. **Not yet tested.**
- `POST /sony/appControl` `getApplicationList` lists installed apps; `setActiveApp` with `uri` launches one. URI pattern: `com.sony.dtv.<package>.<activity class>`.
- The TV's API is reachable from the TV itself at `http://127.0.0.1/sony/...`.

**Install:** ADB over the LAN. The user turns "Network debugging" on in Developer options for the install and off afterwards.

**Build machine:** Linux, no root assumed. JDK + Android command-line tools installed in the user's home. adb from Google's platform-tools.

**iPhone:** Shortcuts app with a personal automation triggered by a specific alarm, set to run without asking. Calendar: whatever calendars the iPhone shows (tested with iCloud).

**Network:** the TV should have a fixed IP (DHCP reservation in the router), so the Shortcut's target doesn't move.

## Approaches considered (getting the calendar onto the TV)

| | How | Trade-off |
|---|---|---|
| **A. iPhone sends events (chosen)** | Shortcut reads today's events from all phone calendars and sends them to the app on the TV | No Apple credentials on the TV, covers all calendars. Snapshot at alarm time only. |
| B. TV pulls from iCloud (CalDAV) | App logs in with an Apple app-specific password | Works without the phone, can refresh. But a read/write calendar password sits on an unpatched TV. Rejected. |
| C. Public iCloud calendar link | App reads a published ICS URL | Easy. But the calendar is public to anyone with the link, one calendar at a time, delayed updates. Rejected. |

The user picked A. The iPhone talks to the TV directly; no server involved.

## Flow (decided with the user)

1. The chosen workday alarm goes off → Shortcuts automation runs without asking.
2. Shortcut checks the current Wi-Fi network name. Not home Wi-Fi → stop quietly.
3. Shortcut asks the TV `getPowerStatus`. Already `active` → stop (TV is in use).
4. Shortcut calls `setPowerStatus true`, then `setActiveApp` with the app's URI.
5. Shortcut finds today's calendar events (all calendars) and sends them to the app.
6. App shows greeting, weather, appointments, and a countdown.
7. When the standby time is reached, the app calls `setPowerStatus false` on `127.0.0.1` → TV goes to standby.
8. If the user leaves the app before that (Home button or another app), the timer is cancelled and the TV stays on.
9. *Claude's call:* the app also shows up in the TV launcher (to check the weather any time). Opened by hand, it **never** arms the standby timer. The timer is armed only by the Shortcut's launch (intent data if the `data` check works, otherwise the arrival of the Shortcut's POST).

## Screen (decided with the user)

```
┌────────────────────────────────────────────────────────┐
│  Good morning, <name>                 Mon, 23 Nov 06:30 │
│                                                        │
│  TODAY'S WEATHER              │  TODAY'S APPOINTMENTS  │
│  ☁  4°  now                   │  09:00  Team meeting   │
│  high 8° / low 1°             │  12:30  Lunch          │
│  rain 40% from 14:00          │  18:00  Gym            │
│  07  09  12  15  18           │                        │
│  3°  5°  7°  6°  4°           │  (all-day: Birthday)   │
│                                         TV off in 29 min│
└────────────────────────────────────────────────────────┘
```

- Greeting with the configured name and current date/time.
- Weather: now (icon + temp), today's high/low, rain chance with the time it starts, a few hours ahead.
- Appointments: start time + title, all-day events listed separately. No data received → "No appointments today" (or a waiting state, see builder notes).
- Countdown to standby, small, bottom right.
- UI language: English.

## Weather

- Source: Open-Meteo forecast API (free, no key, no account).
- Location: one fixed place from the user's config (latitude, longitude, display name, timezone). Town-level coordinates are enough; the README should say so (no street address needed).
- Fetched when the app starts. If it fails: show "Weather unavailable"; the rest of the screen still works.

## Calendar receiver (security)

- A small HTTP server inside the app, **only running while the app's screen is open**. Stops when the app closes.
- Accepts only one endpoint (e.g. `POST /events`) with a secret token in a header. Everything else → rejected.
- Token is a long random value generated per install, never in the repo.

## Configuration and secrets (open source rules)

- All personal values live in **one local config file that is git-ignored** (e.g. `config/local.properties`). The repo ships `config/example.properties` with placeholder values and comments.
- Config values: TV IP, TV Pre-Shared Key, receiver token, greeting name, latitude/longitude/location name, timezone, standby minutes (default 30).
- The install step reads the local config and puts the values onto the TV (e.g. over ADB, like the AirPlay app's settings broadcast). They are never compiled into a committed file.
- The Shortcut guide in the README uses placeholders (`<TV_IP>`, `<PSK>`, `<TOKEN>`) that the user fills in on the iPhone.
- A check (script or pre-commit hook) fails if the local config is staged or if known secret-looking values appear in tracked files.
- License: **MIT** (Claude's call: short, permissive, common for small tools; easy to change before the first public push).
- Public hosting (e.g. GitHub) only after the user says so.

## Left to the builder

- Language/toolkit: native Kotlin Android app, minSdk 28, single full-screen activity (Claude's call). No native code, so the ABI doesn't matter.
- How the Shortcut waits until the app's receiver is ready before sending events (fixed wait vs. retry), and what the app shows while waiting.
- **Check first:** whether `setActiveApp` accepts a `data` field that reaches the app's launch intent. *Claude's call:* if it works, events travel with the launch call and the receiver port is **dropped** (fits the "no open port" anti-goal; the Shortcut then collects events before step 4). If it doesn't, build the token-protected receiver as described.
- Calendar payload format (JSON fields), time zone handling, all-day events.
- Exact Shortcut steps, documented in the README (Shortcuts can't be created from a computer).
- Build and install scripts (Gradle, ADB install, pushing config to the TV).
- Tests: unit tests for payload parsing, weather parsing, timer logic. End-to-end test with a real TV and iPhone at the end.
- Verifying `setPowerStatus true/false` works from standby before building on it.

## Project

- Repo: `~/projects/morning-tv` (local git, no remote yet).
- This document: `docs/superpowers/specs/2026-11-22-morning-tv-design.md`.
- The author's own setup (IPs, location, key names) is kept outside the repo.
