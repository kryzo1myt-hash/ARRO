package com.arrowking.arrowking.game.solver

import com.arrowking.arrowking.game.model.Arrow
import com.arrowking.arrowking.game.model.Cell
import com.arrowking.arrowking.game.model.Direction
import com.arrowking.arrowking.game.model.LevelMask
import kotlin.math.max

data class SolverAnalysis(
    val isValidStructure: Boolean,
    val isSolvable: Boolean,
    val removalOrder: List<Int>,
    val startFreeCount: Int,
    val avgFreeCount: Float,
    val dependencyDepth: Int,
    val blockedRatio: Float,
    val singleCellRatio: Float,
    val failureReason: String? = null
)

object LevelSolver {

    fun rayCells(arrow: Arrow, width: Int, height: Int): List<Cell> {
        val result = ArrayList<Cell>(max(width, height))
        var cur = arrow.head.step(arrow.headDirection)
        while (cur.x in 0 until width && cur.y in 0 until height) {
            result.add(cur)
            cur = cur.step(arrow.headDirection)
        }
        return result
    }

    fun isArrowFree(
        arrow: Arrow,
        occupiedByArrowId: Map<Cell, Int>,
        width: Int,
        height: Int
    ): Boolean {
        var cur = arrow.head.step(arrow.headDirection)
        while (cur.x in 0 until width && cur.y in 0 until height) {
            val occupant = occupiedByArrowId[cur]
            if (occupant != null && occupant != arrow.id) {
                return false
            }
            if (occupant == arrow.id) {
                // Self-intersection on ray is structurally invalid / self-blocking
                return false
            }
            cur = cur.step(arrow.headDirection)
        }
        return true
    }

    fun firstBlocker(
        arrow: Arrow,
        occupiedByArrowId: Map<Cell, Int>,
        width: Int,
        height: Int
    ): Pair<Cell, Int>? {
        var cur = arrow.head.step(arrow.headDirection)
        while (cur.x in 0 until width && cur.y in 0 until height) {
            val occupant = occupiedByArrowId[cur]
            if (occupant != null) {
                return cur to occupant
            }
            cur = cur.step(arrow.headDirection)
        }
        return null
    }

    fun validateStructure(arrows: List<Arrow>, mask: LevelMask): String? {
        if (arrows.isEmpty()) return "No arrows"
        val seenCells = HashSet<Cell>(mask.cellCount)
        val arrowIds = HashSet<Int>(arrows.size)

        for (arrow in arrows) {
            if (!arrowIds.add(arrow.id)) return "Duplicate arrow id ${arrow.id}"
            if (arrow.cells.isEmpty()) return "Empty arrow ${arrow.id}"

            val ownSet = HashSet<Cell>(arrow.cells.size)
            for (i in arrow.cells.indices) {
                val c = arrow.cells[i]
                if (c !in mask.cells) return "Cell $c outside mask"
                if (!ownSet.add(c)) return "Repeated cell $c in arrow ${arrow.id}"
                if (!seenCells.add(c)) return "Overlapping cell $c"
                if (i > 0 && !arrow.cells[i - 1].isAdjacentTo(c)) {
                    return "Disconnected step in arrow ${arrow.id}"
                }
            }

            if (arrow.cells.size >= 2) {
                val expectedDir = Direction.between(
                    arrow.cells[arrow.cells.size - 2],
                    arrow.cells.last()
                )
                if (expectedDir != arrow.headDirection) {
                    return "Head direction mismatch in arrow ${arrow.id}"
                }
            }

            var cur = arrow.head.step(arrow.headDirection)
            while (cur.x in 0 until mask.width && cur.y in 0 until mask.height) {
                if (cur in ownSet) return "Self-ray intersection in arrow ${arrow.id}"
                cur = cur.step(arrow.headDirection)
            }
        }

        if (seenCells.size != mask.cells.size) {
            return "Coverage mismatch: ${seenCells.size} / ${mask.cells.size}"
        }
        return null
    }

