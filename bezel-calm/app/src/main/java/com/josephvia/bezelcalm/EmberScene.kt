package com.josephvia.bezelcalm

import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * A small campfire. Each bezel click is a breath of air on the coals: keep turning and it
 * grows into a roaring blaze throwing sparks, stop and it slowly settles back to embers.
 * Clockwise turns lean the flames right, anticlockwise lean them left.
 */
class EmberScene : Scene() {

    private val sim = EmberSim()
    private val rnd = Random.Default
    private var time = 0f

    private var sprites: GlowSprites? = null
    private var smokeSprite: GlowSprites? = null

    // Flame particles, kept in flat arrays to avoid allocating while animating.
    private val fx = FloatArray(MAX_FLAMES)
    private val fy = FloatArray(MAX_FLAMES)
    private val fvx = FloatArray(MAX_FLAMES)
    private val fvy = FloatArray(MAX_FLAMES)
    private val fAge = FloatArray(MAX_FLAMES)
    private val fLife = FloatArray(MAX_FLAMES)
    private val fSize = FloatArray(MAX_FLAMES)
    private val fHeat = FloatArray(MAX_FLAMES)
    private var flames = 0
    private var flameDebt = 0f

    private val sx = FloatArray(MAX_SPARKS)
    private val sy = FloatArray(MAX_SPARKS)
    private val svx = FloatArray(MAX_SPARKS)
    private val svy = FloatArray(MAX_SPARKS)
    private val sAge = FloatArray(MAX_SPARKS)
    private val sLife = FloatArray(MAX_SPARKS)
    private val sPhase = FloatArray(MAX_SPARKS)
    private var sparks = 0
    private var sparkDebt = 0f

    private val mx = FloatArray(MAX_SMOKE)
    private val my = FloatArray(MAX_SMOKE)
    private val mvx = FloatArray(MAX_SMOKE)
    private val mAge = FloatArray(MAX_SMOKE)
    private val mLife = FloatArray(MAX_SMOKE)
    private var smokes = 0
    private var smokeDebt = 0f

    // Coal bed under the logs: x offset, y offset, radius (fractions of u) and a flicker phase each.
    // Two loose rows, back row first so the front row overlaps it.
    private val coals = Array(COALS) { i ->
        val front = i >= COALS / 2
        val t = (if (front) i - COALS / 2 else i) / (COALS / 2 - 1f) - 0.5f
        floatArrayOf(
            t * (if (front) 0.30f else 0.26f) + (rnd.nextFloat() - 0.5f) * 0.03f,
            (if (front) 0.072f else 0.05f) + (rnd.nextFloat() - 0.5f) * 0.012f,
            0.018f + rnd.nextFloat() * 0.014f,
            rnd.nextFloat() * 6.28f,
        )
    }

