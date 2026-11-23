# Morning TV

A morning screen for Sony BRAVIA Android TVs. When your workday alarm rings on the iPhone, the TV wakes up and shows a greeting, today's weather and today's appointments. After a set time (default 30 minutes) it goes back to standby by itself.

Unofficial hobby project, not affiliated with or endorsed by Sony.

- **Time-graph layout (default):** one card with the hourly weather and temperature curve, today's appointments on the same time axis (each with the forecast at its start), and a line for the current time. The background colour follows the weather. Set `layout=classic` for two separate cards instead.
- **German and English**, chosen by the TV's system language.

- **No server, no account, no cloud of our own.** The iPhone talks to the TV directly on your home network.
- **Only at home:** the iPhone Shortcut checks your Wi-Fi first. Away from home, nothing happens.
- **Never takes over a TV in use:** if the TV is already on, the Shortcut stops.
- **Your calendar stays on your phone:** the Shortcut sends only today's events (title, start, end) when the alarm rings. No Apple password on the TV.
- **Optional Google Calendar:** set `google_ical_url` (see below) and the TV also reads today's events from Google's read-only secret iCal address. Both sources are merged. Today's agenda is cached, so opening the app by hand still shows it.
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

## Google Calendar (optional)

Google Calendar → Settings → your calendar → **Secret address in iCal format**. Put it into `config/local.properties` as `google_ical_url=...` and run `scripts/install.sh`. The address is read-only and works like a password: anyone who has it can read that calendar, so keep it private. You can reset it in the same Google settings page. Recurring events, all-day events and moved or cancelled instances are supported.

## Settings

All settings are in `config/local.properties` (see `config/example.properties`):

| Key | Meaning |
|---|---|
| `layout` | `timeline` (default) or `classic` |
| `calendar_style` | classic layout only: `timeline` (default) or `list` for 2+ appointments |
| `google_ical_url` | optional, see above |
| `standby_minutes`, `greeting_name`, `latitude`, `longitude`, `location_name`, `timezone` | as in the example file |

## Privacy and security

- `config/local.properties` holds your TV address, key, name and location. It is git-ignored, and `scripts/check-secrets.sh` (pre-commit hook) blocks commits that contain its values.
- Town-level coordinates are enough for the weather; no street address is needed.
- Secrets can come from the environment instead of the file: `MTV_TV_PSK=... scripts/install.sh`.
- The TV key (PSK) and the Google address are stored in the app's private storage on the TV in plain text, as is usual on Android. Only the adb shell can set them (`android.permission.DUMP`).
- The app only allows unencrypted HTTP to the TV itself (`127.0.0.1`); everything else uses HTTPS.
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

## License

[MIT](LICENSE)

## Credits

- Weather and place search: [Open-Meteo](https://open-meteo.com) (CC BY 4.0; free for non-commercial use).
- Icons: [Font Awesome Free](https://fontawesome.com) 6.5.1, [CC BY 4.0](https://fontawesome.com/license/free).
- "Sony" and "BRAVIA" are trademarks of Sony Group Corporation; they are used here only to name the supported TVs.
