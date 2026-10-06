package com.josephvia.bezelcalm

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * The mode picker: the four modes sit on a dial that follows the bezel. Whatever is at
 * twelve o'clock is selected, and the Back key starts it.
 */
class MenuScene(initial: Mode) : Scene() {

    private val modes = Mode.entries
    private val stepper = DetentStepper()

    /** Unbounded step count so the dial can spin round and round without snapping back. */
    private var targetStep = initial.ordinal
    private var shownStep = initial.ordinal.toFloat()
    private var time = 0f

    val selected: Mode get() = modes[Math.floorMod(targetStep, modes.size)]

    private val titlePaint = Ink.textPaint()
    private val blurbPaint = Ink.textPaint()
    private val glyphFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val flame = Path()
    private val box = RectF()

    override fun onRotate(clicks: Float) {
        targetStep += stepper.feed(clicks)
    }

    override fun update(dt: Float): Boolean {
        time += dt
        shownStep += (targetStep - shownStep) * (1f - exp(-dt * 12f))
        if (abs(targetStep - shownStep) < 0.001f) shownStep = targetStep.toFloat()
        // Keep animating for the gentle glow pulse on the selected glyph.
        return true
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(0xFF000000.toInt())
        val stepAngle = 360f / modes.size
        val ringR = u * 0.33f

        // Faint guide ring and the notch at twelve o'clock that marks the selection.
        ringPaint.color = 0xFF2A2733.toInt()
        ringPaint.strokeWidth = u * 0.004f
        canvas.drawCircle(cx, cy, ringR, ringPaint)
        ringPaint.color = selected.accent
        ringPaint.alpha = 170
        ringPaint.strokeWidth = u * 0.012f
        ringPaint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(cx, cy - u * 0.475f, cx, cy - u * 0.44f, ringPaint)

        for (i in modes.indices) {
            // Modes are laid out anticlockwise so a clockwise turn brings the next one to the top.
            val angleDeg = (i - shownStep) * -stepAngle - 90f
            val a = Math.toRadians(angleDeg.toDouble())
            val x = cx + (ringR * cos(a)).toFloat()
            val y = cy + (ringR * sin(a)).toFloat()
            var offset = abs(i - shownStep) % modes.size
            if (offset > modes.size / 2f) offset = modes.size - offset
            val focus = (1f - offset).coerceIn(0f, 1f)
            val pulse = 0.85f + 0.15f * sin(time * 2.2f)
            drawGlyph(canvas, modes[i], x, y, u * (0.05f + 0.02f * focus), 0.35f + 0.65f * focus * pulse)
        }

        val mode = selected
        Ink.drawCaption(canvas, mode.title, cx, cy + u * 0.02f, u * 0.095f, 0xFFECE6DA.toInt(), 0.9f, titlePaint)
        Ink.drawCaption(canvas, mode.blurb, cx, cy + u * 0.10f, u * 0.05f, 0xFF9A95A6.toInt(), 0.8f, blurbPaint)
        Ink.drawBackKeyHint(canvas, cx, cy, u, "begin", mode.accent, 0.85f)
    }