    // Glowing cracks along each log: start and end positions along the log (-0.5..0.5) and a phase.
    private val cracks = Array(2) { Array(5) { floatArrayOf(rnd.nextFloat() - 0.5f, (rnd.nextFloat() - 0.5f) * 0.5f, rnd.nextFloat() * 6.28f) } }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val addPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { blendMode = BlendMode.PLUS }
    private val smokePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val logPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        blendMode = BlendMode.PLUS
    }
    private val captionPaint = Ink.textPaint()
    private val rect = RectF()
    private var baseY = 0f

    override fun onResize() {
        baseY = cy + u * 0.17f
        if (sprites == null) {
            sprites = GlowSprites(IntArray(BUCKETS) { fireColor(it / (BUCKETS - 1f)) })
            smokeSprite = GlowSprites(intArrayOf(0xFF6B6672.toInt()))
        }
        glowPaint.shader = RadialGradient(
            cx, baseY - u * 0.08f, u * 0.62f,
            intArrayOf(0xFFFF7A2A.toInt(), 0x66B83200, 0x00000000),
            floatArrayOf(0f, 0.38f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onRotate(clicks: Float) {
        sim.breathe(clicks)
        // A hard turn on a hot fire kicks up a spark or two right away.
        if (sim.heat > 0.3f && rnd.nextFloat() < 0.5f) spawnSpark()
    }

    override fun update(dt: Float): Boolean {
        time += dt
        sim.step(dt)
        val h = sim.heat

        flameDebt += (18f + 150f * h.pow(1.2f)) * dt
        while (flameDebt >= 1f) { flameDebt -= 1f; spawnFlame(h) }

        sparkDebt += ((sim.air - 0.15f).coerceAtLeast(0f) * 25f + (h - 0.6f).coerceAtLeast(0f) * 6f) * dt
        while (sparkDebt >= 1f) { sparkDebt -= 1f; spawnSpark() }

        smokeDebt += (0.4f - h).coerceAtLeast(0f) * 5f * dt
        while (smokeDebt >= 1f) { smokeDebt -= 1f; spawnSmoke() }

        val wind = sim.wind
        var i = 0
        while (i < flames) {
            fAge[i] += dt
            if (fAge[i] >= fLife[i]) { removeFlame(i); continue }
            val t = fAge[i] / fLife[i]
            fvy[i] -= u * 0.25f * (0.5f + h) * dt
            fvx[i] += (cx - fx[i]) * 1.8f * dt
            fx[i] += (fvx[i] + wind * u * 0.25f * t) * dt
            fy[i] += fvy[i] * dt
            i++
        }

        i = 0
        while (i < sparks) {
            sAge[i] += dt
            if (sAge[i] >= sLife[i]) { removeSpark(i); continue }
            svy[i] += u * 0.18f * dt
            svx[i] *= 1f - 0.8f * dt
            sx[i] += (svx[i] + wind * u * 0.12f) * dt
            sy[i] += svy[i] * dt
            i++
        }

        i = 0
        while (i < smokes) {
            mAge[i] += dt
            if (mAge[i] >= mLife[i]) { removeSmoke(i); continue }
            mx[i] += (mvx[i] + wind * u * 0.06f + sin(time * 0.9f + i) * u * 0.008f) * dt
            my[i] -= u * 0.06f * dt
            i++
        }
        return true
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(0xFF000000.toInt())
        val sprites = sprites ?: return
        val h = sim.heat
        val flicker = 0.85f + 0.08f * sin(time * 7.3f) + 0.05f * sin(time * 13.1f + 1.7f) + 0.04f * sin(time * 23.7f + 0.4f)

        // Warm light thrown on the surroundings.
        glowPaint.alpha = ((0.08f + 0.42f * h) * flicker * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, baseY - u * 0.08f, u * 0.62f, glowPaint)

        smokeSprite?.let { smoke ->
            for (i in 0 until smokes) {
                val t = mAge[i] / mLife[i]
                val size = u * (0.03f + 0.09f * t)
                smokePaint.alpha = (sin(t * PI).toFloat() * 0.10f * 255).toInt()
                rect.set(mx[i] - size, my[i] - size, mx[i] + size, my[i] + size)
                canvas.drawBitmap(smoke.sprites[0], null, rect, smokePaint)
            }
        }

        for (i in 0 until flames) {
            val t = fAge[i] / fLife[i]
            val temp = (fHeat[i] * (1f - t).pow(1.1f) * 0.95f + 0.05f).coerceIn(0f, 1f)
            val bucket = (temp * (BUCKETS - 1)).toInt()
            val size = fSize[i] * (1f - 0.55f * t)
            val alpha = (t * 6f).coerceAtMost(1f) * (1f - t).pow(0.8f) * 0.55f
            addPaint.alpha = (alpha * 255).toInt().coerceIn(0, 255)
            rect.set(fx[i] - size, fy[i] - size * 1.25f, fx[i] + size, fy[i] + size * 0.85f)
            canvas.drawBitmap(sprites.sprites[bucket], null, rect, addPaint)
        }

        drawCoalBodies(canvas)
        drawLogs(canvas, h, flicker)
        drawCoalGlow(canvas, sprites, h)

        for (i in 0 until sparks) {
            val t = sAge[i] / sLife[i]
            val twinkle = 0.6f + 0.4f * sin(sAge[i] * 40f + sPhase[i])
            addPaint.alpha = ((1f - t) * twinkle * 255).toInt().coerceIn(0, 255)
            val size = u * 0.014f * (1f - 0.5f * t)
            rect.set(sx[i] - size, sy[i] - size, sx[i] + size, sy[i] + size)
            canvas.drawBitmap(sprites.sprites[BUCKETS - 2], null, rect, addPaint)
        }

        val intro = introAlpha(time)
        Ink.drawCaption(canvas, "turn the bezel to breathe", cx, cy - u * 0.30f, u * 0.055f, 0xFFE9D9C8.toInt(), intro * 0.8f, captionPaint)
        Ink.drawBackKeyHint(canvas, cx, cy, u, "menu", Mode.EMBER.accent, intro * 0.7f)
    }

    private fun drawLogs(canvas: Canvas, h: Float, flicker: Float) {
        val length = u * 0.40f
        val thick = u * 0.07f
        val ly = baseY + u * 0.035f
        for (side in 0..1) {
            val angle = if (side == 0) -13f else 13f
            canvas.save()
            canvas.rotate(angle, cx, ly)
            rect.set(cx - length / 2, ly - thick / 2, cx + length / 2, ly + thick / 2)
            logPaint.color = 0xFF22140D.toInt()
            canvas.drawRoundRect(rect, thick / 2, thick / 2, logPaint)
            // A lighter strip of bark along the top edge.
            logPaint.color = 0xFF3A2519.toInt()
            rect.set(cx - length / 2 + thick * 0.4f, ly - thick / 2, cx + length / 2 - thick * 0.4f, ly - thick * 0.25f)
            canvas.drawRoundRect(rect, thick * 0.15f, thick * 0.15f, logPaint)

            crackPaint.strokeWidth = u * 0.006f
            for (c in cracks[side]) {
                val glow = (0.15f + 0.85f * h) * (0.6f + 0.4f * sin(time * 2.3f + c[2])) * flicker
                crackPaint.color = fireColor((0.35f + 0.5f * h).coerceAtMost(1f))
                crackPaint.alpha = (glow * 230).toInt().coerceIn(0, 255)
                // Keep each crack inside the log, clear of its rounded ends.
                val x0 = cx + c[0] * (length - thick * 1.6f) - thick * 0.22f
                val y0 = ly + c[1] * thick
                canvas.drawLine(x0, y0, x0 + thick * 0.45f, y0 + thick * 0.12f, crackPaint)
            }
            canvas.restore()
        }
    }

    private fun drawCoalBodies(canvas: Canvas) {
        logPaint.color = 0xFF1C0E09.toInt()
        for (c in coals) canvas.drawCircle(cx + c[0] * u, baseY + c[1] * u, c[2] * u, logPaint)
    }

    // The glow is additive and drawn after the logs, so the bed seems to light them from below.
    private fun drawCoalGlow(canvas: Canvas, sprites: GlowSprites, h: Float) {
        for (c in coals) {
            val x = cx + c[0] * u
            val y = baseY + c[1] * u
            val r = c[2] * u
            val pulse = 0.65f + 0.35f * sin(time * (1.1f + c[3] * 0.2f) + c[3])
            val temp = (0.12f + 0.6f * h * pulse).coerceIn(0f, 1f)
            addPaint.alpha = ((0.45f + 0.55f * h) * pulse * 255).toInt().coerceIn(0, 255)
            val g = r * 1.7f
            rect.set(x - g, y - g, x + g, y + g)
            canvas.drawBitmap(sprites.sprites[(temp * (BUCKETS - 1)).toInt()], null, rect, addPaint)
        }
    }

    private fun spawnFlame(h: Float) {
        if (flames >= MAX_FLAMES) return
        val i = flames++
        fx[i] = cx + gauss() * u * (0.03f + 0.06f * h)
        fy[i] = baseY - rnd.nextFloat() * u * 0.02f
        fvx[i] = gauss() * u * 0.02f
        fvy[i] = -u * (0.12f + 0.30f * h) * (0.7f + 0.6f * rnd.nextFloat())
        fAge[i] = 0f
        fLife[i] = (0.45f + 0.6f * rnd.nextFloat()) * (0.55f + 0.75f * h)
        fSize[i] = u * (0.045f + 0.075f * h) * (0.7f + 0.6f * rnd.nextFloat())
        fHeat[i] = (h * (0.8f + 0.4f * rnd.nextFloat()) + 0.1f).coerceAtMost(1f)
    }

    private fun spawnSpark() {
        if (sparks >= MAX_SPARKS) return
        val h = sim.heat
        val i = sparks++
        sx[i] = cx + gauss() * u * 0.04f
        sy[i] = baseY - u * (0.05f * h + 0.12f * rnd.nextFloat())
        svx[i] = gauss() * u * 0.08f + sim.wind * u * 0.15f
        svy[i] = -u * (0.25f + 0.35f * rnd.nextFloat()) * (0.5f + h)
        sAge[i] = 0f
        sLife[i] = 0.7f + 0.9f * rnd.nextFloat()
        sPhase[i] = rnd.nextFloat() * 6.28f
    }

    private fun spawnSmoke() {
        if (smokes >= MAX_SMOKE) return
        val i = smokes++
        mx[i] = cx + gauss() * u * 0.02f
        my[i] = baseY - u * 0.05f
        mvx[i] = gauss() * u * 0.01f
        mAge[i] = 0f
        mLife[i] = 3f + 2f * rnd.nextFloat()
    }

    // Swap-remove: move the last particle into the freed slot.
    private fun removeFlame(i: Int) {
        val last = --flames
        fx[i] = fx[last]; fy[i] = fy[last]; fvx[i] = fvx[last]; fvy[i] = fvy[last]
        fAge[i] = fAge[last]; fLife[i] = fLife[last]; fSize[i] = fSize[last]; fHeat[i] = fHeat[last]
    }

    private fun removeSpark(i: Int) {
        val last = --sparks
        sx[i] = sx[last]; sy[i] = sy[last]; svx[i] = svx[last]; svy[i] = svy[last]
        sAge[i] = sAge[last]; sLife[i] = sLife[last]; sPhase[i] = sPhase[last]
    }

    private fun removeSmoke(i: Int) {
        val last = --smokes
        mx[i] = mx[last]; my[i] = my[last]; mvx[i] = mvx[last]; mAge[i] = mAge[last]; mLife[i] = mLife[last]
    }

    private fun gauss(): Float = (rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() - 1.5f) * 1.2f

    override fun release() {
        sprites?.release()
        smokeSprite?.release()
        sprites = null
        smokeSprite = null
    }

    companion object {
        private const val MAX_FLAMES = 240
        private const val MAX_SPARKS = 80
        private const val MAX_SMOKE = 30
        private const val COALS = 14
        private const val BUCKETS = 24

        private val FIRE_STOPS = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
        private val FIRE_COLORS = intArrayOf(
            0xFF2A0500.toInt(), 0xFF7A1000.toInt(), 0xFFC42A00.toInt(),
            0xFFFF6A10.toInt(), 0xFFFFB347.toInt(), 0xFFFFF1C4.toInt(),
        )

        /** Colour of fire at temperature t: deep red embers through orange to a pale yellow core. */
        fun fireColor(t: Float): Int {
            val k = t.coerceIn(0f, 1f)
            for (s in 1 until FIRE_STOPS.size) {
                if (k <= FIRE_STOPS[s]) {
                    val local = (k - FIRE_STOPS[s - 1]) / (FIRE_STOPS[s] - FIRE_STOPS[s - 1])
                    return GlowSprites.mix(FIRE_COLORS[s - 1], FIRE_COLORS[s], local)
                }
            }
            return FIRE_COLORS.last()
        }

        /** Fade a how-to caption in, hold it for a few seconds, then fade it away. */
        fun introAlpha(t: Float): Float = when {
            t < 0.6f -> t / 0.6f
            t < 3.5f -> 1f
            t < 5f -> 1f - (t - 3.5f) / 1.5f
            else -> 0f
        }
    }
}
