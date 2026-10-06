package com.josephvia.bezelcalm

import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A perfect maze (exactly one route between any two cells) on a [size] x [size] grid.
 * Coordinates are in cells: cell (x, y) spans x..x+1 and y..y+1, with y growing downward.
 */
class Maze(val size: Int, random: Random) {

    /** wallEast[y][x] is the wall between (x, y) and (x + 1, y). The last column is the outer wall. */
    private val wallEast = Array(size) { BooleanArray(size) { true } }

    /** wallSouth[y][x] is the wall between (x, y) and (x, y + 1). The last row is the outer wall. */
    private val wallSouth = Array(size) { BooleanArray(size) { true } }

    val startX: Int
    val startY: Int
    val goalX: Int
    val goalY: Int

    /** Wall runs as x1, y1, x2, y2 quadruples, collinear neighbours merged. */
    val segments: FloatArray

    init {
        require(size >= 2) { "maze needs at least 2x2 cells" }
        carve(random)
        val corner = random.nextInt(4)
        startX = if (corner % 2 == 0) 0 else size - 1
        startY = if (corner < 2) 0 else size - 1
        // The goal is the cell furthest from the start, which in a perfect maze is always a dead end.
        val dist = distancesFrom(startX, startY)
        var best = 0
        for (i in dist.indices) if (dist[i] > dist[best]) best = i
        goalX = best % size
        goalY = best / size
        segments = buildSegments()
    }

    /** True if the ball can pass from (x, y) to the orthogonal neighbour (x + dx, y + dy). */
    fun isOpen(x: Int, y: Int, dx: Int, dy: Int): Boolean {
        val nx = x + dx
        val ny = y + dy
        if (nx !in 0 until size || ny !in 0 until size) return false
        return when {
            dx == 1 && dy == 0 -> !wallEast[y][x]
            dx == -1 && dy == 0 -> !wallEast[y][nx]
            dx == 0 && dy == 1 -> !wallSouth[y][x]
            dx == 0 && dy == -1 -> !wallSouth[ny][x]
            else -> false
        }
    }

    /** Steps from (x, y) to every cell, indexed y * size + x. */
    fun distancesFrom(x: Int, y: Int): IntArray {
        val dist = IntArray(size * size) { -1 }
        val queue = IntArray(size * size)
        var head = 0
        var tail = 0
        dist[y * size + x] = 0
        queue[tail++] = y * size + x
        while (head < tail) {
            val cell = queue[head++]
            val cx = cell % size
            val cy = cell / size
            for (d in DIRECTIONS) {
                if (!isOpen(cx, cy, d[0], d[1])) continue
                val next = (cy + d[1]) * size + cx + d[0]
                if (dist[next] >= 0) continue
                dist[next] = dist[cell] + 1
                queue[tail++] = next
            }
        }
        return dist
    }

    // Iterative depth-first "recursive backtracker": long winding corridors, nice for rolling.
    private fun carve(random: Random) {
        val visited = BooleanArray(size * size)
        val stack = IntArray(size * size)
        var top = 0
        val start = random.nextInt(size * size)
        visited[start] = true
        stack[top++] = start
        val options = IntArray(4)
        while (top > 0) {
            val cell = stack[top - 1]
            val x = cell % size
            val y = cell / size
            var n = 0
            if (x > 0 && !visited[cell - 1]) options[n++] = 0
            if (x < size - 1 && !visited[cell + 1]) options[n++] = 1
            if (y > 0 && !visited[cell - size]) options[n++] = 2
            if (y < size - 1 && !visited[cell + size]) options[n++] = 3
            if (n == 0) {
                top--
                continue
            }
            val next = when (options[random.nextInt(n)]) {
                0 -> { wallEast[y][x - 1] = false; cell - 1 }
                1 -> { wallEast[y][x] = false; cell + 1 }
                2 -> { wallSouth[y - 1][x] = false; cell - size }
                else -> { wallSouth[y][x] = false; cell + size }
            }
            visited[next] = true
            stack[top++] = next
        }
    }

