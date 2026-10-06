package com.josephvia.bezelcalm

import kotlin.math.abs
import kotlin.math.exp

/**
 * How hot the fire is. Turning the bezel is breath on the coals: it adds air, air feeds heat,
 * and with no breath the fire slowly sinks back to embers. It never goes fully out.
 *
 * Rough feel: one click a second holds a steady campfire, a few clicks a second makes it roar,
 * and with no input it takes about a minute to fade from a full blaze to embers.
 */
class EmberSim {
    /** 0 = cold, 1 = roaring. */
    var heat = START_HEAT
        private set

    /** Fresh air from recent bezel turns. */
    var air = 0f
        private set

    /** Sideways draft, -1 (left) to 1 (right). Clockwise turns push the flames right. */
    var wind = 0f
        private set

    fun breathe(clicks: Float) {
        air = (air + abs(clicks) * AIR_PER_CLICK).coerceAtMost(AIR_MAX)
        wind = (wind + clicks * WIND_PER_CLICK).coerceIn(-1f, 1f)
    }

    fun step(dt: Float) {
        val gain = GAIN * air * (HEAT_CEILING - heat)
        val loss = COOL_BASE + COOL_PER_HEAT * heat
        heat = (heat + (gain - loss) * dt).coerceIn(MIN_HEAT, 1f)
        air *= exp(-AIR_DECAY * dt)
        wind *= exp(-WIND_DECAY * dt)
    }

    companion object {
        const val MIN_HEAT = 0.04f
        const val START_HEAT = 0.12f
        private const val AIR_PER_CLICK = 0.25f
        private const val AIR_MAX = 2.5f
        private const val AIR_DECAY = 2.5f
        private const val WIND_PER_CLICK = 0.08f
        private const val WIND_DECAY = 1.6f
        private const val GAIN = 0.6f
        private const val HEAT_CEILING = 1.15f
        private const val COOL_BASE = 0.008f
        private const val COOL_PER_HEAT = 0.022f
    }
}
