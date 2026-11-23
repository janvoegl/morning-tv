package io.github.morningtv.text

import java.util.Locale

/** Texts of the on-TV settings menu. */
data class SettingsTexts(
    val title: String,
    val keysHint: String,
    val name: String,
    val place: String,
    val standby: String,
    val language: String,
    val layout: String,
    val calendarStyle: String,
    val google: String,
    val weatherLink: String,
    val auto: String,
    val german: String,
    val english: String,
    val timeGraph: String,
    val classic: String,
    val timelineStyle: String,
    val listStyle: String,
    val on: String,
    val off: String,
    val notSetUp: String,
    val notSet: String,
    val minutes: (Int) -> String,
    val namePrompt: String,
    val placePrompt: String,
    val searching: String,
    val noResults: String,
    val searchFailed: String,
    val pickPlace: String,
    val ok: String,
    val cancel: String,
    val openHint: String,
) {
    companion object {
        val EN = SettingsTexts(
            title = "Settings", keysHint = "Up/Down: select   Left/Right or OK: change   Back: close",
            name = "Name in greeting", place = "Place (weather)", standby = "TV off after", language = "Language",
            layout = "Layout", calendarStyle = "Calendar style (classic layout)", google = "Google Calendar",
            weatherLink = "Weather station (current temperature)", auto = "Automatic", german = "Deutsch", english = "English",
            timeGraph = "Time graph", classic = "Classic", timelineStyle = "Timeline", listStyle = "List",
            on = "On", off = "Off", notSetUp = "Not set up (install.sh)", notSet = "Not set",
            minutes = { "$it min" }, namePrompt = "Name for the greeting (empty = none)", placePrompt = "Search for a place",
            searching = "Searching…", noResults = "No place found", searchFailed = "Search failed (no network?)",
            pickPlace = "Choose a place", ok = "OK", cancel = "Cancel", openHint = "OK: Settings",
        )
        val DE = SettingsTexts(
            title = "Einstellungen", keysHint = "Hoch/Runter: wählen   Links/Rechts oder OK: ändern   Zurück: schließen",
            name = "Name in der Begrüßung", place = "Ort (Wetter)", standby = "TV aus nach", language = "Sprache",
            layout = "Layout", calendarStyle = "Kalenderansicht (klassisches Layout)", google = "Google Kalender",
            weatherLink = "Wetterstation (aktuelle Temperatur)", auto = "Automatisch", german = "Deutsch", english = "English",
            timeGraph = "Zeitgraph", classic = "Klassisch", timelineStyle = "Zeitleiste", listStyle = "Liste",
            on = "An", off = "Aus", notSetUp = "Nicht eingerichtet (install.sh)", notSet = "Nicht gesetzt",
            minutes = { "$it Min." }, namePrompt = "Name für die Begrüßung (leer = keiner)", placePrompt = "Ort suchen",
            searching = "Suche…", noResults = "Kein Ort gefunden", searchFailed = "Suche fehlgeschlagen (kein Netz?)",
            pickPlace = "Ort wählen", ok = "OK", cancel = "Abbrechen", openHint = "OK: Einstellungen",
        )

        fun forTexts(t: Texts): SettingsTexts = if (t.locale.language == Locale.GERMAN.language) DE else EN
    }
}
