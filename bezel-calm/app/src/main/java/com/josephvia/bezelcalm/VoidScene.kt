package com.josephvia.bezelcalm

import android.graphics.Canvas

/**
 * Nothing. A black screen that swallows every bezel turn, for spinning in a dark theatre.
 * The screen stays on (black pixels on an AMOLED panel are off) at the lowest brightness, so
 * spinning never wakes up a bright watch face. After a brief, very dim hint it stops drawing.
 */
class VoidScene : Scene() {

    private var time = 0f
    private val captionPaint = Ink.textPaint()

    override fun onRotate(clicks: Float) {
        // Deliberately nothing.
    }

    override fun update(dt: Float): Boolean {
        time += dt
        return time < HINT_SECONDS
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(0xFF000000.toInt())
        if (time >= HINT_SECONDS) return
        val fade = when {
            time < 0.8f -> time / 0.8f
            time < HINT_SECONDS - 1.2f -> 1f
            else -> (HINT_SECONDS - time) / 1.2f
        }.coerceIn(0f, 1f)
        Ink.drawCaption(canvas, "spin freely", cx, cy + u * 0.015f, u * 0.055f, 0xFF5E5A66.toInt(), fade * 0.6f, captionPaint)
        Ink.drawBackKeyHint(canvas, cx, cy, u, "menu", 0xFF5E5A66.toInt(), fade * 0.6f)
    }

    companion object {
        private const val HINT_SECONDS = 3f
    }
}
