# Prompts to create the iPhone Shortcuts

Paste each prompt into Apple Intelligence / Siri's Shortcut creation, one Shortcut at a time, **in this order**: "MTV Wake", "MTV Events", "Morning TV". Run each one by hand before you build the next. Replace the placeholders (`PASTE_..._HERE`) in the Text actions once the Shortcut exists. Never share a Shortcut (iCloud link or file) that contains your real key: the file holds it in plain text.

Not every assistant builds a Shortcut correctly from text. If something is wrong, fix it by hand; the usual mistakes are `id` as Text instead of Number, `status` as Text instead of Boolean, typed curly braces in a Text action (they end up doubled, `{{...}}`), and a variable inside a Dictionary key name. The manual version of the same steps is in [shortcut.md](shortcut.md).

## 1. "MTV Wake": wake the TV, output whether it was already on

```
Create a new Shortcut named "MTV Wake". Types matter: `id` must be a NUMBER, not text. A text id makes the TV reject the request. Do NOT add Quick Look, Show Result, Show Alert or any other action that waits for me.

Settings (edit once after creation):
Text "PASTE_PSK_HERE"   -> Set Variable PSK
Text "PASTE_TV_IP_HERE" -> Set Variable TV

1. Get Contents of URL
     URL: http://[TV]/sony/system, Method: POST, Header: X-Auth-PSK = [PSK]
     JSON body: method = "getPowerStatus" (Text), id = 1 (NUMBER), version = "1.0" (Text), params = empty Array
   -> PowerReply
2. If PowerReply contains "active":
     Text "true" -> Set Variable TVOn
   Otherwise:
     Text "false" -> Set Variable TVOn
   End If
3. If TVOn is "false":
     Get Contents of URL
       URL: http://[TV]/sony/system, Method: POST, Header: X-Auth-PSK = [PSK]
       JSON body: method = "setPowerStatus", id = 1 (NUMBER), version = "1.0",
                  params = Array with one Dictionary: status = true (BOOLEAN)
     Text "false" -> Set Variable Ready
     Wait 3 seconds
     Repeat 7 times:
       If Ready is "false":
         Get Contents of URL (same request as step 1) -> CheckReply
         If CheckReply contains "active": Text "true" -> Set Variable Ready
         Otherwise: Wait 1 second
         End If
       End If
     End Repeat
   End If
4. Stop and Output TVOn. Output only the text true or false.
```

Test: with the TV in standby it wakes and outputs `false`; run it again, it outputs `true` and does nothing else.

## 2. "MTV Events": today's appointments as the payload text

```
Create a new Shortcut named "MTV Events". It takes the text "true" or "false" as Shortcut Input (this is whether the TV is already on). It collects today's calendar events and ends with the payload as text. Use exactly these actions. Do NOT add Quick Look, Show Result, Show Alert or any other action that waits for me. Do not type any curly braces anywhere.

1. Find Calendar Events where Start Date is today, sorted by Start Date ascending, limit 15.
2. Repeat with Each (Calendar Events):
   a. Format Date (Repeat Item > Start Date), format ISO 8601, include time -> name "Start".
   b. Format Date (Repeat Item > End Date), format ISO 8601, include time -> name "End".
   c. If (Repeat Item > Is All Day) is Yes: Text "true" -> Set Variable AllDayText. Otherwise: Text "false" -> Set Variable AllDayText. End If.
   d. Dictionary, all values of type Text, exactly these keys:
        title = Repeat Item > Title
        start = Start
        end = End
        allDay = AllDayText
      Key names are exactly title, start, end, allDay. A variable may only be in a value, never in a key. This Dictionary must be the LAST action inside the loop.
   End Repeat.
3. Dictionary with exactly two keys:
     events = Repeat Results (type Array)
     keepOn = Shortcut Input (type Text)
4. Get Text from that Dictionary (convert it to JSON text).
5. Stop and Output that text. Output only that text and nothing else.
```

Test: run it on its own. The output is one JSON text with your events under `events`; `keepOn` is empty without input.

## 3. "Morning TV": the one the alarm runs

```
Create a new Shortcut named "Morning TV". Do NOT add Quick Look, Show Result, Show Alert or any other action that waits for me. Do not type any curly braces anywhere. `id` must be a NUMBER, `status` a BOOLEAN.

Settings (edit once after creation):
Text "PASTE_PSK_HERE"             -> Set Variable PSK
Text "PASTE_TV_IP_HERE"           -> Set Variable TV
Text "PASTE_HOME_WIFI_PREFIX_HERE" -> Set Variable HOME_WIFI

1. Get Network Details (Wi-Fi, Network Name) -> WifiName
   If WifiName does not begin with HOME_WIFI: Stop This Shortcut. End If.
2. Run Shortcut "MTV Wake" (no input) -> TVOn
3. Run Shortcut "MTV Events" with input TVOn -> Payload
4. Get Contents of URL
     URL: http://[TV]/sony/appControl, Method: POST, Header: X-Auth-PSK = [PSK]
     JSON body: method = "setActiveApp" (Text), id = 1 (NUMBER), version = "1.0" (Text),
                params = Array with one Dictionary:
                   uri = "com.sony.dtv.io.github.morningtv.io.github.morningtv.MainActivity" (Text)
                   data = Payload (Text)
   -> LaunchReply
5. If TVOn is "false":
     If LaunchReply does not contain "result":
       Get Contents of URL
         URL: http://[TV]/sony/system, Method: POST, Header: X-Auth-PSK = [PSK]
         JSON body: method = "setPowerStatus", id = 1 (NUMBER), version = "1.0",
                    params = Array with one Dictionary: status = false (BOOLEAN)
     End If
   End If
```

Test: run it by hand twice, with the TV in standby and with it on.

After they are created, check:
- the names are exactly `MTV Wake`, `MTV Events`, `Morning TV`;
- every `id` is **Number**, `status` is **Boolean**, `params` is an **Array** with one dictionary;
- the Wi-Fi check says **does not begin with**, with a prefix both of your networks share;
- there is no Quick Look or Show Result left in any of them.
