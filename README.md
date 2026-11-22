# Morning TV

A morning screen for Sony BRAVIA Android TVs. When your workday alarm rings on the iPhone, the TV wakes up and shows a greeting, today's weather and today's appointments. After a set time (default 30 minutes) it goes back to standby by itself.

- **No server, no account, no cloud of our own.** The iPhone talks to the TV directly on your home network.
- **Only at home:** the iPhone Shortcut checks your Wi-Fi first. Away from home, nothing happens.
- **Never takes over a TV in use:** if the TV is already on, the Shortcut stops.
- **Your calendar stays on your phone:** the Shortcut sends only today's events (title, start, end) when the alarm rings. No Apple password on the TV.
- **No open port:** the events travel inside Sony's app-launch command.

Weather comes from [Open-Meteo](https://open-meteo.com) (free, no key).

## Requirements

- A Sony BRAVIA Android TV with **IP control** (tested: KD-55XE8505, Android 9).
  - Settings → Network → Home network → IP control → Authentication **Normal and Pre-Shared Key**, set a key.
  - Wake-on-LAN / "Remote start" on, so the TV can be woken over the network.
  - A fixed IP for the TV (DHCP reservation in your router).
- An iPhone with the Shortcuts app.
- A Linux computer to build and install (no root needed).

## Setup

```bash
# 1. Toolchain (JDK 21, Android SDK) into ~/.local/opt
scripts/setup-toolchain.sh

# 2. Your config (git-ignored)
cp config/example.properties config/local.properties
$EDITOR config/local.properties

# 3. Guard against committing personal values (for contributors)
scripts/install-hooks.sh

# 4. On the TV: Settings → Device Preferences → About → press "Build" 7 times,
#    then Developer options → Network debugging ON. Then:
scripts/install.sh
#    Confirm "Allow debugging?" on the TV the first time.
#    Afterwards turn Network debugging OFF again.
```

Then build the iPhone Shortcut: **[docs/shortcut.md](docs/shortcut.md)**.

`scripts/tv.sh status|on|off|apps|launch` is a small helper to try the TV's API from the computer.

## Privacy

- `config/local.properties` holds your TV address, key, name and location. It is git-ignored, and `scripts/check-secrets.sh` (pre-commit hook) blocks commits that contain its values.
- Town-level coordinates are enough for the weather; no street address is needed.
- Secrets can come from the environment instead of the file: `MTV_TV_PSK=... scripts/install.sh`.
- Old TVs get no security updates. Turn Network debugging off after installing.

## How it works

1. Alarm → Shortcut: home Wi-Fi? TV off? → `setPowerStatus true` → wait → `setActiveApp` with today's events as `data`.
2. The TV starts the app with the events in a byte-array extra `PostBody` ([findings](docs/tv-findings.md)).
3. The app shows weather + appointments and arms the standby timer. Opened by hand from the launcher, it never arms the timer.
4. When the timer runs out, the app calls `setPowerStatus false` on the TV's own API (`127.0.0.1`).

## Development

```bash
source scripts/env.sh
./gradlew :app:testDebugUnitTest   # unit tests
tests/install_test.sh              # install script against a fake adb
./gradlew :app:assembleDebug       # APK
```

Design: [docs/superpowers/specs](docs/superpowers/specs). Plan: [docs/superpowers/plans](docs/superpowers/plans).

## License

[MIT](LICENSE)
