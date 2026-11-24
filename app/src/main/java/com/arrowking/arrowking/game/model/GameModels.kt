package com.arrowking.arrowking.game.model

import kotlin.math.abs

enum class Direction(val dx: Int, val dy: Int) {
    UP(0, -1),
    RIGHT(1, 0),
    DOWN(0, 1),
    LEFT(-1, 0);

    fun opposite(): Direction = when (this) {
        UP -> DOWN
        RIGHT -> LEFT
        DOWN -> UP
        LEFT -> RIGHT
    }

    companion object {
        fun between(from: Cell, to: Cell): Direction? {
            val dx = to.x - from.x
            val dy = to.y - from.y
            return when {
                dx == 0 && dy == -1 -> UP
                dx == 1 && dy == 0 -> RIGHT
                dx == 0 && dy == 1 -> DOWN
                dx == -1 && dy == 0 -> LEFT
                else -> null
            }
        }
    }
}

data class Cell(val x: Int, val y: Int) {
    fun step(dir: Direction): Cell = Cell(x + dir.dx, y + dir.dy)

    fun isAdjacentTo(other: Cell): Boolean =
        abs(x - other.x) + abs(y - other.y) == 1
}

/**
 * A snake-arrow on the board.
 * [cells] is ordered from tail (index 0) to head (last index).
 */
data class Arrow(
    val id: Int,
    val cells: List<Cell>,
    val headDirection: Direction,
    val colorIndex: Int
) {
    val head: Cell get() = cells.last()
    val tail: Cell get() = cells.first()
    val length: Int get() = cells.size
}

data class LevelMask(
    val shapeId: String,
    val shapeName: String,
    val width: Int,
    val height: Int,
    val cells: Set<Cell>,
    val isBoss: Boolean
) {
    val cellCount: Int get() = cells.size
    val fillRatio: Float get() = if (width * height == 0) 0f else cells.size.toFloat() / (width * height)
}

data class LevelDifficulty(
    val level: Int,
    val gridMaxSide: Int,
    val minLen: Int,
    val maxLen: Int,
    val turnChance: Float,
    val minDependencyDepth: Int,
    val minBlockedRatio: Float,
    val maxStartFree: Int,
    val maxAvgFree: Float,
    val estimatedSolveSeconds: Int
)

data class LevelMetrics(
    val gridWidth: Int,
    val gridHeight: Int,
    val arrowCount: Int,
    val dependencyDepth: Int,
    val startFreeCount: Int,
    val avgFreeCount: Float,
    val blockedRatio: Float,
    val singleCellRatio: Float,
    val estimatedSolveSeconds: Int,
    val shapeName: String
)

data class GeneratedLevel(
    val levelNumber: Int,
    val seed: Long,
    val mask: LevelMask,
    val arrows: List<Arrow>,
    val metrics: LevelMetrics,
    val isBoss: Boolean
)
