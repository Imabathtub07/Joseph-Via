package com.josephvia.bezelcalm

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * A square maze that turns with the bezel, one click = 15 degrees, just like the bezel itself.
 * Gravity always pulls toward the bottom of the screen, so turning the maze tips the ball
 * through the corridors. Roll it into the glowing cell and a new maze drifts in.
 */
class LabyrinthScene(private val onSolved: () -> Unit) : Scene() {

    private val rnd = Random.Default
    private var solvedCount = 0
    private var maze = newMaze()
    private var physics = MazePhysics(maze)
    private var ball = startBall()

    /** Where the bezel says the maze should be, and where it is drawn (eased toward the target). */
    private var targetDeg = 0f
    private var shownDeg = 0f
    private var time = 0f

    private enum class Phase { ROLLING, SINKING, FADING_OUT, FADING_IN }
    private var phase = Phase.FADING_IN
    private var phaseTime = 0f

    // Recent ball positions for a faint trail, as a ring buffer in maze units.
    private val trailX = FloatArray(TRAIL)
    private val trailY = FloatArray(TRAIL)
    private var trailHead = 0
    private var trailLen = 0
    private var trailClock = 0f

    private var cell = 1f
    private val wallPath = Path()
    private val wallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFF8FD8CF.toInt()
    }
    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0C1626.toInt() }
    private val backPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF0D0.toInt() }
    private val captionPaint = Ink.textPaint()
    private val countPaint = Ink.textPaint()

    override fun onResize() {
        // Size the square so its corners stay on screen at every angle.
        cell = u * MAZE_SPAN / maze.size
        rebuildWallPath()
        backPaint.shader = RadialGradient(
            cx, cy, u * 0.5f,
            intArrayOf(0xFF0E1830.toInt(), 0xFF070B18.toInt(), 0xFF000000.toInt()),
            floatArrayOf(0f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
        goalPaint.shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(0xFFFFE3A0.toInt(), 0x88FFB347.toInt(), 0x00FF8A3D),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        ballPaint.shader = RadialGradient(
            -0.35f, -0.4f, 1.3f,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFF3E6CF.toInt(), 0xFFA8957A.toInt()),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onRotate(clicks: Float) {
        targetDeg += clicks * DEGREES_PER_CLICK
    }

    override fun update(dt: Float): Boolean {
        time += dt
        phaseTime += dt
        shownDeg += (targetDeg - shownDeg) * (1f - exp(-dt * 14f))

        when (phase) {
            Phase.FADING_IN -> {
                roll(dt)
                if (phaseTime >= FADE_TIME) setPhase(Phase.ROLLING)
            }
            Phase.ROLLING -> {
                roll(dt)
                if (physics.reachedGoal(ball)) {
                    setPhase(Phase.SINKING)
                    solvedCount++
                    onSolved()
                }
            }
            Phase.SINKING -> {
                // Draw the ball into the centre of the goal while it shrinks away.
                val k = 1f - exp(-dt * 10f)
                ball.x += (maze.goalX + 0.5f - ball.x) * k
                ball.y += (maze.goalY + 0.5f - ball.y) * k
                if (phaseTime >= SINK_TIME) setPhase(Phase.FADING_OUT)
            }
            Phase.FADING_OUT -> if (phaseTime >= FADE_TIME) {
                maze = newMaze()
                physics = MazePhysics(maze)
                ball = startBall()
                trailLen = 0
                onResize()
                setPhase(Phase.FADING_IN)
            }
        }
        return true
    }

    private fun roll(dt: Float) {
        // Screen-down gravity expressed in the maze's own (rotated) frame.
        val a = Math.toRadians(shownDeg.toDouble())
        val gx = (MazePhysics.GRAVITY * sin(a)).toFloat()
        val gy = (MazePhysics.GRAVITY * cos(a)).toFloat()
        physics.step(ball, gx, gy, dt)

        trailClock += dt
        if (trailClock >= 1f / 30f) {
            trailClock = 0f
            trailX[trailHead] = ball.x
            trailY[trailHead] = ball.y
            trailHead = (trailHead + 1) % TRAIL
            if (trailLen < TRAIL) trailLen++
        }
    }

    override fun draw(canvas: Canvas) {
        canvas.drawColor(0xFF000000.toInt())
        canvas.drawCircle(cx, cy, u * 0.5f, backPaint)

        val mazeAlpha = when (phase) {
            Phase.FADING_IN -> (phaseTime / FADE_TIME).coerceIn(0f, 1f)
            Phase.FADING_OUT -> 1f - (phaseTime / FADE_TIME).coerceIn(0f, 1f)
            else -> 1f
        }
        val half = maze.size / 2f

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(shownDeg)

        floorPaint.alpha = (mazeAlpha * 255).toInt()
        canvas.drawRect(-half * cell, -half * cell, half * cell, half * cell, floorPaint)

        // The goal: a slow warm pulse, which blooms when the ball arrives.
        val bloom = when (phase) {
            Phase.SINKING -> 1f + 0.8f * (phaseTime / SINK_TIME).coerceAtMost(1f)
            Phase.FADING_OUT -> 1.8f
            else -> 1f
        }
        val pulse = 0.75f + 0.25f * sin(time * 2f)
        val gx = (maze.goalX + 0.5f - half) * cell
        val gy = (maze.goalY + 0.5f - half) * cell
        canvas.save()
        canvas.translate(gx, gy)
        val gr = cell * 0.55f * bloom
        canvas.scale(gr, gr)
        goalPaint.alpha = (mazeAlpha * pulse * 255).toInt()
        canvas.drawCircle(0f, 0f, 1f, goalPaint)
        canvas.restore()

        // Fading trail behind the ball.
        for (k in 0 until trailLen) {
            val idx = (trailHead - 1 - k + TRAIL) % TRAIL
            val fade = 1f - k / TRAIL.toFloat()
            trailPaint.alpha = (fade * fade * 70 * mazeAlpha).toInt()
            canvas.drawCircle((trailX[idx] - half) * cell, (trailY[idx] - half) * cell, cell * 0.07f * (0.5f + fade), trailPaint)
        }

        wallPaint.strokeWidth = cell * MazePhysics.WALL_HALF_WIDTH * 2f
        wallPaint.alpha = (mazeAlpha * 235).toInt()
        canvas.drawPath(wallPath, wallPaint)
        canvas.restore()

        // The ball is drawn unrotated so its highlight always faces the top of the screen.
        val a = Math.toRadians(shownDeg.toDouble())
        val bx = (ball.x - half) * cell
        val by = (ball.y - half) * cell
        val screenX = cx + (bx * cos(a) - by * sin(a)).toFloat()
        val screenY = cy + (bx * sin(a) + by * cos(a)).toFloat()
        val shrink = when (phase) {
            Phase.SINKING -> 1f - (phaseTime / SINK_TIME).coerceIn(0f, 1f)
            Phase.FADING_OUT -> 0f
            Phase.FADING_IN -> sqrt((phaseTime / FADE_TIME).coerceIn(0f, 1f))
            Phase.ROLLING -> 1f
        }
        val br = cell * MazePhysics.BALL_RADIUS * shrink
        if (br > 0.5f) {
            canvas.save()
            canvas.translate(screenX, screenY)
            canvas.scale(br, br)
            canvas.drawCircle(0f, 0f, 1f, ballPaint)
            canvas.restore()
        }

        val intro = if (solvedCount == 0) EmberScene.introAlpha(time) else 0f
        Ink.drawCaption(canvas, "turn to tip the ball", cx, cy - u * 0.40f, u * 0.053f, 0xFFD6EEEA.toInt(), intro * 0.85f, captionPaint, backing = true)
        if (solvedCount > 0 && phase == Phase.FADING_IN) {
            Ink.drawCaption(canvas, "$solvedCount", cx, cy - u * 0.405f, u * 0.05f, 0xFFFFE3A0.toInt(), mazeAlpha * 0.7f, countPaint, backing = true)
        }
        Ink.drawBackKeyHint(canvas, cx, cy, u, "menu", Mode.LABYRINTH.accent, EmberScene.introAlpha(time) * 0.7f, backing = true)
    }

    private fun rebuildWallPath() {
        wallPath.reset()
        val half = maze.size / 2f
        val s = maze.segments
        var i = 0
        while (i < s.size) {
            wallPath.moveTo((s[i] - half) * cell, (s[i + 1] - half) * cell)
            wallPath.lineTo((s[i + 2] - half) * cell, (s[i + 3] - half) * cell)
            i += 4
        }
    }

    private fun setPhase(p: Phase) {
        phase = p
        phaseTime = 0f
    }

    // Mazes grow slowly from 5x5 up to 9x9 as you solve them.
    private fun newMaze() = Maze((5 + (solvedCount + 1) / 2).coerceAtMost(9), rnd)

    private fun startBall() = Ball(maze.startX + 0.5f, maze.startY + 0.5f)

    companion object {
        const val DEGREES_PER_CLICK = 15f
        /** Width of the maze square as a fraction of the screen; its diagonal must stay under 1. */
        private const val MAZE_SPAN = 0.64f
        private const val TRAIL = 36
        private const val FADE_TIME = 0.9f
        private const val SINK_TIME = 0.7f
    }
}
