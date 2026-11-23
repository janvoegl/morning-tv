package io.github.morningtv.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import io.github.morningtv.config.AppConfig
import io.github.morningtv.config.ConfigStore
import io.github.morningtv.text.SettingsTexts
import io.github.morningtv.weather.Place
import io.github.morningtv.weather.geocodingUrl
import io.github.morningtv.weather.httpGet
import io.github.morningtv.weather.parsePlaces

/**
 * Settings menu drawn over the morning screen (same activity, so the standby timer logic stays in one place).
 * Up/Down select, Left/Right or OK change a value, Back closes (handled by the activity via [close]).
 */
class SettingsOverlay(
    private val activity: Activity,
    private val parent: FrameLayout,
    private val store: ConfigStore,
    private val config: () -> AppConfig,
    private val texts: () -> SettingsTexts,
    private val language: () -> String,
    private val onChanged: () -> Unit,
    private val onClose: () -> Unit,
) {
    private var view: FrameLayout? = null
    private var list: LinearLayout? = null
    val isShown: Boolean get() = view != null
    private val d = activity.resources.displayMetrics.density

    fun show() {
        if (view != null) return
        val st = texts()
        val scrim = FrameLayout(activity).apply { setBackgroundColor(0xCC000000.toInt()); isClickable = true }
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { setColor(0xF01E1A33.toInt()); cornerRadius = 20 * d }
            setPadding((28 * d).toInt(), (22 * d).toInt(), (28 * d).toInt(), (18 * d).toInt())
        }
        panel.addView(label(st.title, 20f, Color.WHITE, bold = true))
        val rows = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        list = rows
        panel.addView(rows, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * d).toInt() })
        panel.addView(label(st.keysHint, 11f, 0xB3FFFFFF.toInt()), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * d).toInt() })
        scrim.addView(panel, FrameLayout.LayoutParams((540 * d).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        parent.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        view = scrim
        refresh(0)
    }

    fun close() {
        view?.let { parent.removeView(it) }
        view = null
        list = null
        onClose()
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false) = TextView(activity).apply {
        this.text = text; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun refresh(focusIndex: Int) {
        val box = list ?: return
        val rows = settingRows(config(), texts())
        box.removeAllViews()
        rows.forEachIndexed { i, row ->
            val line = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply { setColor(0x40FFFFFF); cornerRadius = 10 * d })
                    addState(intArrayOf(), GradientDrawable().apply { setColor(Color.TRANSPARENT) })
                }
                setPadding((14 * d).toInt(), (7 * d).toInt(), (14 * d).toInt(), (7 * d).toInt())
                setOnKeyListener { _, code, ev -> ev.action == KeyEvent.ACTION_DOWN && handleKey(row, i, code) }
            }
            val muted = row.kind == RowKind.UNAVAILABLE
            line.addView(label(row.label, 14f, if (muted) 0x80FFFFFF.toInt() else Color.WHITE), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(label(
                when (row.kind) {
                    RowKind.CHOICE -> "◀  ${row.value}  ▶"
                    else -> row.value
                }, 14f, if (muted) 0x80FFFFFF.toInt() else 0xFFFFFFFF.toInt(), bold = true,
            ))
            box.addView(line)
            if (i == focusIndex) line.post { line.requestFocus() }
        }
    }

    private fun handleKey(row: SettingRow, index: Int, code: Int): Boolean {
        val step = when (code) {
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> 1
            KeyEvent.KEYCODE_DPAD_LEFT -> -1
            else -> return false
        }
        val activate = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
        when (row.kind) {
            RowKind.CHOICE -> changeChoice(config(), row.id, step)?.let { (k, v) -> apply(listOf(k to v), index) }
            RowKind.TEXT -> if (activate) askName(index)
            RowKind.SEARCH -> if (activate) askPlace(index)
            RowKind.UNAVAILABLE -> Unit
        }
        return true
    }

    private fun apply(updates: List<Pair<String, String>>, focusIndex: Int) {
        updates.forEach { (k, v) -> store.set(k, v) }
        onChanged()
        refresh(focusIndex)
    }

    private fun dialog() = AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)

    private fun input(prefill: String): EditText = EditText(activity).apply {
        setText(prefill); setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        imeOptions = EditorInfo.IME_ACTION_DONE; setSelection(text.length)
    }

    private fun askName(index: Int) {
        val st = texts()
        val box = input(config().greetingName)
        dialog().setTitle(st.namePrompt).setView(box)
            .setPositiveButton(st.ok) { _, _ -> apply(listOf("greeting_name" to box.text.toString().trim()), index) }
            .setNegativeButton(st.cancel, null).show()
    }

    private fun askPlace(index: Int) {
        val st = texts()
        val box = input("")
        dialog().setTitle(st.placePrompt).setView(box)
            .setPositiveButton(st.ok) { _, _ -> search(box.text.toString().trim(), index) }
            .setNegativeButton(st.cancel, null).show()
    }

    private fun search(query: String, index: Int) {
        if (query.isEmpty()) return
        val st = texts()
        Toast.makeText(activity, st.searching, Toast.LENGTH_SHORT).show()
        val lang = language()
        Thread {
            val places = runCatching { parsePlaces(httpGet(geocodingUrl(query, lang))) }
            activity.runOnUiThread {
                if (activity.isDestroyed || !isShown) return@runOnUiThread
                places.onFailure { Toast.makeText(activity, st.searchFailed, Toast.LENGTH_LONG).show() }
                places.onSuccess { found ->
                    if (found.isEmpty()) Toast.makeText(activity, st.noResults, Toast.LENGTH_LONG).show()
                    else pick(found, index)
                }
            }
        }.start()
    }

    private fun pick(places: List<Place>, index: Int) {
        val st = texts()
        dialog().setTitle(st.pickPlace)
            .setItems(places.map { it.label }.toTypedArray()) { _, which -> apply(placeUpdates(places[which]), index) }
            .setNegativeButton(st.cancel, null).show()
    }
}