    private fun buildSegments(): FloatArray {
        val out = ArrayList<Float>()
        fun add(x1: Int, y1: Int, x2: Int, y2: Int) {
            out += x1.toFloat(); out += y1.toFloat(); out += x2.toFloat(); out += y2.toFloat()
        }
        // Horizontal lines y = j.
        for (j in 0..size) {
            var runStart = -1
            for (x in 0..size) {
                val wall = x < size && (j == 0 || j == size || wallSouth[j - 1][x])
                if (wall && runStart < 0) runStart = x
                if (!wall && runStart >= 0) { add(runStart, j, x, j); runStart = -1 }
            }
        }
        // Vertical lines x = i.
        for (i in 0..size) {
            var runStart = -1
            for (y in 0..size) {
                val wall = y < size && (i == 0 || i == size || wallEast[y][i - 1])
                if (wall && runStart < 0) runStart = y
                if (!wall && runStart >= 0) { add(i, runStart, i, y); runStart = -1 }
            }
        }
        return out.toFloatArray()
    }

    private companion object {
        val DIRECTIONS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))
    }
}

/** The rolling ball, in maze cells. */
class Ball(var x: Float, var y: Float) {
    var vx = 0f
    var vy = 0f
}

/**
 * Rolls a [Ball] through the walls of a [Maze] under a gravity vector given in maze space.
 * Walls are treated as capsules (thick lines with round ends) so corners never snag.
 */
class MazePhysics(private val maze: Maze) {

    fun step(ball: Ball, gx: Float, gy: Float, dt: Float) {
        var remaining = dt
        while (remaining > 1e-6f) {
            val h = if (remaining > MAX_SUBSTEP) MAX_SUBSTEP else remaining
            remaining -= h
            ball.vx += gx * h
            ball.vy += gy * h
            val damping = exp(-ROLLING_FRICTION * h)
            ball.vx *= damping
            ball.vy *= damping
            val speed = sqrt(ball.vx * ball.vx + ball.vy * ball.vy)
            if (speed > MAX_SPEED) {
                ball.vx *= MAX_SPEED / speed
                ball.vy *= MAX_SPEED / speed
            }
            ball.x += ball.vx * h
            ball.y += ball.vy * h
            collide(ball)
        }
    }

    fun reachedGoal(ball: Ball): Boolean {
        val dx = ball.x - (maze.goalX + 0.5f)
        val dy = ball.y - (maze.goalY + 0.5f)
        return dx * dx + dy * dy < GOAL_RADIUS * GOAL_RADIUS
    }

    private fun collide(ball: Ball) {
        val reach = BALL_RADIUS + WALL_HALF_WIDTH
        val s = maze.segments
        // Two passes let a ball wedged into a corner settle against both walls.
        repeat(2) {
            var i = 0
            while (i < s.size) {
                val ax = s[i]
                val ay = s[i + 1]
                val bx = s[i + 2]
                val by = s[i + 3]
                i += 4
                // Cheap reject using the segment's bounding box.
                if (ball.x < minOf(ax, bx) - reach || ball.x > maxOf(ax, bx) + reach) continue
                if (ball.y < minOf(ay, by) - reach || ball.y > maxOf(ay, by) + reach) continue
                val abx = bx - ax
                val aby = by - ay
                val lengthSq = abx * abx + aby * aby
                var t = if (lengthSq > 0f) ((ball.x - ax) * abx + (ball.y - ay) * aby) / lengthSq else 0f
                t = t.coerceIn(0f, 1f)
                val dx = ball.x - (ax + abx * t)
                val dy = ball.y - (ay + aby * t)
                val distSq = dx * dx + dy * dy
                if (distSq >= reach * reach || distSq < 1e-12f) continue
                val dist = sqrt(distSq)
                val nx = dx / dist
                val ny = dy / dist
                val push = reach - dist
                ball.x += nx * push
                ball.y += ny * push
                val vn = ball.vx * nx + ball.vy * ny
                if (vn < 0f) {
                    ball.vx -= (1f + RESTITUTION) * vn * nx
                    ball.vy -= (1f + RESTITUTION) * vn * ny
                }
            }
        }
        // The outer walls already hold the ball in; this only guards against float drift.
        ball.x = ball.x.coerceIn(reach, maze.size - reach)
        ball.y = ball.y.coerceIn(reach, maze.size - reach)
    }

    companion object {
        const val BALL_RADIUS = 0.26f
        const val WALL_HALF_WIDTH = 0.06f
        const val GOAL_RADIUS = 0.3f
        const val GRAVITY = 7f
        private const val ROLLING_FRICTION = 0.7f
        private const val MAX_SPEED = 6f
        private const val RESTITUTION = 0.3f
        private const val MAX_SUBSTEP = 1f / 240f
    }
}
