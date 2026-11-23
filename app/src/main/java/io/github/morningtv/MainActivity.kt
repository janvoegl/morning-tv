package io.github.morningtv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import io.github.morningtv.calendar.Agenda
import io.github.morningtv.calendar.parseAgenda
import io.github.morningtv.config.AppConfig
import io.github.morningtv.config.ConfigStore
import io.github.morningtv.sony.SonyApi
import io.github.morningtv.standby.StandbyTimer
import io.github.morningtv.standby.countdownLabel
import io.github.morningtv.text.Texts
import io.github.morningtv.transport.POST_BODY_EXTRA
import io.github.morningtv.transport.extractPayload
import io.github.morningtv.ui.DatedAgenda
import io.github.morningtv.ui.agendaForToday
import io.github.morningtv.ui.agendaRows
import io.github.morningtv.ui.isFreshLaunch
import io.github.morningtv.ui.greeting
import io.github.morningtv.ui.rainLine
import io.github.morningtv.weather.WeatherClient
import io.github.morningtv.weather.WeatherSummary
import io.github.morningtv.weather.weatherIcon
import io.github.morningtv.weather.weatherLabel
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
    private var standbyRequested = false
    /** Follows the TV's system language (German or English). */
    private val t: Texts get() = Texts.forLocale(resources.configuration.locales[0])

    private val tick = object : Runnable {
        override fun run() {
            renderClock()
            renderCountdown()
            if (timer.isDue() && !standbyRequested) goToStandby()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        config = loadConfig()
        if (isFreshLaunch(intent.flags, restored = savedInstanceState != null)) handleLaunch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (handleLaunch(intent)) {
            renderAgenda()
            renderCountdown()
        }
    }

    override fun onStart() {
        super.onStart()
        config = loadConfig()
        text(R.id.greeting).text = greeting(config.greetingName, t)
        renderAgenda()
        loadWeather()
        handler.post(tick)
    }

    override fun onStop() {
        super.onStop()
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
        agenda = DatedAgenda(today, parseAgenda(payload, today, config.timezone, t) ?: Agenda(emptyList(), emptyList()))
        standbyRequested = false
        timer.arm(config.standbyMinutes)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        text(R.id.status).text = ""
        return true
    }

    private fun loadConfig(): AppConfig =
        ConfigStore(getSharedPreferences(ConfigStore.PREFS_NAME, Context.MODE_PRIVATE)).load()

    private fun loadWeather() {
        val cfg = config
        if (cfg.latitude.isNaN() || cfg.longitude.isNaN()) {
            showWeather(null, t.weatherNotSet)
            return
        }
        text(R.id.weatherNow).text = "…"
        Thread {
            val w = WeatherClient().load(cfg, LocalTime.now(cfg.timezone).hour)
            runOnUiThread { if (!isDestroyed) showWeather(w, t.weatherUnavailable) }
        }.start()
    }

    private fun showWeather(w: WeatherSummary?, fallback: String) {
        val place = config.locationName.trim()
        text(R.id.weatherTitle).text =
            if (place.isEmpty()) t.weatherTitle else "${t.weatherTitle} · ${place.uppercase(Locale.ROOT)}"
        if (w == null) {
            text(R.id.weatherNow).text = fallback
            text(R.id.weatherDetail).text = ""
            text(R.id.weatherHours).text = ""
            return
        }
        text(R.id.weatherNow).text = "${weatherIcon(w.nowCode)}  ${w.nowTempC}°  ${weatherLabel(w.nowCode, t)}"
        text(R.id.weatherDetail).text = "${t.highLow(w.highC, w.lowC)}\n${rainLine(w, t)}"
        fun row(cell: (Int) -> String) = w.hours.indices.joinToString("  ") { "%-4s".format(cell(it)) }
        text(R.id.weatherHours).text = listOf(
            row { "%02d".format(w.hours[it].hour) },
            row { "${w.hours[it].tempC}°" },
            row { weatherIcon(w.hours[it].code) },
        ).joinToString("\n")
    }

    private fun renderAgenda() {
        val box = findViewById<LinearLayout>(R.id.agenda)
        val density = resources.displayMetrics.density
        box.removeAllViews()
        for (row in agendaRows(agendaForToday(agenda, LocalDate.now(config.timezone)), t)) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            if (row.time.isNotEmpty()) line.addView(TextView(this).apply {
                text = row.time
                textSize = 26f
                setTextColor(0xFFF2B33D.toInt())
                minWidth = (150 * density).toInt()
            })
            line.addView(TextView(this).apply {
                text = row.title
                textSize = 26f
                setTextColor(0xFFF2EBDD.toInt())
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            box.addView(line, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = (10 * density).toInt() })
        }
    }

    private fun renderClock() {
        text(R.id.clock).text = ZonedDateTime.now(config.timezone).format(DateTimeFormatter.ofPattern(t.clockPattern, t.locale))
    }

    private fun renderCountdown() {
        text(R.id.countdown).text = timer.remainingMs()?.let { countdownLabel(it, t) } ?: ""
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
                if (result.isFailure) text(R.id.status).text = t.standbyFailed
            }
        }.start()
    }

    private fun text(id: Int): TextView = findViewById(id)

    private companion object {
        const val TICK_MS = 30_000L
    }
}
