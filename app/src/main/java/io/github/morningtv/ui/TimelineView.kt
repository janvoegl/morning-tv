package io.github.morningtv.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View

/** One event as the timeline draws it. */
data class TimelineEntry(val title: String, val range: String, val color: Int)

/**
 * Two-column day timeline like the iPhone calendar widget: hour labels with lines, events as
 * coloured blocks. Rows are stretched to the view height.
 */
class TimelineView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var timeline: Timeline? = null
    private var entries: List<TimelineEntry> = emptyList()
    private var hourSuffix = ":00"

    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = 12 * sp }
    private val linePaint = Paint().apply { color = 0x48FFFFFF; strokeWidth = d }
    private val blockPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 14 * sp; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt(); textSize = 12 * sp }

    fun set(timeline: Timeline?, entries: List<TimelineEntry>, hourSuffix: String) {
        this.timeline = timeline
        this.entries = entries
        this.hourSuffix = hourSuffix
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val tl = timeline ?: return
        val gap = 16 * d
        val colW = (width - gap) / 2
        val top = 8 * d  // room for the first hour label, which is centred on its line
        val rowH = (height - top) / tl.rowsPerColumn
        val labelW = 50 * d
        for (c in 0..1) {
            val x0 = c * (colW + gap)
            for (r in 0 until tl.rowsPerColumn) {
                val hour = tl.firstHour + c * tl.rowsPerColumn + r
                if (hour >= tl.endHour) break
                val y = top + r * rowH + d
                canvas.drawText("%02d%s".format(hour, hourSuffix), x0, y + labelPaint.textSize * 0.35f, labelPaint)
                canvas.drawLine(x0 + labelW, y, x0 + colW, y, linePaint)
            }
        }
        for (b in tl.blocks) {
            val e = entries.getOrNull(b.index) ?: continue
            val x0 = b.column * (colW + gap) + labelW
            val bTop = top + b.topHours * rowH + 2 * d
            val h = b.heightHours * rowH - 3 * d
            val rect = RectF(x0, bTop, b.column * (colW + gap) + colW, bTop + h)
            blockPaint.color = blend(e.color, 0xFF6A2F9A.toInt(), 0.62f)
            canvas.drawRoundRect(rect, 6 * d, 6 * d, blockPaint)
            barPaint.color = e.color
            canvas.drawRoundRect(RectF(rect.left, rect.top, rect.left + 4 * d, rect.bottom), 2 * d, 2 * d, barPaint)
            if (h < 20 * d) continue
            val textW = rect.width() - 14 * d
            val tx = rect.left + 10 * d
            val titleY = if (h >= 40 * d) rect.top + 18 * d else rect.centerY() + titlePaint.textSize * 0.35f
            canvas.drawText(TextUtils.ellipsize(e.title, android.text.TextPaint(titlePaint), textW, TextUtils.TruncateAt.END).toString(), tx, titleY, titlePaint)
            if (h >= 40 * d) canvas.drawText(e.range, tx, titleY + 15 * d, rangePaint)
        }
    }

    private fun blend(a: Int, b: Int, wa: Float): Int {
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * wa + ((b shr shift) and 0xFF) * (1 - wa)).toInt()
        return Color.rgb(ch(16), ch(8), ch(0))
    }
}
