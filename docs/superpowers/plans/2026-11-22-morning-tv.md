# Morning TV Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An open-source Android TV app for Sony BRAVIA TVs that an iPhone alarm Shortcut wakes and opens; it shows a greeting, today's weather and today's appointments, then puts the TV to standby after a set time.

**Architecture:** One Kotlin Android app (single full-screen Activity, Android Views). Pure-Kotlin logic (payload parsing, weather parsing, timer) is unit-tested on the JVM. Personal values live in a git-ignored `config/local.properties`; `scripts/install.sh` builds, installs over ADB and pushes the config into the app via a shell-only broadcast. The iPhone Shortcut talks to the TV's Sony REST API directly; how calendar events reach the app (launch data vs. token-protected HTTP receiver) is decided by the probe in Task 2.

**Tech Stack:** Kotlin, Gradle (Kotlin DSL) with wrapper, Android Gradle Plugin (latest stable supporting minSdk 28), kotlinx.serialization (JSON), java.time, HttpURLConnection, JUnit 4. NanoHTTPD only if Task 2 picks the receiver variant. Bash scripts with Google platform-tools `adb`.

**Spec:** `docs/superpowers/specs/2026-11-22-morning-tv-design.md`

## Global Constraints

- minSdk 28 (Android 9); must run on 32-bit ARM (armeabi-v7a) → no native code.
- Application ID / package: `io.github.morningtv`. App label: "Morning TV".
- UI language: English. D-pad only; nothing needs touch.
- Weather: Open-Meteo forecast API, no key. If it fails: "Weather unavailable", rest of screen still works.
- Standby default: 30 minutes, configurable (`standby_minutes`).
- Opened by hand from the launcher → standby timer is **never** armed.
- Leaving the app (onStop) cancels the timer; the TV stays on.
- The app's HTTP receiver (if built) runs only while the Activity is started, accepts only `POST /events` with header `X-Morning-Token: <token>`.
- No personal data in tracked files: no IPs, keys, tokens, names, coordinates. Examples use placeholders.
- License: MIT.
- Build tools install into `~/.local/opt/` (JDK, Android SDK, Gradle bootstrap). No sudo. No files loose in `~`.
- Never push to a remote; the user decides about public hosting.

## Review Focus

- TV just woke from standby and the network isn't up yet → weather fetch retries (3 tries, 5 s apart) before showing "Weather unavailable". Test in Task 5.
- No events / empty list / malformed payload → "No appointments today" (malformed is logged, never a crash). Test in Task 4.
- Multi-day events and events with a UTC offset different from the configured timezone → shown in local time; events that started yesterday and run into today show as "until HH:mm". Test in Task 4.
- Shortcut fires twice (launch while already running) → one timer, not two; timer restarts from the latest arming only. Test in Task 6.
- Standby call fails (wrong PSK, TV API down) → visible small error line, app stays usable, no crash. Test in Task 6.

---

## File Structure

```
morning-tv/
├── LICENSE                                   MIT
├── README.md                                 what, requirements, setup, Shortcut guide
├── .gitignore                                (exists) ignores config/local.properties etc.
├── config/example.properties                 placeholder config with comments
├── scripts/
│   ├── env.sh                                sets JAVA_HOME / ANDROID_HOME / PATH from ~/.local/opt
│   ├── setup-toolchain.sh                    downloads JDK, cmdline-tools, platform-tools, SDK packages
│   ├── check-secrets.sh                      fails if local config is tracked or its values appear in tracked files
│   ├── install-hooks.sh                      installs check-secrets as git pre-commit hook
│   ├── tv.sh                                 Sony REST helper: power/status/apps/launch (reads local config)
│   └── install.sh                            build + adb install + push config
├── docs/tv-findings.md                       result of the Task 2 probe (no personal data)
├── docs/shortcut.md                          step-by-step iPhone Shortcut guide
├── settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, gradlew*, gradle/wrapper/*
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/AndroidManifest.xml
        ├── main/res/layout/activity_main.xml, values/strings.xml, drawable/banner.xml (320x180 leanback banner)
        ├── main/java/io/github/morningtv/
        │   ├── MainActivity.kt               wires everything, renders, lifecycle
        │   ├── config/AppConfig.kt           data class + ConfigStore (SharedPreferences)
        │   ├── config/ConfigReceiver.kt      shell-only broadcast to set config values
        │   ├── calendar/Agenda.kt            payload model + parseAgenda()
        │   ├── weather/Weather.kt            WeatherSummary model + parseForecast() + forecastUrl()
        │   ├── weather/WeatherClient.kt      fetch with retry
        │   ├── standby/StandbyTimer.kt       pure timer logic (injectable clock)
        │   ├── sony/SonyApi.kt               setPowerStatus(false) on 127.0.0.1
        │   └── transport/…                   LaunchData.kt (variant A) or EventsServer.kt (variant B)
        └── test/java/io/github/morningtv/…   JVM unit tests mirroring the above
```

