# Phone automation

When your alarm rings, the phone wakes the TV and starts Morning TV. Two variants:

- **iPhone:** a Shortcut named "Morning TV" does the work and a personal automation runs it when an alarm rings. It also sends today's events from the iPhone calendar.
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

## 1. The Shortcut "Morning TV"

Create a new Shortcut named **Morning TV** with these actions.

**Am I home?**

1. **Get Network Details** → Wi-Fi → **Network Name**.
2. **If** *Network Details* **is not** `<HOME_WIFI>` → **Stop This Shortcut**. **End If**.

**Is the TV already on?**

3. Get Contents of URL (`/sony/system`), JSON body:
   `method` (Text) = `getPowerStatus`, `id` (Number) = `1`, `version` (Text) = `1.0`, `params` (Array) = empty.
4. **If** *Contents of URL* **contains** `active`: **Text** `true` → **Set Variable** *TVOn*. **Otherwise**: **Text** `false` → **Set Variable** *TVOn*. **End If**.

**Wake the TV (only if it was off)**

5. **If** *TVOn* **is** `false`:
   - Get Contents of URL (`/sony/system`), JSON body:
     `method` = `setPowerStatus`, `id` (Number) = `1`, `version` = `1.0`, `params` (Array) → one **Dictionary** item with `status` (Boolean) = **true**.
   - **Text** `false` → **Set Variable** *Ready*. **Wait** 3 seconds.
   - **Repeat** 7 times (that is until about 10 seconds after the wake call): **If** *Ready* **is** `false`: Get Contents of URL (`getPowerStatus` as in step 3); **If** it **contains** `active`: **Text** `true` → **Set Variable** *Ready*; **Otherwise**: **Wait** 1 second. **End If**. **End If**. **End Repeat**.
   - **End If**.
   (The TV reports "on" after about 4 s. The loop continues as soon as it does, instead of always waiting 8 s. If it never reports "on", the Shortcut still tries to launch after 10 s and the safety net in step 12 applies.)

**Collect today's appointments**

7. **Find Calendar Events** where **Start Date** **is today**, sort by **Start Date**, **Limit** 15.
8. **Repeat with Each** *Calendar Events*:
   - **Format Date** *Repeat Item → Start Date*, format **ISO 8601**, include time → rename result to *Start*.
   - **Format Date** *Repeat Item → End Date*, format **ISO 8601**, include time → rename to *End*.
   - **Dictionary**: `title` (Text) = *Repeat Item → Title*, `start` (Text) = *Start*, `end` (Text) = *End*, `allDay` (Text) = *Repeat Item → Is All Day*.
   - **Add to Variable** `Events`.
   - **End Repeat**.
9. **Combine Text** *Events* with **Custom** separator `,`.
10. **Text**: `{"events":[` *Combined Text* `],"keepOn":` *TVOn* `}` → rename to *Payload*.
    (*TVOn* is the text `true` or `false`, so this is valid JSON. With `"keepOn":true` the app opens over the running TV and never switches it off.)

**Start Morning TV**

11. Get Contents of URL (`/sony/appControl`), JSON body:
    `method` = `setActiveApp`, `id` (Number) = `1`, `version` = `1.0`, `params` (Array) → one **Dictionary** item with
    `uri` (Text) = `com.sony.dtv.io.github.morningtv.io.github.morningtv.MainActivity` and `data` (Text) = *Payload*.

**Safety net: don't leave the TV on**

12. **If** *TVOn* **is** `false`: **If** *Contents of URL* (from step 11) **does not contain** `result` → Get Contents of URL (`/sony/system`), JSON body:
    `method` = `setPowerStatus`, `id` (Number) = `1`, `version` = `1.0`, `params` (Array) → one **Dictionary** with `status` (Boolean) = **false**. **End If**. **End If**.
    (If the app could not be started, e.g. because the payload was too large or the key was wrong, the TV would otherwise stay on all day. A TV that was already on is never switched off.)

**Test it by hand first**, twice: with the TV in standby (it should wake and show the screen, and switch off after `standby_minutes`) and with the TV on (the screen opens over what is running and the TV stays on).

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

- **Size limit (iPhone):** keep the payload under about **5 KB** (5.7 KB worked, 8.6 KB was dropped on the test TV). 15 events with normal titles stay well below that; step 12 switches the TV off again if it is exceeded.
- Events that started yesterday (e.g. a night shift) are not found by "Start Date is today".
- Not at home: the automation stops quietly. TV already on: the app opens over it and the TV stays on (`keepOn`).
- If the appointments are missing but the app opens, check step 10: the text must be valid JSON. Opening the Shortcut by hand and adding **Quick Look** on *Payload* shows what is sent.
- The TV goes back to standby after `standby_minutes`. Pressing Home on the remote cancels that. Opening the app by hand never starts the timer.

## Troubleshooting

- **The Shortcut waits 8 seconds although the TV is on, and the app never opens:** the `id` field is a **Text** instead of a **Number** in one of the requests. The TV then answers `{"error":[5,"Illegal JSON"]}` to every call, so the Shortcut never sees `active` and the launch fails. In each *Get Contents of URL* action, set `id` to the type **Number** (value `1`). The same applies to the Android recipe: `"id":1`, not `"id":"1"`.
- **Nothing happens at all:** the Shortcut stops quietly if you are not on `<HOME_WIFI>` (step 2). Test with the phone on the home Wi-Fi.
- **Works with the TV off but not when it is on:** your Shortcut still has the old step 4 ("contains `active` → Stop This Shortcut"). Replace it with the *TVOn* version above.
- **The TV wakes but the app does not open, and the TV switches off again:** the launch call (step 11) was refused or the payload was too large; step 12 switches the TV off. Check that `<PSK>` is right (a wrong key gives `403 Forbidden` with an `auth_url` in the reply, also for `setActiveApp`) and the payload size.
- **Try the calls from a computer:** `scripts/tv.sh status` and `scripts/tv.sh launch <uri> <data>` use the same requests (key via `MTV_TV_PSK`).
