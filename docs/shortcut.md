# iPhone Shortcut

The Shortcut runs when your chosen alarm goes off. It checks that you are at home and the TV is off, wakes the TV, and starts Morning TV with today's appointments.

Placeholders — replace them with your own values:

| Placeholder | What it is |
|---|---|
| `<TV_IP>` | the TV's IP address (`tv_host` in your config) |
| `<PSK>` | the TV's Pre-Shared Key (`tv_psk`) |
| `<HOME_WIFI>` | your home Wi-Fi name (`home_wifi`) |

The app URI is the same for everyone:
`com.sony.dtv.io.github.morningtv.io.github.morningtv.MainActivity`

## 1. Create the automation

1. Shortcuts app → **Automation** → **+** → **Alarm**.
2. Choose **Goes Off**, then **Existing** and pick your workday alarm.
3. Choose **Run Immediately** and turn **Notify When Run** off.
4. Create a **New Blank Automation** and add the actions below.

## 2. Actions

Every TV request is a **Get Contents of URL** action with:
- URL `http://<TV_IP>/sony/system` (or `/sony/appControl` where noted)
- Method **POST**
- Header `X-Auth-PSK` = `<PSK>`
- Request Body **JSON** with the fields listed

**Am I home?**

1. **Get Network Details** → Wi-Fi → **Network Name**.
2. **If** *Network Details* **is not** `<HOME_WIFI>` → **Stop This Shortcut**. **End If**.

**Is the TV already on?**

3. Get Contents of URL (`/sony/system`), JSON body:
   `method` (Text) = `getPowerStatus`, `id` (Number) = `1`, `version` (Text) = `1.0`, `params` (Array) = empty.
4. **If** *Contents of URL* **contains** `active` → **Stop This Shortcut**. **End If**.

**Wake the TV**

5. Get Contents of URL (`/sony/system`), JSON body:
   `method` = `setPowerStatus`, `id` = `1`, `version` = `1.0`, `params` (Array) → one **Dictionary** item with `status` (Boolean) = **true**.
6. **Wait** 8 seconds. (The TV reports "on" after about 4 s; the extra time lets its home screen settle.)

**Collect today's appointments**

7. **Find Calendar Events** where **Start Date** **is today**, sort by **Start Date**, **Limit** 25.
8. **Repeat with Each** *Calendar Events*:
   - **Format Date** *Repeat Item → Start Date*, format **ISO 8601**, include time → rename result to *Start*.
   - **Format Date** *Repeat Item → End Date*, format **ISO 8601**, include time → rename to *End*.
   - **Dictionary**: `title` (Text) = *Repeat Item → Title*, `start` (Text) = *Start*, `end` (Text) = *End*, `allDay` (Text) = *Repeat Item → Is All Day*.
   - **Add to Variable** `Events`.
   - **End Repeat**.
9. **Combine Text** *Events* with **Custom** separator `,`.
10. **Text**: `{"events":[` *Combined Text* `]}` → rename to *Payload*.

**Start Morning TV**

11. Get Contents of URL (`/sony/appControl`), JSON body:
    `method` = `setActiveApp`, `id` = `1`, `version` = `1.0`, `params` (Array) → one **Dictionary** item with
    `uri` (Text) = `com.sony.dtv.io.github.morningtv.io.github.morningtv.MainActivity` and `data` (Text) = *Payload*.

## Notes

- **Size limit:** the TV drops launch data over roughly 6 KB, so keep the limit in step 7 at about 25 events.
- Events that started yesterday (e.g. a night shift) are not found by "Start Date is today".
- Not at home or TV already on → the Shortcut stops quietly.
- If the appointments are missing but the app opens, check step 10: the text must be valid JSON. Opening the Shortcut by hand and adding **Quick Look** on *Payload* shows what is sent.
- The TV goes back to standby after `standby_minutes`. Pressing Home on the remote cancels that.
