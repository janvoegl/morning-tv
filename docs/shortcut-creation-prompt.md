# Prompt to create the iPhone Shortcut

Paste this into Apple Intelligence / Siri's Shortcut creation, **after replacing the three placeholders** in the first block, or paste the key, address and Wi-Fi prefix into the three Text actions once the Shortcut exists. Never share a Shortcut (iCloud link or file) that contains your real key: the file holds it in plain text.

Not every assistant builds a Shortcut correctly from text. If a request type is wrong, fix it by hand; the usual mistakes are `id` as Text instead of Number and `status` as Text instead of Boolean. The manual version of the same steps is in [shortcut.md](shortcut.md).

```
Create a new Shortcut named "Morning TV" with exactly these actions.
Types matter: `id` must be a NUMBER, not text. A text id makes the TV reject the request.

# Settings (edit once after creation)
Text "PASTE_PSK_HERE"              -> Set Variable PSK
Text "PASTE_TV_IP_HERE"            -> Set Variable TV
Text "PASTE_HOME_WIFI_PREFIX_HERE" -> Set Variable WIFI_PREFIX

# 1. Only at home
Get Network Details (Wi-Fi, Network Name) -> WifiName
If WifiName does not begin with WIFI_PREFIX:
    Stop This Shortcut
End If

# 2. Is the TV already on?
Get Contents of URL
    URL: http://[TV]/sony/system
    Method: POST
    Header: X-Auth-PSK = [PSK]
    JSON body: { method: "getPowerStatus" (text), id: 1 (NUMBER), version: "1.0" (text), params: [] (empty array) }
    -> PowerReply
If PowerReply contains "active":
    Text "true"  -> Set Variable TVOn
Otherwise:
    Text "false" -> Set Variable TVOn
End If

# 3. Wake the TV only if it was off, then wait until it reports "active" (at most ~10 s)
If TVOn is "false":
    Get Contents of URL
        URL: http://[TV]/sony/system, Method: POST, Header X-Auth-PSK = [PSK]
        JSON body: { method: "setPowerStatus", id: 1 (NUMBER), version: "1.0",
                     params: [ { status: true (BOOLEAN) } ] }
    Text "false" -> Set Variable Ready
    Wait 3 seconds
    Repeat 8 times:
        If Ready is "false":
            Get Contents of URL (same request as in step 2) -> CheckReply
            If CheckReply contains "active":
                Text "true" -> Set Variable Ready
            Otherwise:
                Wait 1 second
            End If
        End If
    End Repeat
End If

# 4. Today's appointments
Find Calendar Events where Start Date is today, sorted by Start Date ascending, limit 15
Repeat with each event:
    Format Date (event Start Date) as ISO 8601 with time -> StartText
    Format Date (event End Date)   as ISO 8601 with time -> EndText
    Text: {"title": "[event Title]", "start": "[StartText]", "end": "[EndText]", "allDay": "[event Is All Day]"}
    Add to Variable Events
End Repeat
Combine Text (Events) with custom separator "," -> EventList
Text: {"events":[EventList],"keepOn":TVOn}   -> Set Variable Payload
(TVOn is inserted as the text true or false, WITHOUT quotes.)

# 5. Start the app
Get Contents of URL
    URL: http://[TV]/sony/appControl
    Method: POST
    Header: X-Auth-PSK = [PSK]
    JSON body: { method: "setActiveApp", id: 1 (NUMBER), version: "1.0",
                 params: [ { uri: "com.sony.dtv.io.github.morningtv.io.github.morningtv.MainActivity",
                             data: [Payload] (text) } ] }
    -> LaunchReply

# 6. Safety net: if the launch failed and the TV was off, switch it off again
If TVOn is "false":
    If LaunchReply does not contain "result":
        Get Contents of URL
            URL: http://[TV]/sony/system, Method: POST, Header X-Auth-PSK = [PSK]
            JSON body: { method: "setPowerStatus", id: 1 (NUMBER), version: "1.0",
                         params: [ { status: false (BOOLEAN) } ] }
    End If
End If
```

After it is created, check:
- every `id` field is the type **Number**, `status` is **Boolean**, `params` is an **array** with one dictionary;
- the payload text ends with `"keepOn":` followed directly by the variable, without quotes;
- the header of all requests uses the `PSK` variable, not typed text.