---

### Task 1: Toolchain, project skeleton, secret guard

**Files:**
- Create: `scripts/env.sh`, `scripts/setup-toolchain.sh`, `scripts/check-secrets.sh`, `scripts/install-hooks.sh`, `config/example.properties`, `LICENSE`, Gradle files, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/io/github/morningtv/MainActivity.kt` (empty Activity showing "Morning TV"), `app/src/test/java/io/github/morningtv/SmokeTest.kt`
- Test: `scripts/check-secrets.sh` self-test (below)

**Interfaces:**
- Produces: `source scripts/env.sh` makes `java`, `adb`, `sdkmanager`, `./gradlew` usable. `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` work. `config/example.properties` keys (exact): `tv_host`, `tv_psk`, `receiver_token`, `greeting_name`, `latitude`, `longitude`, `location_name`, `timezone`, `standby_minutes`, `home_wifi` (documented only; used by the Shortcut).

- [ ] **Step 1:** Write `scripts/setup-toolchain.sh`: Temurin JDK 17 tarball → `~/.local/opt/jdk-17`, Android cmdline-tools → `~/.local/opt/android-sdk/cmdline-tools/latest`, then `sdkmanager "platform-tools" "platforms;android-<compileSdk>" "build-tools;<ver>"` with license acceptance. Idempotent (skip what exists). Check current stable versions against their official sources, not memory.
- [ ] **Step 2:** Run it. Expected: `java -version` → 17, `adb version` prints, `sdkmanager --list_installed` shows the platform.
- [ ] **Step 3:** Create the Gradle project (wrapper generated with a temporary Gradle download in `~/.local/opt`, then only the wrapper is used). compileSdk/targetSdk = latest installed, minSdk 28, Kotlin + kotlinx.serialization plugin, JUnit 4 for tests. Manifest: INTERNET permission, `MainActivity` with `MAIN` + `LEANBACK_LAUNCHER` + `LAUNCHER` categories, `android:banner`, `uses-feature android.software.leanback required=false`, `android.hardware.touchscreen required=false`.
- [ ] **Step 4:** `SmokeTest.assertsTrue()` (`assertTrue(true)`), run `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Expected: BUILD SUCCESSFUL, APK under `app/build/outputs/apk/debug/`.
- [ ] **Step 5:** Write `scripts/check-secrets.sh`: exit 1 if `git ls-files --error-unmatch config/local.properties` succeeds, or if any non-empty value from `config/local.properties` (when present; values ≥4 chars) appears in `git diff --cached` content or in tracked files. Prints only the file name and key name, never the value. `scripts/install-hooks.sh` links it as `.git/hooks/pre-commit`.
- [ ] **Step 6:** Self-test: create a temp `config/local.properties` with `greeting_name=Zyxwvut`, stage a file containing `Zyxwvut` → `scripts/check-secrets.sh` exits 1 naming `greeting_name`; unstage and remove → exits 0. Remove the temp files.
- [ ] **Step 7:** Commit: `git add -A && git commit -m "chore: project skeleton, toolchain scripts, secret guard"` (hook installed first; verify it ran).

### Task 2: TV probe (gate for Task 7)

**Files:**
- Create: `scripts/tv.sh`, `docs/tv-findings.md`

**Interfaces:**
- Consumes: `config/local.properties` (`tv_host`, `tv_psk`).
- Produces: `scripts/tv.sh status|on|off|apps|launch <uri> [data]` — prints the JSON reply. `docs/tv-findings.md` stating: (a) does `setPowerStatus true` wake from standby, (b) does `setPowerStatus false` sleep, (c) does `setActiveApp` accept `data`, and does it reach the launched intent (seen as `dat=` or extras in `adb logcat` `ActivityTaskManager: START` lines), (d) the delay from `on` until the API answers `active`. **Variant decision line:** `TRANSPORT=launch-data` or `TRANSPORT=receiver`.

