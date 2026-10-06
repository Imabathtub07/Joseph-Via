package com.josephvia.bezelcalm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class MazeTest {

    @Test
    fun everyMazeIsPerfect() {
        for (size in 5..9) for (seed in 0L until 40L) {
            val maze = Maze(size, Random(seed))
            var passages = 0
            for (y in 0 until size) for (x in 0 until size) {
                if (maze.isOpen(x, y, 1, 0)) passages++
                if (maze.isOpen(x, y, 0, 1)) passages++
            }
            // A spanning tree over n cells has exactly n - 1 edges, and every cell is reachable.
            assertEquals(size * size - 1, passages)
            assertTrue(maze.distancesFrom(maze.startX, maze.startY).all { it >= 0 })
        }
    }

    @Test
    fun goalIsTheFurthestDeadEnd() {
        for (seed in 0L until 40L) {
            val maze = Maze(7, Random(seed))
            val dist = maze.distancesFrom(maze.startX, maze.startY)
            assertEquals(dist.max(), dist[maze.goalY * maze.size + maze.goalX])
            val exits = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1).count { (dx, dy) -> maze.isOpen(maze.goalX, maze.goalY, dx, dy) }
            assertEquals(1, exits)
        }
    }

    @Test
    fun ballNeverPassesThroughWalls() {
        val rnd = Random(7)
        for (seed in 0L until 12L) {
            val maze = Maze(5 + (seed % 5).toInt(), Random(seed))
            val physics = MazePhysics(maze)
            val ball = Ball(maze.startX + 0.5f, maze.startY + 0.5f)
            var cellX = maze.startX
            var cellY = maze.startY
            var angle = 0.0
            var spin = 0.0
            // 90 seconds of erratic spinning, including very fast bursts.
            repeat(90 * 60) {
                if (rnd.nextFloat() < 0.05f) spin = (rnd.nextDouble() - 0.5) * 20.0
                angle += spin / 60.0
                physics.step(ball, (MazePhysics.GRAVITY * sin(angle)).toFloat(), (MazePhysics.GRAVITY * cos(angle)).toFloat(), 1f / 60f)
                val nx = floor(ball.x).toInt()
                val ny = floor(ball.y).toInt()
                if (nx != cellX || ny != cellY) {
                    val dx = nx - cellX
                    val dy = ny - cellY
                    assertEquals("moved diagonally", 1, kotlin.math.abs(dx) + kotlin.math.abs(dy))
                    assertTrue("rolled through a wall at ($cellX,$cellY)+($dx,$dy)", maze.isOpen(cellX, cellY, dx, dy))
                    cellX = nx
                    cellY = ny
                }
                assertTrue(ball.x > 0f && ball.x < maze.size && ball.y > 0f && ball.y < maze.size)
            }
        }
    }

    @Test
    fun ballCanBeTippedAllTheWayHome() {
        for (seed in 0L until 30L) {
            val maze = Maze(5 + (seed % 5).toInt(), Random(seed))
            val physics = MazePhysics(maze)
            val ball = Ball(maze.startX + 0.5f, maze.startY + 0.5f)
            val toGoal = maze.distancesFrom(maze.goalX, maze.goalY)
            var reached = false
            var t = 0f
            while (t < 120f && !reached) {
                // Aim gravity at the centre of the next cell on the route, as a player would by turning.
                val x = floor(ball.x).toInt().coerceIn(0, maze.size - 1)
                val y = floor(ball.y).toInt().coerceIn(0, maze.size - 1)
                var wx = x + 0.5f
                var wy = y + 0.5f
                for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    if (maze.isOpen(x, y, dx, dy) && toGoal[(y + dy) * maze.size + x + dx] < toGoal[y * maze.size + x]) {
                        wx = x + dx + 0.5f
                        wy = y + dy + 0.5f
                    }
                }
                val angle = atan2((wx - ball.x).toDouble(), (wy - ball.y).toDouble())
                physics.step(ball, (MazePhysics.GRAVITY * sin(angle)).toFloat(), (MazePhysics.GRAVITY * cos(angle)).toFloat(), 1f / 60f)
                reached = physics.reachedGoal(ball)
                t += 1f / 60f
            }
            assertTrue("seed $seed never reached the goal", reached)
        }
    }

    @Test
    fun ballRestsInsideTheGoalCellWhenTippedIntoIt() {
        // The goal is a dead end, so leaning into it must leave the ball within the capture radius.
        val reach = MazePhysics.BALL_RADIUS + MazePhysics.WALL_HALF_WIDTH
        assertTrue(0.5f - reach < MazePhysics.GOAL_RADIUS)
        // And the ball must fit through a gap between walls.
        assertTrue(2 * MazePhysics.BALL_RADIUS < 1f - 2 * MazePhysics.WALL_HALF_WIDTH)
        assertTrue(sqrt(2f) * 0.64f < 1f)
    }
}
