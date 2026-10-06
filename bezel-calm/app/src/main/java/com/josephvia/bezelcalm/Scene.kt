package com.josephvia.bezelcalm

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** The four things the app can do, in the order they sit on the menu dial. */
enum class Mode(val title: String, val blurb: String, val accent: Int) {
    EMBER("Ember", "breathe on the coals", 0xFFFF8A3D.toInt()),
    LABYRINTH("Labyrinth", "turn the world, roll home", 0xFF7FD6CC.toInt()),
    BEADS("Beads", "one click, one bead", 0xFFC9A27A.toInt()),
    VOID("Void", "dark screen, free spin", 0xFF8C8799.toInt()),
}

/**
 * One full-screen mode. Input is only the bezel ([onRotate]) and the back button, which the
 * host handles. Scenes draw on a round screen centred at ([cx], [cy]) with radius about [u] / 2.
 */
abstract class Scene {
    protected var width = 0f
    protected var height = 0f
    protected var cx = 0f
    protected var cy = 0f

    /** The screen's smaller side. Every size is a fraction of this, so any watch size works. */
    protected var u = 1f

    fun resize(w: Int, h: Int) {
        width = w.toFloat()
        height = h.toFloat()
        cx = width / 2f
        cy = height / 2f
        u = min(width, height).coerceAtLeast(1f)
        onResize()
    }

    protected open fun onResize() {}

    /** Bezel turned by [clicks]; positive is clockwise and one physical click is about 1. */
    abstract fun onRotate(clicks: Float)

    /** Advance by [dt] seconds. Return false once nothing is moving so the screen can rest. */
    abstract fun update(dt: Float): Boolean

    abstract fun draw(canvas: Canvas)

    /** Called when the scene is closed so it can let go of bitmaps. */
    open fun release() {}
}

/** Shared text and hint drawing. */
object Ink {
    val light: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    val regular: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    fun textPaint(typeface: Typeface = light): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    // The Back key sits at about four o'clock on Galaxy Watch Classic models.
    private const val BACK_KEY_ANGLE = 35.0

    private val hintPaint = textPaint(regular).apply { textAlign = Paint.Align.RIGHT }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcBounds = RectF()
    private val backingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF000000.toInt() }
    private val backingRect = RectF()

    // A soft dark pill behind text so it stays readable over walls or flames.
    private fun drawBacking(canvas: Canvas, left: Float, right: Float, baseline: Float, size: Float, alpha: Float) {
        val pad = size * 0.45f
        backingRect.set(left - pad, baseline - size * 1.05f, right + pad, baseline + size * 0.4f)
        backingPaint.alpha = (alpha.coerceIn(0f, 1f) * 215).toInt()
        canvas.drawRoundRect(backingRect, size * 0.7f, size * 0.7f, backingPaint)
    }

    /** A small label pointing at the Back key, e.g. "begin" on the menu. */
    fun drawBackKeyHint(canvas: Canvas, cx: Float, cy: Float, u: Float, label: String, color: Int, alpha: Float, backing: Boolean = false) {
        if (alpha <= 0.01f) return
        val a = Math.toRadians(BACK_KEY_ANGLE)
        val r = u * 0.47f
        arcBounds.set(cx - r, cy - r, cx + r, cy + r)
        tickPaint.color = color
        tickPaint.alpha = (alpha * 200).toInt()
        tickPaint.strokeWidth = u * 0.014f
        canvas.drawArc(arcBounds, (BACK_KEY_ANGLE - 5).toFloat(), 10f, false, tickPaint)

        hintPaint.textSize = u * 0.056f
        hintPaint.color = color
        hintPaint.alpha = (alpha * 255).toInt()
        val tx = cx + (u * 0.40f * cos(a)).toFloat()
        val ty = cy + (u * 0.40f * sin(a)).toFloat() + hintPaint.textSize * 0.35f
        val text = "$label ›"
        if (backing) drawBacking(canvas, tx - hintPaint.measureText(text), tx, ty, hintPaint.textSize, alpha)
        canvas.drawText(text, tx, ty, hintPaint)
    }

    /** A centred caption that fades in and out, used for the short how-to line on entering a mode. */
    fun drawCaption(
        canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int, alpha: Float, paint: Paint,
        backing: Boolean = false,
    ) {
        if (alpha <= 0.01f) return
        paint.textSize = size
        if (backing) {
            val w = paint.measureText(text)
            drawBacking(canvas, x - w / 2, x + w / 2, y, size, alpha)
        }
        paint.color = color
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawText(text, x, y, paint)
    }
}

/**
 * Pre-rendered soft round glows, one per colour, drawn scaled with additive blending.
 * Far cheaper per frame than building gradients for every particle.
 */
class GlowSprites(colors: IntArray, private val sizePx: Int = 64) {
    val sprites: Array<Bitmap> = Array(colors.size) { i -> render(colors[i]) }
    val count get() = sprites.size

    private fun render(color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val r = sizePx / 2f
        val solid = color or 0xFF000000.toInt()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(
            r, r, r,
            intArrayOf(solid, withAlpha(solid, 0.55f), withAlpha(solid, 0.12f), withAlpha(solid, 0f)),
            floatArrayOf(0f, 0.3f, 0.65f, 1f),
            Shader.TileMode.CLAMP,
        )
        c.drawCircle(r, r, r, p)
        return bmp
    }

    fun release() = sprites.forEach { it.recycle() }

    companion object {
        fun withAlpha(color: Int, alpha: Float): Int =
            (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).toInt() shl 24)

        /** Linear blend between two colours, t in 0..1. */
        fun mix(a: Int, b: Int, t: Float): Int {
            val k = t.coerceIn(0f, 1f)
            fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - k) + ((b shr shift) and 0xFF) * k).toInt()
            return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}
