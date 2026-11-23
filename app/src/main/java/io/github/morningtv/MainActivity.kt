package io.github.morningtv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.widget.FrameLayout
import io.github.morningtv.text.SettingsTexts
import io.github.morningtv.ui.SettingsOverlay
import android.view.View
import android.widget.ImageView
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import io.github.morningtv.calendar.Agenda
import io.github.morningtv.calendar.decodeCache
import io.github.morningtv.calendar.icalAgenda
import io.github.morningtv.calendar.mergeAgendas
import io.github.morningtv.calendar.parseIcal
import io.github.morningtv.calendar.encodeCache
import io.github.morningtv.calendar.parseAgenda
import io.github.morningtv.config.AppConfig
import io.github.morningtv.config.CalendarStyle
import io.github.morningtv.config.ScreenLayout
import io.github.morningtv.config.ConfigStore
import io.github.morningtv.sony.SonyApi
import io.github.morningtv.standby.StandbyTimer
import io.github.morningtv.standby.countdownLabel
import io.github.morningtv.text.Texts
import io.github.morningtv.transport.POST_BODY_EXTRA
import io.github.morningtv.transport.extractPayload
import io.github.morningtv.ui.DatedAgenda
import io.github.morningtv.ui.agendaForToday
import io.github.morningtv.ui.AgendaView
import io.github.morningtv.ui.GraphData
import io.github.morningtv.ui.GraphEvent
import io.github.morningtv.ui.GraphHour
import io.github.morningtv.ui.TimeGraphView
import io.github.morningtv.ui.TimelineEntry
import io.github.morningtv.ui.TimelineEvent
import io.github.morningtv.ui.TimelineView
import io.github.morningtv.ui.agendaRows
import io.github.morningtv.ui.agendaView
import io.github.morningtv.ui.buildTimeline
import io.github.morningtv.ui.graphRange
import io.github.morningtv.ui.isFreshLaunch
import io.github.morningtv.ui.greeting
import io.github.morningtv.ui.rainLine
import io.github.morningtv.weather.WeatherClient
import io.github.morningtv.weather.WeatherLinkClient
import io.github.morningtv.weather.httpGet
import io.github.morningtv.weather.WeatherSummary
import io.github.morningtv.weather.WeatherTheme
import io.github.morningtv.weather.gradientColors
import io.github.morningtv.weather.weatherIcon
import io.github.morningtv.weather.weatherTheme
import io.github.morningtv.weather.weatherLabel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * The morning screen. Started by the iPhone Shortcut (events in the PostBody extra → standby
 * timer armed) or by hand from the launcher (no timer). Leaving the app cancels the timer.
 */