- [ ] **Step 1:** Write `tv.sh` (reads the local config; PSK sent as header from the variable, never echoed). The user must agree before `on`/`off` are run (it switches their TV).
- [ ] **Step 2:** With the user present: `status` → standby; `on` → poll `status` until `active`, note seconds; `off` → `standby`.
- [ ] **Step 3:** With Network debugging on: `adb logcat -c`; `tv.sh launch com.sony.dtv.com.phlox.tvwebbrowser.com.phlox.tvwebbrowser.activity.main.MainActivity '{"probe":1}'`; `adb logcat -d | grep -E "START u0"`. Record whether the data appears and how (data URI vs. extra name). Also try `data` as a URI string (`morningtv://x?p=1`).
- [ ] **Step 4:** Write `docs/tv-findings.md` (no IPs/keys; model and Android version are fine). Commit.

### Task 3: Config store and config push

**Files:**
- Create: `app/src/main/java/io/github/morningtv/config/AppConfig.kt`, `config/ConfigReceiver.kt`, `scripts/install.sh`
- Modify: `AndroidManifest.xml` (receiver, `android:permission="android.permission.DUMP"`, exported)
- Test: `app/src/test/java/io/github/morningtv/config/AppConfigTest.kt`

**Interfaces:**
- Produces:
  - `data class AppConfig(val tvPsk: String, val receiverToken: String, val greetingName: String, val latitude: Double, val longitude: Double, val locationName: String, val timezone: java.time.ZoneId, val standbyMinutes: Int)`
  - `fun AppConfig.Companion.fromMap(values: Map<String, String>): AppConfig` — defaults: `greetingName=""` (greeting becomes "Good morning"), `standbyMinutes=30`, `timezone=ZoneId.systemDefault()`; missing lat/lon → `latitude = Double.NaN` (weather shows "Weather location not set").
  - `class ConfigStore(prefs: SharedPreferences) { fun load(): AppConfig; fun set(key: String, value: String) }` with keys exactly as in `config/example.properties` (snake_case).
  - Broadcast: action `io.github.morningtv.SET_CONFIG`, extras `key`, `value`.
  - `scripts/install.sh`: `assembleDebug` → `adb connect $tv_host:5555` → `adb install -r` → one `am broadcast` per key from `config/local.properties` (excluding `tv_host`, `home_wifi`) → `adb disconnect`. Values passed as arguments, not echoed.

- [ ] **Step 1:** Tests: `fromMap` with all keys → exact values; `fromMap(emptyMap())` → `standbyMinutes == 30`, `greetingName == ""`, `latitude.isNaN()`; `standby_minutes=abc` → 30; `timezone=Europe/Vienna` → `ZoneId.of("Europe/Vienna")`.
- [ ] **Step 2:** Run `./gradlew :app:testDebugUnitTest --tests '*AppConfigTest*'` → FAIL (unresolved).
- [ ] **Step 3:** Implement `AppConfig`, `ConfigStore`, `ConfigReceiver`, manifest entry, `install.sh`.
- [ ] **Step 4:** Run tests → PASS. Commit.

### Task 4: Calendar payload → agenda

**Files:**
- Create: `app/src/main/java/io/github/morningtv/calendar/Agenda.kt`
- Test: `app/src/test/java/io/github/morningtv/calendar/AgendaTest.kt`

**Interfaces:**
- Payload (the Shortcut produces this; document in `docs/shortcut.md`):
  ```json
  {"events":[{"title":"Team meeting","start":"2026-11-23T09:00:00+01:00","end":"2026-11-23T10:00:00+01:00","allDay":false}]}
  ```
- Produces:
  - `data class AgendaItem(val title: String, val timeLabel: String, val allDay: Boolean)` — `timeLabel` is `"09:00"`, `"until 11:00"` (started before today), or `""` for all-day.
  - `data class Agenda(val timed: List<AgendaItem>, val allDay: List<AgendaItem>)`
  - `fun parseAgenda(json: String, today: java.time.LocalDate, zone: java.time.ZoneId): Agenda?` — `null` on malformed JSON; drops events not touching `today` in `zone`; timed sorted by start; titles trimmed, empty title → "(no title)".

- [ ] **Step 1:** Tests: `parsesSingleEventInLocalTime` (start `09:00:00Z`, zone Europe/Vienna, winter → `"10:00"`); `sortsByStart`; `allDayGoesToAllDayList`; `eventStartedYesterdayShowsUntil` (→ `"until 11:00"`); `eventTomorrowDropped`; `emptyEventsGivesEmptyAgenda`; `malformedReturnsNull` (`"{oops"`); `blankTitleBecomesNoTitle`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement with kotlinx.serialization (`ignoreUnknownKeys = true`) and `OffsetDateTime.parse`.
- [ ] **Step 4:** Run → PASS. Commit.

### Task 5: Weather

