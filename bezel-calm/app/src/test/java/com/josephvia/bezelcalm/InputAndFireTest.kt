package com.josephvia.bezelcalm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InputAndFireTest {

    @Test
    fun steadyGentleTurningKeepsAGoodFire() {
        val sim = EmberSim()
        repeat(60 * 60) { frame ->
            if (frame % 60 == 0) sim.breathe(1f)
            sim.step(1f / 60f)
        }
        assertTrue("heat ${sim.heat}", sim.heat in 0.55f..0.9f)
    }

    @Test
    fun fastSpinningRoarsAndSilenceFadesToEmbers() {
        val sim = EmberSim()
        repeat(20 * 60) { frame ->
            if (frame % 10 == 0) sim.breathe(1f)
            sim.step(1f / 60f)
        }
        assertTrue("heat ${sim.heat}", sim.heat > 0.9f)
        repeat(30 * 60) { sim.step(1f / 60f) }
        assertTrue("should still be glowing after 30 s, heat ${sim.heat}", sim.heat > 0.3f)
        repeat(180 * 60) { sim.step(1f / 60f) }
        assertEquals(EmberSim.MIN_HEAT, sim.heat, 1e-4f)
    }

    @Test
    fun heatStaysInRangeUnderAnyInput() {
        val sim = EmberSim()
        val rnd = Random(3)
        repeat(20_000) {
            if (rnd.nextFloat() < 0.3f) sim.breathe((rnd.nextFloat() - 0.5f) * 40f)
            sim.step(rnd.nextFloat() * 0.05f)
            assertTrue(sim.heat in EmberSim.MIN_HEAT..1f)
            assertTrue(sim.wind in -1f..1f)
        }
    }

    @Test
    fun windFollowsTurnDirection() {
        val sim = EmberSim()
        repeat(5) { sim.breathe(1f) }
        assertTrue(sim.wind > 0f)
        repeat(12) { sim.breathe(-1f) }
        assertTrue(sim.wind < 0f)
    }

    @Test
    fun stepperTakesOneStepPerClick() {
        val s = DetentStepper()
        assertEquals(1, s.feed(1f))
        assertEquals(1, s.feed(1f))
        assertEquals(-1, s.feed(-1f))
        assertEquals(3, s.feed(3f))
    }

    @Test
    fun stepperAddsUpSmallCrownTicks() {
        val s = DetentStepper()
        assertEquals(0, s.feed(0.25f))
        assertEquals(0, s.feed(0.25f))
        assertEquals(0, s.feed(0.25f))
        assertEquals(1, s.feed(0.25f))
        // Reversing drops the leftover from the other direction.
        assertEquals(0, s.feed(0.5f))
        assertEquals(0, s.feed(-0.5f))
        assertEquals(-1, s.feed(-0.5f))
    }

    @Test
    fun malaCountsRoundsAndStopsAtTheStart() {
        val m = MalaCounter()
        assertEquals(0, m.turn(-3))
        assertEquals(0, m.position)
        var rounds = 0
        repeat(MalaCounter.BEADS) { rounds += m.turn(1) }
        assertEquals(1, rounds)
        assertEquals(0, m.count)
        assertEquals(1, m.rounds)
        assertEquals(0, m.turn(5))
        assertEquals(5, m.count)
    }
}