class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val timer = StandbyTimer { System.currentTimeMillis() }
    private lateinit var config: AppConfig
    private var agenda: DatedAgenda? = null
    private var googleAgenda: DatedAgenda? = null
    private var standbyRequested = false
    private var weather: WeatherSummary? = null
    private var weatherFailed = false
    /** Today's agenda from an earlier launch (hand start); null once this launch brought its own. */
    private var cachedStamp: String? = null
    private var weatherFallback = ""
    private var statusOverride: String? = null
    private var hiddenCount = 0
    private val timelineMode get() = config.layout == ScreenLayout.TIMELINE
    /** Follows the TV's system language (German or English). */
    private val t: Texts get() = Texts.forSetting(config.language, resources.configuration.locales[0])
    private var settings: SettingsOverlay? = null
    private var pausedRemainingMs: Long? = null

    private val tick = object : Runnable {
        override fun run() {
            renderClock()
            renderCountdown()
            if (timelineMode) renderAgenda()
            if (timer.isDue() && !standbyRequested) goToStandby()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        config = loadConfig()
        settings = SettingsOverlay(
            activity = this,
            parent = findViewById<FrameLayout>(R.id.root),
            store = ConfigStore(getSharedPreferences(ConfigStore.PREFS_NAME, Context.MODE_PRIVATE)),
            config = { config },
            texts = { SettingsTexts.forTexts(t) },
            language = { t.locale.language },
            onChanged = { applyConfig() },
            onClose = { resumeTimer() },
        )
        if (isFreshLaunch(intent.flags, restored = savedInstanceState != null)) handleLaunch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleLaunch(intent)) {
            loadGoogle()
            renderAgenda()
            renderCountdown()
        }
    }

    override fun onStart() {
        super.onStart()
        applyConfig()
        handler.post(tick)
    }

    /** Re-reads the config and redraws everything (also after a change in the settings menu). */
    private fun applyConfig() {
        config = loadConfig()
        renderGreeting()
        if (!config.googleEnabled || config.googleIcalUrl.isEmpty()) googleAgenda = null
        renderAgenda()
        renderClock()
        renderCountdown()
        loadWeather()
        loadGoogle()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val s = settings
        if (s != null && !s.isShown && keyCode in OPEN_SETTINGS_KEYS) {
            pausedRemainingMs = timer.remainingMs()
            timer.cancel()
            s.show()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onBackPressed() {
        val s = settings
        if (s != null && s.isShown) s.close() else super.onBackPressed()
    }

    /** Counts down again from where the settings menu paused the standby timer. */
    private fun resumeTimer() {
        pausedRemainingMs?.let { timer.armForMs(it) }
        pausedRemainingMs = null
        renderCountdown()
    }

    override fun onStop() {
        super.onStop()
        pausedRemainingMs = null
        if (settings?.isShown == true) settings?.close()
        handler.removeCallbacks(tick)
        timer.cancel()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /**
     * A launch with a non-empty PostBody is a Shortcut start: keep its events and arm the timer.
     * Returns false for a launch by hand (nothing changes).
     */
    private fun handleLaunch(intent: Intent): Boolean {
        val payload = extractPayload(intent.getByteArrayExtra(POST_BODY_EXTRA)) ?: return false
        val today = LocalDate.now(config.timezone)
        val parsed = parseAgenda(payload, today, config.timezone, t) ?: Agenda(emptyList(), emptyList())
        agenda = DatedAgenda(today, parsed)
        cachedStamp = null
        stateStore().edit().putString(CACHE_KEY, encodeCache(today, ZonedDateTime.now(config.timezone).format(HHMM), parsed)).apply()
        standbyRequested = false
        timer.arm(config.standbyMinutes)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        statusOverride = null
        text(R.id.status).text = ""
        return true
    }

    private fun stateStore() = getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)

    private fun loadConfig(): AppConfig =
        ConfigStore(getSharedPreferences(ConfigStore.PREFS_NAME, Context.MODE_PRIVATE)).load()

    /** Fetches today's events from the Google calendar (secret iCal address), if one is configured. */
    private fun loadGoogle() {
        val url = config.googleIcalUrl
        if (url.isEmpty() || !config.googleEnabled) return
        val zone = config.timezone
        val texts = t
        Thread {
            var feed: String? = null
            repeat(3) { attempt ->
                if (feed == null) {
                    if (attempt > 0) Thread.sleep(5_000)
                    feed = runCatching { httpGet(url) }.getOrNull()
                }
            }
            val text = feed ?: return@Thread
            val today = LocalDate.now(zone)
            val result = icalAgenda(parseIcal(text, today, zone), today, zone, texts)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                googleAgenda = DatedAgenda(today, result)
                val merged = mergeAgendas(agendaForToday(agenda, today), result) ?: result
                stateStore().edit().putString(CACHE_KEY, encodeCache(today, ZonedDateTime.now(zone).format(HHMM), merged)).apply()
                renderAgenda()
            }
        }.start()
    }

    private fun loadWeather() {
        val cfg = config
        if (cfg.latitude.isNaN() || cfg.longitude.isNaN()) {
            weather = null
            showWeather(t.weatherNotSet)
            return
        }
        text(R.id.weatherNow).text = "…"
        Thread {
            var w = WeatherClient().load(cfg, LocalTime.now(cfg.timezone).hour)
            // Optional local station: only the current temperature comes from it; Open-Meteo stays the fallback.
            if (w != null && cfg.weatherLinkEnabled && cfg.weatherLinkHost.isNotEmpty()) {
                WeatherLinkClient(cfg.weatherLinkHost).outdoorTempC()?.let { w = w!!.copy(nowTempC = it) }
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                weather = w
                showWeather(t.weatherUnavailable)
            }
        }.start()
    }

    private fun showWeather(fallback: String) {
        weatherFallback = fallback
        val w = weather
        val place = config.locationName.trim()
        text(R.id.weatherTitle).text =
            if (place.isEmpty()) t.weatherTitle else "${t.weatherTitle} · ${place.uppercase(Locale.ROOT)}"
        val colors = gradientColors(if (w == null) WeatherTheme.CLEAR else weatherTheme(w.nowCode))
        findViewById<View>(R.id.root).background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
        if (timelineMode) { renderAgenda(); return }
        val hours = findViewById<LinearLayout>(R.id.hours)
        hours.removeAllViews()
        if (w == null) {
            text(R.id.weatherNow).text = fallback
            text(R.id.weatherLabel).text = ""
            text(R.id.weatherDetail).text = ""
            text(R.id.weatherDetailWide).text = ""
            return
        }
        text(R.id.weatherNow).text = "${weatherIcon(w.nowCode, w.isDayAt(LocalTime.now(config.timezone).hour))} ${temp(w.nowTempC)}"
        text(R.id.weatherLabel).text = weatherLabel(w.nowCode, t)
        val detail = "${t.highLow(w.highC, w.lowC).replace("-", "−")}\n${rainLine(w, t)}"
        text(R.id.weatherDetail).text = detail
        text(R.id.weatherDetailWide).text = detail
        val d = resources.displayMetrics.density
        val wide = wideLayout
        for (h in w.hours) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setBackgroundResource(R.drawable.tile_bg)
                val pad = ((if (wide) 14 else 8) * d).toInt()
                setPadding(0, pad, 0, pad)
            }
            fun line(s: String, size: Float, color: Int) = tile.addView(TextView(this).apply {
                text = s; textSize = size; setTextColor(color); gravity = Gravity.CENTER
            })
            line("%02d".format(h.hour), if (wide) 19f else 14f, 0xCCFFFFFF.toInt())
            line(temp(h.tempC), if (wide) 24f else 17f, 0xFFFFFFFF.toInt())
            line(weatherIcon(h.code, h.isDay), if (wide) 24f else 17f, 0xFFFFFFFF.toInt())
            hours.addView(tile, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (2.5f * d).toInt(); marginEnd = (2.5f * d).toInt()
            })
        }
    }

    /** True when the calendar card is not shown: the weather card then uses the full width. */
    private var wideLayout = false

    private fun currentAgenda(): Pair<Agenda?, String?> {
        val today = LocalDate.now(config.timezone)
        mergeAgendas(agendaForToday(agenda, today), agendaForToday(googleAgenda, today))?.let { return it to null }
        val cached = decodeCache(stateStore().getString(CACHE_KEY, null), today)
        return cached?.agenda to cached?.receivedAt
    }

    private fun renderAgenda() {
        val (data, stamp) = currentAgenda()
        cachedStamp = stamp
        findViewById<View>(R.id.classicRow).visibility = if (timelineMode) View.GONE else View.VISIBLE
        findViewById<View>(R.id.graphCard).visibility = if (timelineMode) View.VISIBLE else View.GONE
        if (timelineMode) {
            findViewById<View>(R.id.pill).visibility = View.GONE
            renderTimeline(data, stamp)
            return
        }
        val view = agendaView(data, stamp)
        val card = view is AgendaView.Card
        val wasWide = wideLayout
        wideLayout = !card
        findViewById<View>(R.id.calendarCard).visibility = if (card) View.VISIBLE else View.GONE
        findViewById<View>(R.id.pill).visibility = if (card) View.GONE else View.VISIBLE
        findViewById<View>(R.id.weatherDetail).visibility = if (card) View.VISIBLE else View.GONE
        findViewById<View>(R.id.weatherDetailWide).visibility = if (card) View.GONE else View.VISIBLE
        text(R.id.weatherNow).textSize = if (card) 46f else 60f
        text(R.id.status).text = stamp?.let { t.asOf(it) } ?: ""
        when (view) {
            is AgendaView.Card -> renderCard(view.agenda)
            is AgendaView.Single -> renderPill(R.drawable.ic_calendar, view.item.timeLabel.ifEmpty { t.allDay }, view.item.title)
            is AgendaView.Message -> when (view.kind) {
                AgendaView.Kind.NO_APPOINTMENTS -> renderPill(R.drawable.ic_calendar_check, "", t.noAppointments)
                AgendaView.Kind.WAITING_FOR_ALARM -> renderPill(R.drawable.ic_bell, "", t.sentWithAlarm)
            }
        }
        if (wasWide != wideLayout && weather != null) showWeather(t.weatherUnavailable)
    }

    /** The time-graph layout: weather header, graph with weather and appointments, footer message. */
    private fun renderTimeline(agenda: Agenda?, stamp: String?) {
        val now = ZonedDateTime.now(config.timezone)
        val nowMin = now.hour * 60 + now.minute
        val w = weather
        val place = config.locationName.trim()
        if (w == null) {
            text(R.id.gNow).text = weatherFallback
            text(R.id.gLabel).text = ""
            text(R.id.gDetail).text = ""
        } else {
            text(R.id.gNow).text = "${weatherIcon(w.nowCode, w.isDayAt(now.hour))} ${temp(w.nowTempC)}"
            text(R.id.gLabel).text = weatherLabel(w.nowCode, t)
            text(R.id.gDetail).text = "${t.highLow(w.highC, w.lowC).replace("-", "−")} · ${rainLine(w, t)}" + if (place.isEmpty()) "" else "\n$place"
        }
        val d = resources.displayMetrics.density
        val chips = findViewById<LinearLayout>(R.id.gChips)
        chips.removeAllViews()
        for (item in (agenda?.allDay ?: emptyList()).take(2)) {
            chips.addView(TextView(this).apply {
                text = item.title; textSize = 13f; setTextColor(0xFFFFFFFF.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD)
                setBackgroundResource(R.drawable.chip_bg); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding((10 * d).toInt(), (3 * d).toInt(), (10 * d).toInt(), (3 * d).toInt())
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = (8 * d).toInt()
            })
        }
        val hours = w?.allHours.orEmpty().map { GraphHour(it.hour, it.tempC, weatherIcon(it.code, it.isDay)) }
        val byHour = hours.associateBy { it.hour }
        val palette = intArrayOf(0xFF7FD8FF.toInt(), 0xFF9CF08C.toInt(), 0xFFFF9BDB.toInt(), 0xFFFFC27A.toInt(), 0xFFC9A8FF.toInt(), 0xFF7FFFE0.toInt(), 0xFFFFA0A0.toInt())
        fun hm(min: Int) = "%02d:%02d".format(min / 60 % 24, min % 60)
        val timed = agenda?.timed.orEmpty()
        val events = timed.mapIndexed { i, e ->
            val h = byHour[e.startMin / 60]
            GraphEvent(e.title, "${hm(e.startMin)} – ${hm(e.endMin)}", e.startMin, e.endMin, palette[i % palette.size],
                h?.let { "${it.icon} ${temp(it.tempC)}" }.orEmpty())
        }
        val range = graphRange(nowMin, events.map { io.github.morningtv.ui.TimelineEvent(it.startMin, it.endMin, 0) })
        findViewById<TimeGraphView>(R.id.graph).apply {
            onHidden = { n -> hiddenCount = n; updateStatus() }
            set(GraphData(range, hours, events, nowMin, hm(nowMin)))
        }
        statusIcon = when {
            agenda == null -> R.drawable.ic_bell
            agenda.timed.isEmpty() && agenda.allDay.isEmpty() -> R.drawable.ic_calendar_check
            else -> 0
        }
        statusBase = when {
            agenda == null -> t.sentWithAlarm
            agenda.timed.isEmpty() && agenda.allDay.isEmpty() -> t.noAppointments
            else -> stamp?.let { t.asOf(it) }.orEmpty()
        }
        updateStatus()
    }

    private var statusBase = ""
    private var statusIcon = 0

    private fun updateStatus() {
        val tv = text(R.id.status)
        val override = statusOverride
        val msg = override ?: listOf(statusBase, if (hiddenCount > 0) t.more(hiddenCount) else "").filter { it.isNotEmpty() }.joinToString(" · ")
        tv.text = msg
        val icon = if (override == null && statusIcon != 0) getDrawable(statusIcon) else null
        icon?.setBounds(0, 0, (13 * resources.displayMetrics.density).toInt(), (13 * resources.displayMetrics.density).toInt())
        tv.setCompoundDrawables(icon, null, null, null)
    }

    private fun renderPill(icon: Int, time: String, title: String) {
        findViewById<ImageView>(R.id.pillIcon).setImageResource(icon)
        text(R.id.pillTime).apply { text = time; visibility = if (time.isEmpty()) View.GONE else View.VISIBLE }
        text(R.id.pillTitle).text = title
    }

    private fun renderCard(agenda: Agenda) {
        val list = config.calendarStyle == CalendarStyle.LIST
        findViewById<View>(R.id.calHeader).visibility = if (list) View.GONE else View.VISIBLE
        findViewById<View>(R.id.timeline).visibility = if (list) View.GONE else View.VISIBLE
        findViewById<View>(R.id.agendaList).visibility = if (list) View.VISIBLE else View.GONE
        if (list) { renderList(agenda); return }
        val now = ZonedDateTime.now(config.timezone)
        text(R.id.calWeekday).text = now.dayOfWeek.getDisplayName(TextStyle.FULL, t.locale).uppercase(t.locale)
        text(R.id.calDay).text = now.dayOfMonth.toString()
        val d = resources.displayMetrics.density
        val chips = findViewById<LinearLayout>(R.id.chips)
        chips.removeAllViews()
        for (item in agenda.allDay.take(2)) {
            chips.addView(TextView(this).apply {
                text = item.title; textSize = 14f; setTextColor(0xFFFFFFFF.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD)
                setBackgroundResource(R.drawable.chip_bg); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding((10 * d).toInt(), (3 * d).toInt(), (10 * d).toInt(), (3 * d).toInt())
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = (8 * d).toInt()
            })
        }
        val colors = intArrayOf(0xFF7FD8FF.toInt(), 0xFF9CF08C.toInt(), 0xFFFF9BDB.toInt(), 0xFFFFC27A.toInt(), 0xFFC9A8FF.toInt())
        fun hm(min: Int) = "%02d:%02d".format(min / 60 % 24, min % 60)
        val entries = agenda.timed.mapIndexed { i, e -> TimelineEntry(e.title, "${hm(e.startMin)} – ${hm(e.endMin)}", colors[i % colors.size]) }
        val timeline = buildTimeline(agenda.timed.mapIndexed { i, e -> TimelineEvent(e.startMin, e.endMin, i) })
        findViewById<TimelineView>(R.id.timeline).set(timeline, entries, t.hourSuffix)
        val hidden = (timeline?.hidden ?: 0)
        if (hidden > 0) text(R.id.status).text = t.more(hidden)
    }

    /** The plain appointment list (setting "calendar_style=list"). */
    private fun renderList(agenda: Agenda) {
        val box = findViewById<LinearLayout>(R.id.agendaList)
        val d = resources.displayMetrics.density
        box.removeAllViews()
        for (row in agendaRows(agenda, t)) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (row.time.isNotEmpty()) line.addView(TextView(this).apply {
                text = row.time; textSize = 22f; setTextColor(0xFFFFFFFF.toInt()); setTypeface(typeface, android.graphics.Typeface.BOLD)
                minWidth = (120 * d).toInt()
            })
            line.addView(TextView(this).apply {
                text = row.title; textSize = 22f; setTextColor(0xFFFFFFFF.toInt()); maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            box.addView(line, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { bottomMargin = (8 * d).toInt() })
        }
    }

    /** "-1°" with a proper minus sign. */
    private fun temp(c: Int) = "$c°".replace("-", "−")

    private fun renderGreeting() {
        text(R.id.greeting).text = greeting(config.greetingName, t, ZonedDateTime.now(config.timezone).hour)
    }

    private fun renderClock() {
        renderGreeting()
        text(R.id.clock).text = ZonedDateTime.now(config.timezone).format(DateTimeFormatter.ofPattern(t.clockPattern, t.locale))
    }

    private fun renderCountdown() {
        text(R.id.countdown).text = timer.remainingMs()?.let { countdownLabel(it, t) }
            ?: if (pausedRemainingMs != null) "" else SettingsTexts.forTexts(t).openHint
    }

    private fun goToStandby() {
        standbyRequested = true
        val psk = config.tvPsk
        Thread {
            val result = SonyApi().standbyWithRetry(psk)
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                timer.cancel()
                renderCountdown()
                // Never hold the screen on after the timer ran out, even if standby failed.
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (result.isFailure) { statusOverride = t.standbyFailed; text(R.id.status).text = t.standbyFailed }
            }
        }.start()
    }

    private fun text(id: Int): TextView = findViewById(id)

    private companion object {
        const val TICK_MS = 30_000L
        val OPEN_SETTINGS_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_SETTINGS)
        const val STATE_PREFS = "morningtv_state"
        const val CACHE_KEY = "agenda_cache"
        val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