**Files:**
- Create: `app/src/main/java/io/github/morningtv/weather/Weather.kt`, `weather/WeatherClient.kt`
- Test: `app/src/test/java/io/github/morningtv/weather/WeatherTest.kt` with fixture `app/src/test/resources/open-meteo-sample.json` (a real response saved for a made-up location; no personal coordinates)

**Interfaces:**
- Produces:
  - `fun forecastUrl(lat: Double, lon: Double, zone: ZoneId): String` → `https://api.open-meteo.com/v1/forecast?latitude=…&longitude=…&current=temperature_2m,weather_code&hourly=temperature_2m,precipitation_probability,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=<zone id URL-encoded>&forecast_days=1`
  - `data class HourPoint(val hour: Int, val tempC: Int, val code: Int)`
  - `data class WeatherSummary(val nowTempC: Int, val nowCode: Int, val highC: Int, val lowC: Int, val rainChance: Int, val rainFromHour: Int?, val hours: List<HourPoint>)` — `rainChance` = max precipitation probability from the current hour on; `rainFromHour` = first hour from now with probability ≥ 30, else null; `hours` = 07, 09, 12, 15, 18 (only those ≥ current hour).
  - `fun parseForecast(json: String, nowHour: Int): WeatherSummary?` (null on malformed)
  - `fun weatherLabel(code: Int): String` (WMO codes → "Clear", "Cloudy", "Fog", "Drizzle", "Rain", "Snow", "Showers", "Thunderstorm") and `fun weatherIcon(code: Int): String` (☀ ⛅ ☁ 🌫 🌧 ❄ ⛈).
  - `class WeatherClient(fetch: (String) -> String = ::httpGet, sleep: (Long) -> Unit = Thread::sleep) { fun load(cfg: AppConfig, nowHour: Int): WeatherSummary? }` — 3 attempts, 5000 ms apart; any exception counts as a failed attempt.

- [ ] **Step 1:** Tests: `urlContainsCoordinatesAndZone`; `parsesFixture` (exact values from the fixture); `rainFromHourFirstAtOrAbove30`; `hoursSkipPast` (nowHour 10 → 12, 15, 18); `malformedReturnsNull`; `clientRetriesThreeTimesThenNull` (fake fetch throws, counts 3 calls, sleep called 2×); `clientSucceedsOnSecondTry`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run → PASS. Commit.

### Task 6: Standby timer and Sony standby call

**Files:**
- Create: `app/src/main/java/io/github/morningtv/standby/StandbyTimer.kt`, `sony/SonyApi.kt`
- Test: `app/src/test/java/io/github/morningtv/standby/StandbyTimerTest.kt`, `sony/SonyApiTest.kt`

**Interfaces:**
- Produces:
  - `class StandbyTimer(private val now: () -> Long) { fun arm(minutes: Int); fun cancel(); fun remainingMs(): Long?; fun isDue(): Boolean }` — `arm` replaces any earlier arming; `remainingMs` null when not armed; never negative.
  - `fun countdownLabel(remainingMs: Long): String` → `"TV off in 29 min"`, under 1 min → `"TV off in <1 min"`.
  - `class SonyApi(private val post: (url: String, headers: Map<String, String>, body: String) -> Pair<Int, String>) { fun standby(psk: String): Result<Unit> }` — POST `http://127.0.0.1/sony/system`, header `X-Auth-PSK`, body `{"method":"setPowerStatus","id":1,"params":[{"status":false}],"version":"1.0"}`; failure if HTTP ≠ 200 or reply has `"error"`.

- [ ] **Step 1:** Tests: `notArmedHasNoRemaining`; `armThirtyMinutes` (fake clock → remaining 1_800_000); `rearmReplacesEarlier`; `cancelClears`; `dueAfterTime`; `labelRoundsUp` (29 min 1 s → "TV off in 30 min"); `standbySendsExactBody`; `standbyErrorReplyIsFailure` (`{"error":[403,"Forbidden"]}`); `standbyExceptionIsFailure`.
- [ ] **Step 2:** Run → FAIL.
- [ ] **Step 3:** Implement.
- [ ] **Step 4:** Run → PASS. Commit.

### Task 7: Event transport (variant from `docs/tv-findings.md`)

Do **only** the variant named in the findings' `TRANSPORT=` line.

**Variant A — `launch-data`**
- Files: `transport/LaunchData.kt`, test `transport/LaunchDataTest.kt`
- Produces: `fun extractPayload(dataString: String?, extras: Map<String, String?>): String?` — returns the events JSON in whatever form Task 2 found (e.g. URI query param `p`, URL-decoded). `isShortcutLaunch = extractPayload(...) != null`.
- Tests: exact input form recorded in findings → JSON string; plain launcher intent (null data, no extras) → null.

