# Phone automation

When your alarm rings, the phone wakes the TV and starts Morning TV. Two variants:

- **iPhone:** a Shortcut named "Morning TV" does the work (with two small helper Shortcuts) and a personal automation runs it when an alarm rings. It also sends today's events from the iPhone calendar.
- **Android:** an automation app does the same TV calls. Android has no counterpart of the iPhone calendar step, so the appointments come from the optional [Google calendar address](../README.md#google-calendar-optional) instead.

Placeholders, replace them with your own values:

| Placeholder | What it is |
|---|---|
| `<TV_IP>` | the TV's IP address (`tv_host` in your config) |
| `<PSK>` | the TV's Pre-Shared Key (`tv_psk`) |
| `<HOME_WIFI>` | your home Wi-Fi name (`home_wifi`) |

The app URI is the same for everyone:
`com.sony.dtv.io.github.morningtv.io.github.morningtv.MainActivity`

Every TV request is an HTTP **POST** with the header `X-Auth-PSK: <PSK>`, a JSON body, and the URL `http://<TV_IP>/sony/system` (power) or `http://<TV_IP>/sony/appControl` (start the app).

---

# iPhone

Your iPhone shows German names for the Shortcuts actions below. The names here are the English ones; search the action list by what the action does. Example: *Get Network Details* is "Netzwerkdetails abrufen".

## 1. Three Shortcuts

Build three small Shortcuts instead of one long one. Each can be run and checked on its own, and the main one only calls the other two. Names must match exactly, because *Run Shortcut* picks them by name. Create them in this order and run each one by hand before you build the next.

Rules for all three:

- **No Quick Look, Show Result or Show Alert** in the finished Shortcuts. They wait for you to dismiss them, so the Shortcut hangs when an alarm runs it. Add one temporarily while testing, then delete it.
- **Do not type curly braces.** The JSON comes from Dictionary actions. A hand-typed `{...}` in a Text action ended up as `{{...}}`, which is not valid JSON.
- `id` is a **Number**, `status` is a **Boolean**, `params` is an **Array**.
- The first run of each Shortcut may ask for permission (calendar data, network). Run each once by hand and tap **Allow** before you rely on the alarm; an unanswered question at alarm time stops it.
- Do not share a Shortcut (iCloud link or file) that contains your key: the file holds it in plain text.

A ready-made prompt for each Shortcut is in [shortcut-creation-prompt.md](shortcut-creation-prompt.md). The steps:

### "MTV Wake": wake the TV, answer whether it was already on

Settings: Text `<PSK>` -> *PSK*, Text `<TV_IP>` -> *TV*.

1. Get Contents of URL (`/sony/system`), JSON body: `method` (Text) = `getPowerStatus`, `id` (Number) = `1`, `version` (Text) = `1.0`, `params` (Array) = empty -> *PowerReply*.
2. **If** *PowerReply* **contains** `active`: **Text** `true` -> *TVOn*. **Otherwise**: **Text** `false` -> *TVOn*.
3. **If** *TVOn* **is** `false`: Get Contents of URL (`/sony/system`), body `method` = `setPowerStatus`, `id` (Number) = `1`, `version` = `1.0`, `params` (Array) = one Dictionary with `status` (Boolean) = **true**. Then **Text** `false` -> *Ready*, **Wait** 3 seconds, **Repeat** 7 times: **If** *Ready* is `false`: ask `getPowerStatus` again; if the reply contains `active`, **Text** `true` -> *Ready*, otherwise **Wait** 1 second.
   (The TV reports "on" after about 4 s. The loop goes on as soon as it does.)
4. **Stop and Output** *TVOn* (the text `true` or `false`).

Check: with the TV in standby it wakes and outputs `false`; run again, it outputs `true` and does nothing else.

### "MTV Events": today's appointments as the payload text

Takes the text `true` or `false` as its input (whether the TV was already on).

1. **Find Calendar Events** where **Start Date** **is today**, sorted by Start Date, **Limit** 15.
2. **Repeat with Each** *Calendar Events*:
   - **Format Date** *Start Date*, ISO 8601 with time -> *Start*; **Format Date** *End Date*, ISO 8601 with time -> *End*.
   - **If** *Repeat Item > Is All Day* **is** Yes: **Text** `true` -> *AllDayText*. **Otherwise**: **Text** `false` -> *AllDayText*.
   - **Dictionary** (all values Text): `title` = *Title*, `start` = *Start*, `end` = *End*, `allDay` = *AllDayText*. This is the **last** action in the loop, so it becomes the result of each round. Variables go into values only, never into a key name.
3. **Dictionary**: `events` = *Repeat Results* (Array), `keepOn` = *Shortcut Input* (Text).
4. **Get Text from** that Dictionary (JSON text), then **Stop and Output** it.

Check: run it on its own. The result is one JSON text with your events under `events`; `keepOn` is empty when you run it without input, which the app reads as "off".

### "Morning TV": the one the alarm runs

Settings: Text `<PSK>` -> *PSK*, Text `<TV_IP>` -> *TV*, Text `<HOME_WIFI>` -> *HOME_WIFI*.

1. **Get Network Details** -> Wi-Fi -> **Network Name** -> *WifiName*. **If** *WifiName* **does not begin with** *HOME_WIFI*: **Stop This Shortcut**. (Use *does not begin with* and a shared prefix such as `MyHome`, not *is not*: with *is not* the exact name must match, and a 5 GHz or guest network name does not.)
2. **Run Shortcut** "MTV Wake" -> *TVOn*.
3. **Run Shortcut** "MTV Events" with input *TVOn* -> *Payload*.
4. Get Contents of URL (`/sony/appControl`), JSON body: `method` = `setActiveApp`, `id` (Number) = `1`, `version` = `1.0`, `params` (Array) = one Dictionary with `uri` (Text) = `com.sony.dtv.io.github.morningtv.io.github.morningtv.MainActivity` and `data` (Text) = *Payload* -> *LaunchReply*.
5. **Safety net:** **If** *TVOn* **is** `false` and *LaunchReply* **does not contain** `result`: Get Contents of URL (`/sony/system`), body `method` = `setPowerStatus`, `id` (Number) = `1`, `version` = `1.0`, `params` (Array) = one Dictionary with `status` (Boolean) = **false**.
   (If the app could not be started, e.g. because the payload was too large or the key was wrong, the TV would otherwise stay on all day. A TV that was already on is never switched off.)

**Test it by hand first**, twice: with the TV in standby (it wakes, shows the screen, and switches off after `standby_minutes`) and with the TV on (the screen opens over what is running and the TV stays on).

## 2. The automation that runs it

Labels are from Apple's German Shortcuts guide for iOS 26 ([create a personal automation](https://support.apple.com/de-at/guide/shortcuts/apdfbdbd7123/ios), [event triggers](https://support.apple.com/de-at/guide/shortcuts/apd932ff833f/ios)). Labels marked *(not confirmed)* were not on those pages and may look different on your iOS version.

1. Open **Kurzbefehle** and tap **Automation**. If you already have automations, tap the "+" at the top right.
2. Tap **Persönliche Automation erstellen**.
3. Choose the trigger **Wecker**.
4. Pick the alarm:
   - **Beliebig**: any alarm triggers it, also weekend and nap alarms.
   - **Bereits vorhanden**: pick one saved alarm, e.g. your workday alarm. Use this if the TV should not wake on other alarms.
5. Choose the event that means "the alarm rings" *(not confirmed; Apple's page lists **Schlummert** and **Gestoppt ist** as events)*. Alternative: **Gestoppt ist**, then the TV wakes when you stop the alarm.
6. Tap **Weiter**, then add the action that runs your Shortcut **Morning TV** *(action name not confirmed)*.
7. Make it run without asking: **Sofort ausführen** and, if shown, **Beim Ausführen benachrichtigen** off *(not confirmed)*.
8. Tap **Fertig**.

Some users report that automations stopped running after iOS 26 updates. If it does not fire, run the Shortcut by hand to check the Shortcut itself, then recreate the automation.

---

# Android

The phone sends the same three calls, but without calendar events, because Android has no equivalent of steps 7 to 10. The appointments come from the app's own **Google calendar address** (`google_ical_url`, see the README), which the app fetches itself.

You need an automation app that can react to an alarm and send HTTP requests. Examples are Tasker and MacroDroid; this guide was **not tested** with either, and the menu names differ per app and per Android version, so no labels are given here.

**Trigger:** your alarm clock goes off (the app's "alarm" trigger). Optional condition: connected to `<HOME_WIFI>`.

**Actions, in this order:**

1. POST `http://<TV_IP>/sony/system`, header `X-Auth-PSK: <PSK>`, body
   `{"method":"getPowerStatus","id":1,"params":[],"version":"1.0"}`.
   If the reply contains `active`, set `KEEPON` = `true` and skip actions 2 and 3; otherwise `KEEPON` = `false`.
2. POST `http://<TV_IP>/sony/system`, same header, body
   `{"method":"setPowerStatus","id":1,"params":[{"status":true}],"version":"1.0"}`.
3. Wait 3 seconds, then repeat action 1 once per second until the reply contains `active`, for at most about 10 seconds after action 2.
4. POST `http://<TV_IP>/sony/appControl`, same header, body
   `{"method":"setActiveApp","id":1,"params":[{"uri":"com.sony.dtv.io.github.morningtv.io.github.morningtv.MainActivity","data":"{\"events\":[],\"keepOn\":KEEPON}"}],"version":"1.0"}`
   (replace `KEEPON` by `true` or `false`). The `data` must not be empty: any non-empty value makes the app treat this as a phone start. `{"events":[]}` means "no events from the phone", and the app then shows the Google calendar. `"keepOn":true` (TV was already on) opens the app without arming the standby timer.
5. Safety net, only if `KEEPON` is `false`: if the reply of action 4 does not contain `result`, POST to `/sony/system` with
   `{"method":"setPowerStatus","id":1,"params":[{"status":false}],"version":"1.0"}`.

Without a Google calendar address the app shows "no appointments" but still works as a weather and clock screen with the standby timer.

---

# Notes

- **Size limit (iPhone):** keep the payload under about **5 KB** (5.7 KB worked, 8.6 KB was dropped on the test TV). 15 events with normal titles stay well below that; step 5 of "Morning TV" switches the TV off again if it is exceeded. The payload is pretty-printed JSON, which is larger than a compact one: check it on a busy day.
- Events that started yesterday (e.g. a night shift) are not found by "Start Date is today".
- Not at home: the automation stops quietly. TV already on: the app opens over it and the TV stays on (`keepOn`).
- If the appointments are missing but the app opens, check the JSON that "MTV Events" outputs: it must be valid and list your events under `events`. Run "MTV Events" by hand to see it. The app also accepts `events` as a text that holds the JSON (this is how the Shortcuts app serialises a list), and `Ja`/`Yes`/`true`/`1` as true.
- Running "Morning TV" a second time while the TV is on tells the app the TV was already on (`keepOn`), which cancels the standby timer from the first run. The TV then stays on until you switch it off.
- The TV goes back to standby after `standby_minutes`. Pressing Home on the remote cancels that. Opening the app by hand never starts the timer.

## Troubleshooting

- **The Shortcut waits 8 seconds although the TV is on, and the app never opens:** the `id` field is a **Text** instead of a **Number** in one of the requests. The TV then answers `{"error":[5,"Illegal JSON"]}` to every call, so the Shortcut never sees `active` and the launch fails. In each *Get Contents of URL* action, set `id` to the type **Number** (value `1`). The same applies to the Android recipe: `"id":1`, not `"id":"1"`.
- **Nothing happens at all:** the Shortcut stops quietly if you are not on `<HOME_WIFI>` (step 1). Test with the phone on the home Wi-Fi. If you are, check the comparison: it must be **does not begin with** your prefix. **is not** with a shorter text stops on every network.
- **A preview opens and the Shortcut seems stuck:** a Quick Look or Show Result action is still in it. Delete it; it waits for you to dismiss it.
- **The app opens but shows "no appointments", and the TV never switches off:** the payload was not valid JSON, so the app read neither the events nor `keepOn`. The usual causes are doubled braces (`{{ ... }}`) from a hand-typed Text action, and a variable that ended up inside a key name (for example `Anna Beispiel (30. Geburtstag)allDay`). Build the payload with Dictionary actions only, as above.
- **Works with the TV off but not when it is on:** your Shortcut still stops when the TV is on. "MTV Wake" must output `true` in that case and "Morning TV" must go on.
- **The TV wakes but the app does not open, and the TV switches off again:** the launch call (step 4) was refused or the payload was too large; step 5 switches the TV off. Check that `<PSK>` is right (a wrong key gives `403 Forbidden` with an `auth_url` in the reply, also for `setActiveApp`) and the payload size.
- **Try the calls from a computer:** `scripts/tv.sh status` and `scripts/tv.sh launch <uri> <data>` use the same requests (key via `MTV_TV_PSK`).