    fun solveAndAnalyze(arrows: List<Arrow>, mask: LevelMask): SolverAnalysis {
        val structureError = validateStructure(arrows, mask)
        if (structureError != null) {
            return SolverAnalysis(
                isValidStructure = false,
                isSolvable = false,
                removalOrder = emptyList(),
                startFreeCount = 0,
                avgFreeCount = 0f,
                dependencyDepth = 0,
                blockedRatio = 0f,
                singleCellRatio = 1f,
                failureReason = structureError
            )
        }

        val w = mask.width
        val h = mask.height
        val occupiedGrid = IntArray(w * h) { -1 }
        val arrowById = HashMap<Int, Arrow>(arrows.size)
        var singleCellCount = 0

        for (arrow in arrows) {
            arrowById[arrow.id] = arrow
            if (arrow.length == 1) singleCellCount++
            for (c in arrow.cells) {
                occupiedGrid[c.y * w + c.x] = arrow.id
            }
        }

        // Build direct ray dependency DAG: arrowId -> set of blocker arrowIds
        val blockersOf = HashMap<Int, Set<Int>>(arrows.size)
        for (arrow in arrows) {
            val blockers = HashSet<Int>()
            var cur = arrow.head.step(arrow.headDirection)
            while (cur.x in 0 until w && cur.y in 0 until h) {
                val occ = occupiedGrid[cur.y * w + cur.x]
                if (occ != -1 && occ != arrow.id) {
                    blockers.add(occ)
                }
                cur = cur.step(arrow.headDirection)
            }
            blockersOf[arrow.id] = blockers
        }

        val remaining = HashSet<Int>(arrows.size)
        for (a in arrows) remaining.add(a.id)

        val removalOrder = ArrayList<Int>(arrows.size)
        val depthMap = HashMap<Int, Int>(arrows.size)
        var startFreeCount = -1
        var totalFreeSum = 0L
        var stepCount = 0

        // Greedy simulation
        val freeNow = ArrayList<Int>()
        while (remaining.isNotEmpty()) {
            freeNow.clear()
            for (id in remaining) {
                val b = blockersOf[id]!!
                var blocked = false
                for (blockerId in b) {
                    if (blockerId in remaining) {
                        blocked = true
                        break
                    }
                }
                if (!blocked) {
                    freeNow.add(id)
                }
            }

            if (freeNow.isEmpty()) {
                return SolverAnalysis(
                    isValidStructure = true,
                    isSolvable = false,
                    removalOrder = removalOrder,
                    startFreeCount = max(0, startFreeCount),
                    avgFreeCount = if (stepCount == 0) 0f else totalFreeSum.toFloat() / stepCount,
                    dependencyDepth = depthMap.values.maxOrNull() ?: 0,
                    blockedRatio = if (startFreeCount < 0) 1f else 1f - startFreeCount.toFloat() / arrows.size,
                    singleCellRatio = singleCellCount.toFloat() / arrows.size,
                    failureReason = "Deadlock with ${remaining.size} arrows remaining"
                )
            }

            if (startFreeCount < 0) {
                startFreeCount = freeNow.size
            }
            totalFreeSum += freeNow.size
            stepCount++

            // Remove one free arrow per step (lowest remaining id or first free) to measure realistic free-choice curve
            val chosen = freeNow[0]
            remaining.remove(chosen)
            removalOrder.add(chosen)

            val maxBlockerDepth = blockersOf[chosen]!!.maxOfOrNull { depthMap[it] ?: 0 } ?: 0
            depthMap[chosen] = maxBlockerDepth + 1
        }

        val maxDepth = depthMap.values.maxOrNull() ?: 0
        val avgFree = totalFreeSum.toFloat() / max(1, stepCount)
        val blockedRatio = 1f - (startFreeCount.toFloat() / arrows.size)
        val singleRatio = singleCellCount.toFloat() / arrows.size

        return SolverAnalysis(
            isValidStructure = true,
            isSolvable = true,
            removalOrder = removalOrder,
            startFreeCount = startFreeCount,
            avgFreeCount = avgFree,
            dependencyDepth = maxDepth,
            blockedRatio = blockedRatio,
            singleCellRatio = singleRatio
        )
    }

    /**
     * Picks the best currently FREE arrow for a Hint:
     * prefers the free arrow that unlocks the most downstream arrows / longest dependency chain.
     */
    fun pickBestHintArrow(
        remainingArrows: List<Arrow>,
        width: Int,
        height: Int
    ): Arrow? {
        if (remainingArrows.isEmpty()) return null
        val occupied = HashMap<Cell, Int>(remainingArrows.size * 6)
        for (a in remainingArrows) {
            for (c in a.cells) occupied[c] = a.id
        }

        val freeArrows = remainingArrows.filter { isArrowFree(it, occupied, width, height) }
        if (freeArrows.isEmpty()) return null
        if (freeArrows.size == 1) return freeArrows.first()

        // Count how many remaining arrows have each free arrow on their ray
        val directlyUnlocks = HashMap<Int, Int>(freeArrows.size)
        for (free in freeArrows) directlyUnlocks[free.id] = 0

        for (arrow in remainingArrows) {
            var cur = arrow.head.step(arrow.headDirection)
            val seen = HashSet<Int>()
            while (cur.x in 0 until width && cur.y in 0 until height) {
                val occ = occupied[cur]
                if (occ != null && occ != arrow.id && occ in directlyUnlocks && seen.add(occ)) {
                    directlyUnlocks[occ] = (directlyUnlocks[occ] ?: 0) + 1
                }
                cur = cur.step(arrow.headDirection)
            }
        }

        return freeArrows.maxWithOrNull(
            compareBy<Arrow> { directlyUnlocks[it.id] ?: 0 }.thenBy { it.length }
        )
    }
}