    private fun drawGlyph(canvas: Canvas, mode: Mode, x: Float, y: Float, r: Float, alpha: Float) {
        glyphFill.color = 0xFF000000.toInt()
        canvas.drawCircle(x, y, r * 1.45f, glyphFill)
        val a = (alpha.coerceIn(0f, 1f) * 255).toInt()
        glyphFill.color = mode.accent
        glyphFill.alpha = a
        glyphStroke.color = mode.accent
        glyphStroke.alpha = a
        glyphStroke.strokeWidth = r * 0.16f
        when (mode) {
            Mode.EMBER -> {
                // Outer flame with a leaning tip and a second tongue on the left, then a hot core.
                flame.reset()
                flame.moveTo(x + r * 0.18f, y - r * 1.2f)
                flame.cubicTo(x + r * 0.4f, y - r * 0.75f, x + r * 0.88f, y - r * 0.45f, x + r * 0.82f, y + r * 0.3f)
                flame.cubicTo(x + r * 0.8f, y + r * 0.8f, x + r * 0.42f, y + r * 1.05f, x, y + r * 1.05f)
                flame.cubicTo(x - r * 0.45f, y + r * 1.05f, x - r * 0.84f, y + r * 0.75f, x - r * 0.82f, y + r * 0.28f)
                flame.cubicTo(x - r * 0.8f, y - r * 0.1f, x - r * 0.6f, y - r * 0.38f, x - r * 0.46f, y - r * 0.66f)
                flame.cubicTo(x - r * 0.4f, y - r * 0.32f, x - r * 0.22f, y - r * 0.2f, x - r * 0.12f, y - r * 0.24f)
                flame.cubicTo(x - r * 0.1f, y - r * 0.62f, x, y - r * 0.95f, x + r * 0.18f, y - r * 1.2f)
                flame.close()
                canvas.drawPath(flame, glyphFill)
                glyphFill.color = 0xFFFFD58A.toInt()
                glyphFill.alpha = a
                flame.reset()
                flame.moveTo(x, y - r * 0.05f)
                flame.cubicTo(x + r * 0.3f, y + r * 0.25f, x + r * 0.45f, y + r * 0.45f, x + r * 0.42f, y + r * 0.62f)
                flame.cubicTo(x + r * 0.4f, y + r * 0.85f, x + r * 0.2f, y + r * 0.92f, x, y + r * 0.92f)
                flame.cubicTo(x - r * 0.2f, y + r * 0.92f, x - r * 0.4f, y + r * 0.85f, x - r * 0.42f, y + r * 0.62f)
                flame.cubicTo(x - r * 0.45f, y + r * 0.45f, x - r * 0.3f, y + r * 0.25f, x, y - r * 0.05f)
                flame.close()
                canvas.drawPath(flame, glyphFill)
            }
            Mode.LABYRINTH -> {
                // A tiny square maze: outer frame with a gap and two inner walls.
                val s = r * 0.9f
                canvas.drawLine(x - s, y - s, x + s * 0.3f, y - s, glyphStroke)
                canvas.drawLine(x + s, y - s, x + s, y + s, glyphStroke)
                canvas.drawLine(x + s, y + s, x - s, y + s, glyphStroke)
                canvas.drawLine(x - s, y + s, x - s, y - s, glyphStroke)
                canvas.drawLine(x - s * 0.35f, y - s, x - s * 0.35f, y + s * 0.3f, glyphStroke)
                canvas.drawLine(x + s * 0.35f, y + s, x + s * 0.35f, y - s * 0.3f, glyphStroke)
            }
            Mode.BEADS -> {
                for (k in 0 until 8) {
                    val t = Math.toRadians(k * 45.0)
                    val br = if (k == 6) r * 0.26f else r * 0.18f
                    canvas.drawCircle(x + (r * 0.8f * cos(t)).toFloat(), y + (r * 0.8f * sin(t)).toFloat(), br, glyphFill)
                }
            }
            Mode.VOID -> {
                box.set(x - r * 0.85f, y - r * 0.85f, x + r * 0.85f, y + r * 0.85f)
                canvas.drawOval(box, glyphStroke)
            }
        }
    }
}

/**
 * Turns bezel input into whole steps. A Galaxy bezel sends about 1.0 per click, but some
 * crowns send small fractions, so this adds them up and steps once per full unit.
 */
class DetentStepper {
    private var pending = 0f

    fun feed(clicks: Float): Int {
        // A change of direction drops what was left over from the other way.
        if (clicks * pending < 0f) pending = 0f
        pending += clicks
        var steps = 0
        while (pending >= 1f - EPSILON) { steps++; pending -= 1f }
        while (pending <= -1f + EPSILON) { steps--; pending += 1f }
        return steps
    }

    private companion object {
        const val EPSILON = 1e-3f
    }
}
