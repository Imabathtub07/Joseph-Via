package com.josephvia.bezelcalm

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * The single full-screen view. It owns the current [Scene], runs the frame loop only while
 * something is moving, and switches between the menu and the modes.
 */
class CalmView(context: Context, private val host: Host) : View(context) {

    interface Host {
        /** [mode] is null when the menu is showing. */
        fun onModeChanged(mode: Mode?)
        fun lastMode(): Mode
        fun saveMode(mode: Mode)
    }

    private var scene: Scene = MenuScene(host.lastMode())
    private var mode: Mode? = null
    private var running = false
    private var lastFrameNanos = 0L

    /** Black veil that lifts over a fraction of a second when switching scenes. */
    private var veil = 1f
    private val veilPaint = Paint().apply { color = 0xFF000000.toInt() }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun rotate(clicks: Float) {
        scene.onRotate(clicks)
        if (mode != Mode.VOID) startFrames()
    }

    /** Back key: on the menu it starts the selected mode, anywhere else it returns to the menu. */
    fun back() {
        val menu = scene as? MenuScene
        if (menu != null) {
            val chosen = menu.selected
            host.saveMode(chosen)
            show(sceneFor(chosen), chosen)
        } else {
            show(MenuScene(mode ?: host.lastMode()), null)
        }
    }

    private fun sceneFor(mode: Mode): Scene = when (mode) {
        Mode.EMBER -> EmberScene()
        Mode.LABYRINTH -> LabyrinthScene(onSolved = ::softTick)
        Mode.BEADS -> BeadsScene(onRound = ::softTick)
        Mode.VOID -> VoidScene()
    }

    private fun show(next: Scene, nextMode: Mode?) {
        scene.release()
        scene = next
        mode = nextMode
        if (width > 0 && height > 0) next.resize(width, height)
        veil = 1f
        host.onModeChanged(nextMode)
        startFrames()
    }

    private fun softTick() {
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scene.resize(w, h)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) {
            running = false
            startFrames()
        }
    }

    private fun startFrames() {
        if (running) return
        running = true
        lastFrameNanos = 0L
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 1f / 60f else ((now - lastFrameNanos) / 1e9f).coerceIn(0f, 0.05f)
        lastFrameNanos = now

        val moving = scene.update(dt)
        scene.draw(canvas)

        if (veil > 0f) {
            veilPaint.alpha = (veil * 255).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), veilPaint)
            veil = (veil - dt / VEIL_SECONDS).coerceAtLeast(0f)
        }

        running = moving || veil > 0f
        if (running) postInvalidateOnAnimation()
    }

    private companion object {
        const val VEIL_SECONDS = 0.35f
    }
}
