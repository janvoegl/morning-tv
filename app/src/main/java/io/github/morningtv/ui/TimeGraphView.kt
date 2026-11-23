package io.github.morningtv.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** One weather column of the graph. */
data class GraphHour(val hour: Int, val tempC: Int, val icon: String)

/** One appointment on the graph. [weather] is the forecast at its start, e.g. "☀ 9°". */
data class GraphEvent(
    val title: String, val range: String, val startMin: Int, val endMin: Int, val color: Int, val weather: String,
)

/** Everything the graph draws. [nowMin]: minutes since midnight; [nowLabel] e.g. "07:20". */
data class GraphData(
    val range: GraphRange, val hours: List<GraphHour>, val events: List<GraphEvent>,
    val nowMin: Int, val nowLabel: String,
)

/**
 * Time graph: hour grid, weather icons and a temperature curve per hour, appointments as bars with
 * chips below, and a line for the current time. Chips show the full title (wrapped).
 */
class TimeGraphView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var data: GraphData? = null
    /** Called (after drawing) with the number of appointments that did not fit. */
    var onHidden: ((Int) -> Unit)? = null
    private var reportedHidden = -1

    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * sp; this.color = color
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val hourPaint = paint(12f, 0xCCFFFFFF.toInt()).apply { textAlign = Paint.Align.CENTER }
    private val iconPaint = paint(20f, Color.WHITE).apply { textAlign = Paint.Align.CENTER }
    private val tempPaint = paint(13f, Color.WHITE).apply { textAlign = Paint.Align.CENTER }
    private val titlePaint = paint(14f, Color.WHITE, bold = true)
    private val subPaint = paint(12f, 0xCCFFFFFF.toInt())
    private val chipWeatherPaint = paint(13f, Color.WHITE).apply { textAlign = Paint.Align.RIGHT }
    private val gridPaint = Paint().apply { strokeWidth = d }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xD9FFFFFF.toInt(); strokeWidth = 2 * d; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
    }
    private val nowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NOW_COLOR; strokeWidth = 3 * d }
    private val nowTextPaint = paint(11f, 0xFF110022.toInt(), bold = true).apply { textAlign = Paint.Align.CENTER }

    fun set(data: GraphData?) { this.data = data; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val g = data ?: return
        val w = width.toFloat()
        val r = g.range
        val cols = r.endHour - r.firstHour
        val cw = w / cols
        fun x(min: Float) = graphX(min, r, w)
        val gridTop = 18 * d
        val iconY = 22 * d + iconPaint.textSize
        val curveTop = 78 * d
        val curveH = 40 * d
        val base = 118 * d

        // hour lines and labels
        for (h in r.firstHour..r.endHour) {
            val lx = (h - r.firstHour) * cw
            gridPaint.color = if (h % 3 == 0) 0x61FFFFFF else 0x38FFFFFF
            canvas.drawLine(lx, gridTop, lx, height.toFloat(), gridPaint)
            if (kotlin.math.abs(h * 60f - g.nowMin) > 36f) canvas.drawText("%02d".format(h), lx, 12 * d, hourPaint)
        }
        // past
        val nowX = x(g.nowMin.toFloat())
        fill.color = 0x24000000
        canvas.drawRoundRect(RectF(0f, gridTop, max(0f, nowX), height.toFloat()), 10 * d, 10 * d, fill)
        // now line (behind everything below)
        canvas.drawLine(nowX, 0f, nowX, height.toFloat(), nowPaint)

        // weather: icons + temperature curve
        val byHour = g.hours.associateBy { it.hour }
        val shown = (r.firstHour until r.endHour).mapNotNull { byHour[it] }
        if (shown.isNotEmpty()) {
            val tMin = shown.minOf { it.tempC }; val tMax = shown.maxOf { it.tempC }
            fun yT(t: Int) = if (tMax == tMin) curveTop + curveH / 2 else curveTop + curveH - (t - tMin).toFloat() / (tMax - tMin) * curveH
            val path = Path()
            shown.forEachIndexed { i, hp ->
                val cx = (hp.hour - r.firstHour + 0.5f) * cw
                canvas.drawText(hp.icon, cx, iconY, iconPaint)
                val cy = yT(hp.tempC)
                if (i == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
            }
            canvas.drawPath(path, linePaint)
            fill.color = Color.WHITE
            shown.forEach { hp ->
                val cx = (hp.hour - r.firstHour + 0.5f) * cw
                val cy = yT(hp.tempC)
                canvas.drawCircle(cx, cy, 3 * d, fill)
                canvas.drawText("${hp.tempC}°".replace("-", "−"), cx, cy - 8 * d, tempPaint)
            }
        }

        // appointments
        val chipPad = 8 * d
        val measured = g.events.mapIndexed { i, e ->
            val chipW = min(max(titlePaint.measureText(e.title) + 3 * chipPad, 130 * d), 260 * d)
            ChipRequest(i, e.startMin, chipW)
        }
        val layout = layoutChips(measured, { x(it.toFloat()) }, w, 6 * d)
        val lanes = layout.placed.groupBy { it.lane }
        var y = base + 6 * d
        var hidden = layout.hidden
        for (lane in lanes.keys.sorted()) {
            val items = lanes.getValue(lane).map { p ->
                val e = g.events[p.index]; val cwid = measured[p.index].chipWidth
                val text = StaticLayout.Builder.obtain(e.title, 0, e.title.length, titlePaint, (cwid - 2 * chipPad - 4 * d).toInt())
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
                Triple(p, e, text)
            }
            val laneH = items.maxOf { (_, _, t) -> t.height + 2 * chipPad + subPaint.textSize * 1.3f }
            if (y + laneH > height) { hidden += items.size; continue }
            for ((p, e, text) in items) {
                val cwid = measured[p.index].chipWidth
                val ex0 = x(e.startMin.toFloat()); val ex1 = max(ex0 + 6 * d, x(e.endMin.toFloat()))
                fill.color = e.color
                canvas.drawRoundRect(RectF(ex0, base - 10 * d, ex1, base - 3 * d), 3.5f * d, 3.5f * d, fill)
                canvas.drawRect(ex0, base - 4 * d, ex0 + 2 * d, y, fill)
                val rect = RectF(p.left, y, p.left + cwid, y + laneH)
                fill.color = blend(e.color, 0xFF6A2F9A.toInt(), 0.62f)
                canvas.drawRoundRect(rect, 8 * d, 8 * d, fill)
                fill.color = e.color
                canvas.drawRoundRect(RectF(rect.left, rect.top, rect.left + 4 * d, rect.bottom), 2 * d, 2 * d, fill)
                canvas.save(); canvas.translate(rect.left + chipPad + 4 * d, rect.top + chipPad * 0.6f); text.draw(canvas); canvas.restore()
                val subY = rect.top + chipPad * 0.6f + text.height + subPaint.textSize * 1.1f
                canvas.drawText(e.range, rect.left + chipPad + 4 * d, subY, subPaint)
                canvas.drawText(e.weather, rect.right - chipPad, subY, chipWeatherPaint)
            }
            y += laneH + 6 * d
        }
        if (hidden != reportedHidden) { reportedHidden = hidden; post { onHidden?.invoke(hidden) } }

        // now pill on top
        val pw = nowTextPaint.measureText(g.nowLabel) + 12 * d
        fill.color = NOW_COLOR
        canvas.drawRoundRect(RectF(nowX - pw / 2, 0f, nowX + pw / 2, 16 * d), 8 * d, 8 * d, fill)
        canvas.drawText(g.nowLabel, nowX, 12 * d, nowTextPaint)
    }

    private fun blend(a: Int, b: Int, wa: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * wa + ((b shr shift) and 0xFF) * (1 - wa)).toInt()
        return Color.rgb(ch(16), ch(8), ch(0))
    }

    private companion object { const val NOW_COLOR = 0xFF3DFFD0.toInt() }
}
