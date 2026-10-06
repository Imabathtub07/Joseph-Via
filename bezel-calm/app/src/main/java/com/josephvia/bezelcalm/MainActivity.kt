package com.josephvia.bezelcalm

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import android.window.OnBackInvokedDispatcher

/**
 * Bezel Calm: something soothing to do with the rotating bezel instead of scrolling through
 * tiles and notifications.
 *
 * Controls are only the bezel and the Back key. The Home key is the system's and always
 * leaves the app. Touch is ignored on purpose, so a palm or sleeve can't change anything.
 */
class MainActivity : Activity(), CalmView.Host {

    private lateinit var calm: CalmView
    private val handler = Handler(Looper.getMainLooper())
    private var mode: Mode? = null
    private val letScreenSleep = Runnable { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        calm = CalmView(this, this)
        setContentView(calm)
        calm.requestFocus()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                calm.back()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        stayAwake()
    }

    override fun onPause() {
        handler.removeCallbacks(letScreenSleep)
        super.onPause()
    }

    // Android 12 and older (Wear OS 3) deliver the Back key here instead of the callback above.
    @Deprecated("Replaced by OnBackInvokedCallback on Android 13+")
    override fun onBackPressed() {
        calm.back()
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_SCROLL && ev.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            // Wear OS reports a clockwise turn as negative scroll; flip it so clockwise is positive.
            val clicks = -ev.getAxisValue(MotionEvent.AXIS_SCROLL)
            if (clicks != 0f) {
                calm.rotate(clicks)
                stayAwake()
            }
            // Always consume the bezel so nothing else on the watch scrolls.
            return true
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean = true

    override fun onModeChanged(mode: Mode?) {
        this.mode = mode
        // In the Void the panel drops to its lowest brightness; everywhere else uses the user's setting.
        window.attributes = window.attributes.apply {
            screenBrightness = if (mode == Mode.VOID) VOID_BRIGHTNESS else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
        stayAwake()
    }

    override fun lastMode(): Mode {
        val name = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LAST_MODE, null)
        return Mode.entries.firstOrNull { it.name == name } ?: Mode.EMBER
    }

    override fun saveMode(mode: Mode) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LAST_MODE, mode.name).apply()
    }

    /**
     * Keep the screen on while the bezel is in use. The Void keeps it on indefinitely: if the
     * screen were allowed to sleep, the next bezel turn would wake it to a bright watch face.
     * Elsewhere the watch may sleep after a few idle minutes to save battery.
     */
    private fun stayAwake() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.removeCallbacks(letScreenSleep)
        if (mode != Mode.VOID) handler.postDelayed(letScreenSleep, IDLE_SCREEN_MS)
    }

    private companion object {
        const val PREFS = "bezel_calm"
        const val KEY_LAST_MODE = "last_mode"
        const val IDLE_SCREEN_MS = 3 * 60 * 1000L
        const val VOID_BRIGHTNESS = 0.01f
    }
}