**Variant B — `receiver`**
- Files: `transport/EventsServer.kt` (NanoHTTPD), test `transport/EventsServerTest.kt`
- Produces: `class EventsServer(port: Int = 8765, token: String, onEvents: (String) -> Unit) { fun start(); fun stop() }` — `POST /events` with correct `X-Morning-Token` → 204 and `onEvents(body)`; wrong/missing token → 403; other path/method → 404; body > 64 KB → 413. Arrival of a valid POST counts as the Shortcut launch (arms the timer).
- Tests: start on a free port, real HTTP calls for each case above; `stop()` closes the port (connect fails).

- [ ] **Step 1:** Write tests for the chosen variant. **Step 2:** Run → FAIL. **Step 3:** Implement. **Step 4:** Run → PASS. **Step 5:** Commit.

### Task 8: Screen and lifecycle

**Files:**
- Modify: `MainActivity.kt`
- Create: `res/layout/activity_main.xml`, `res/values/strings.xml`, `res/drawable/banner.xml`
- Test: `app/src/test/java/io/github/morningtv/GreetingTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 3–7.
- Produces: `fun greeting(name: String): String` → `"Good morning, Alex"` / `"Good morning"` for blank.

Behavior (from spec): layout as in the spec sketch, dark background, large type readable from bed. Left: weather (or "Weather unavailable" / "Weather location not set"). Right: timed events, then all-day events; more than 8 timed → first 8 + "+N more"; long titles ellipsized. While waiting for events (variant B or launch without data yet): "Waiting for calendar…" for up to 60 s, then "No appointments today". Bottom right: countdown only when armed, updated every 30 s. `onStart`: load config, start weather load on a background thread, start receiver (variant B). `onStop`: cancel timer, stop receiver. When due: call `SonyApi.standby`; on failure show "Could not switch the TV off" in small text. `FLAG_KEEP_SCREEN_ON` while armed. `onNewIntent` handles a second Shortcut launch (re-arm, replace agenda).

- [ ] **Step 1:** `GreetingTest`: `withName`, `blankName`.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement greeting, layout and Activity wiring. **Step 4:** Run all unit tests → PASS; `assembleDebug` → SUCCESS.
- [ ] **Step 5:** Install with `scripts/install.sh` (user turns Network debugging on), open from the TV launcher → screen shows weather, "No appointments today", no countdown. Screenshot via `adb exec-out screencap -p > <scratchpad>/screen.png` and look at it. Commit.

### Task 9: Shortcut guide and README

**Files:**
- Create: `docs/shortcut.md`, `README.md`

- [ ] **Step 1:** `docs/shortcut.md`: exact Shortcuts actions in order (Personal Automation → Alarm → "Goes Off" → pick the alarm → Run Immediately; Get Network Details → Wi-Fi name; If ≠ `<HOME_WIFI>` → Stop; Get Contents of URL `getPowerStatus` → If `active` → Stop; `setPowerStatus true`; wait until `active` (delay from findings); Find Calendar Events (start is today) → Repeat → build the payload from Task 4; then the variant's delivery (launch `data` or `setActiveApp` + Wait + POST `/events` with `X-Morning-Token`). Placeholders only.
- [ ] **Step 2:** `README.md`: what it does, supported TVs, requirements (Sony IP control + PSK, Network debugging for install), quick start (`setup-toolchain.sh`, copy `config/example.properties` → `config/local.properties`, `install.sh`, build the Shortcut), privacy notes (town-level coordinates are enough; local config is git-ignored; turn Network debugging off after install), MIT license.
- [ ] **Step 3:** `scripts/check-secrets.sh` → exit 0. Commit.

### Task 10: End-to-end on the real TV (with the user)

- [ ] **Step 1:** User builds the Shortcut from `docs/shortcut.md`, sets a test alarm 2 minutes ahead with standby set to 2 minutes (`standby_minutes=2`, re-run `install.sh`).
- [ ] **Step 2:** TV in standby, iPhone on home Wi-Fi → alarm → TV wakes, app shows weather + real appointments + countdown → TV goes to standby after 2 min.
- [ ] **Step 3:** Repeat with iPhone off home Wi-Fi → nothing happens, no error pop-up. Repeat with TV already on → app not opened.
- [ ] **Step 4:** Open the app by hand → no countdown; press Home during an armed run → TV stays on.
- [ ] **Step 5:** Set `standby_minutes=30`, re-install, user switches Network debugging off. Record results in `docs/tv-findings.md`, commit.
