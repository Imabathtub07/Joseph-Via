package com.josephvia.bezelcalm

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/**
 * A string of prayer beads around the rim. Every bezel click moves exactly one bead past the
 * top, so the ring turns in step with the bezel. 108 beads make a round, marked by a larger
 * gold bead and a soft pulse.
 */
class BeadsScene(private val onRound: () -> Unit) : Scene() {

    private val counter = MalaCounter()
    private val stepper = DetentStepper()
    private var shownPos = 0f
    private var time = 0f
    private var roundTime = -10f

    private val stringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFF3B2F28.toInt()
    }
    private val beadPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val guruPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tasselPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = 0xFFB0413E.toInt()
    }
    private val countPaint = Ink.textPaint()
    private val smallPaint = Ink.textPaint()

    override fun onResize() {
        beadPaint.shader = sphere(0xFFE2C29C.toInt(), 0xFFB08560.toInt(), 0xFF5C3F2A.toInt())
        guruPaint.shader = sphere(0xFFFFF0C2.toInt(), 0xFFE0B04A.toInt(), 0xFF7A5414.toInt())
        haloPaint.shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(0x66FFE3A0, 0x22FFB347, 0x00FFB347),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    // A unit-radius shaded sphere, lit from the top left; drawn scaled to each bead's size.
    private fun sphere(hi: Int, mid: Int, lo: Int) = RadialGradient(
        -0.35f, -0.4f, 1.35f,
        intArrayOf(hi, mid, lo),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )

    override fun onRotate(clicks: Float) {
        val steps = stepper.feed(clicks)
        if (steps == 0) return
        if (steps < 0 && counter.position == 0) {
            // Already at the start: a small nudge back so the turn still feels answered.
            shownPos = -0.3f
            return
        }
        if (counter.turn(steps) > 0) {
            roundTime = time
            onRound()
        }
    }

    override fun update(dt: Float): Boolean {
        time += dt
        shownPos += (counter.position - shownPos) * (1f - exp(-dt * 14f))
        if (abs(counter.position - shownPos) < 0.001f) shownPos = counter.position.toFloat()
        return true
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(0xFF000000.toInt())
        val ringR = u * 0.40f
        stringPaint.strokeWidth = u * 0.006f
        canvas.drawCircle(cx, cy, ringR, stringPaint)

        // Soft glow over the bead at twelve o'clock, brighter for a moment after a full round.
        val sinceRound = time - roundTime
        val bloom = if (sinceRound in 0f..2.5f) 1f - sinceRound / 2.5f else 0f
        canvas.save()
        canvas.translate(cx, cy - ringR)
        val hr = u * (0.09f + 0.10f * bloom)
        canvas.scale(hr, hr)
        haloPaint.alpha = (255 * (0.55f + 0.45f * bloom)).toInt()
        canvas.drawCircle(0f, 0f, 1f, haloPaint)
        canvas.restore()

        val base = floor(shownPos).toInt()
        for (k in base - VISIBLE / 2 + 1..base + VISIBLE / 2) {
            if (k < 0) continue
            // The ring follows the bezel: a clockwise turn carries beads clockwise past the top.
            val deg = (shownPos - k) * DEGREES_PER_BEAD - 90f
            val a = Math.toRadians(deg.toDouble())
            val x = cx + (ringR * cos(a)).toFloat()
            val y = cy + (ringR * sin(a)).toFloat()
            val guru = k % MalaCounter.BEADS == 0
            val r = u * if (guru) 0.042f else 0.03f
            if (guru) {
                // A short tassel hanging outward from the gold bead.
                tasselPaint.strokeWidth = u * 0.012f
                val ox = (cos(a) * u * 0.06).toFloat()
                val oy = (sin(a) * u * 0.06).toFloat()
                canvas.drawLine(x, y, x + ox, y + oy, tasselPaint)
            }
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(r, r)
            canvas.drawCircle(0f, 0f, 1f, if (guru) guruPaint else beadPaint)
            canvas.restore()
        }

        val shown = if (counter.count == 0 && counter.position > 0) MalaCounter.BEADS else counter.count
        Ink.drawCaption(canvas, "$shown", cx, cy + u * 0.045f, u * 0.15f, 0xFFF1E4D2.toInt(), 0.9f, countPaint)
        val sub = if (counter.rounds > 0) "round ${counter.rounds + if (shown == MalaCounter.BEADS) 0 else 1}" else "of ${MalaCounter.BEADS}"
        Ink.drawCaption(canvas, sub, cx, cy + u * 0.125f, u * 0.05f, 0xFF9A8E80.toInt(), 0.8f, smallPaint)

        val intro = EmberScene.introAlpha(time)
        Ink.drawCaption(canvas, "one click, one bead", cx, cy - u * 0.16f, u * 0.05f, 0xFFD9C7B0.toInt(), intro * 0.8f, smallPaint)
        Ink.drawBackKeyHint(canvas, cx, cy, u, "menu", Mode.BEADS.accent, intro * 0.7f)
    }

    companion object {
        /** One bead per bezel click; a Galaxy bezel has 24 clicks in a full turn. */
        private const val DEGREES_PER_BEAD = 15f
        /** A full ring of beads, 15 degrees apart. */
        private const val VISIBLE = 24
    }
}

/** Counts beads on a 108-bead string. Turning back past the start is not allowed. */
class MalaCounter {
    var position = 0
        private set

    /** Bead within the current round, 0 until [BEADS] - 1. */
    val count get() = position % BEADS

    /** Rounds completed so far. */
    val rounds get() = position / BEADS

    /** Move by [steps] beads; returns how many rounds this move completed. */
    fun turn(steps: Int): Int {
        val before = rounds
        position = (position + steps).coerceAtLeast(0)
        return (rounds - before).coerceAtLeast(0)
    }

    companion object {
        const val BEADS = 108
    }
}
