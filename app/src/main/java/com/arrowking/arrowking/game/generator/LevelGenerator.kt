package com.arrowking.arrowking.game.generator

import com.arrowking.arrowking.game.model.Arrow
import com.arrowking.arrowking.game.model.Cell
import com.arrowking.arrowking.game.model.Direction
import com.arrowking.arrowking.game.model.GeneratedLevel
import com.arrowking.arrowking.game.model.LevelDifficulty
import com.arrowking.arrowking.game.model.LevelMask
import com.arrowking.arrowking.game.model.LevelMetrics
import com.arrowking.arrowking.game.shapes.ShapeLibrary
import com.arrowking.arrowking.game.solver.LevelSolver
import com.arrowking.arrowking.game.solver.SolverAnalysis
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

object LevelGenerator {

    const val GENERATOR_VERSION = 1

    fun hashSeed(level: Int, attempt: Int = 0): Long {
        var z = (level.toLong() * -7046029254386353131L) xor
            (GENERATOR_VERSION.toLong() * -7046029254386353131L) xor
            (attempt.toLong() * -4658895280553007687L)
        z = (z xor (z ushr 30)) * -4658895280553007687L
        z = (z xor (z ushr 27)) * -7723592293110705685L
        return z xor (z ushr 31)
    }

    fun difficultyForLevel(level: Int): LevelDifficulty {
        val l = max(1, level)
        val isBoss = l % 25 == 0
        val baseGrid = (18 + (l - 1) / 3).coerceAtMost(40)
        val gridMaxSide = if (isBoss) (baseGrid + 4).coerceAtMost(40) else baseGrid

        val minLen = (4 + (l - 1) / 40).coerceAtMost(9)
        val maxLenBase = 14 + l / 4
        val maxLen = if (l <= 200) {
            maxLenBase.coerceAtMost(60)
        } else {
            (60 + (l - 200) / 80).coerceAtMost(72)
        }

        val progress = ((l - 1).toFloat() / 250f).coerceIn(0f, 1f)
        val extraAfter200 = if (l > 200) ((l - 200).toFloat() / 2000f).coerceAtMost(0.03f) else 0f

        val turnChance = (0.50f + progress * 0.25f).coerceIn(0.50f, 0.75f)
        val minDepth = (18 + (progress * 42f).roundToInt() + if (l > 200) ((l - 200) / 150).coerceAtMost(10) else 0)
            .coerceAtMost(68)
        val minBlocked = (0.85f + progress * 0.12f + extraAfter200).coerceAtMost(0.985f)
        val maxStartFree = if (l >= 50) 2 else 3
        val maxAvgFree = (2.5f - progress * 1.0f).coerceIn(1.5f, 2.5f)
        val estSeconds = (135 + (progress * 110f).roundToInt() + if (isBoss) 45 else 0)

        return LevelDifficulty(
            level = l,
            gridMaxSide = gridMaxSide,
            minLen = minLen,
            maxLen = maxLen,
            turnChance = turnChance,
            minDependencyDepth = minDepth,
            minBlockedRatio = minBlocked,
            maxStartFree = maxStartFree,
            maxAvgFree = maxAvgFree,
            estimatedSolveSeconds = estSeconds
        )
    }

    fun generateLevel(levelNumber: Int): GeneratedLevel {
        val l = max(1, levelNumber)
        val diff = difficultyForLevel(l)
        val baseSeed = hashSeed(l, 0)
        val mask = ShapeLibrary.shapeForLevel(l, diff.gridMaxSide, baseSeed)

        var bestLevel: GeneratedLevel? = null
        var bestScore = Float.NEGATIVE_INFINITY

        val maxAttempts = 32
        for (attempt in 0 until maxAttempts) {
            val attemptSeed = hashSeed(l, attempt)
            val relaxFactor = when {
                attempt < 14 -> 1.0f
                attempt < 24 -> 0.90f
                else -> 0.80f
            }

            val candidate = tryConstructBoard(
                seed = attemptSeed,
                mask = mask,
                diff = diff,
                allowSingleCellFallback = attempt >= 8
            ) ?: continue

            val analysis = LevelSolver.solveAndAnalyze(candidate, mask)
            if (!analysis.isValidStructure || !analysis.isSolvable) continue
            if (analysis.singleCellRatio > 0.01f) continue

            val meetsDepth = analysis.dependencyDepth >= (diff.minDependencyDepth * relaxFactor).toInt()
            val meetsBlocked = analysis.blockedRatio >= diff.minBlockedRatio * relaxFactor
            val meetsStartFree = analysis.startFreeCount in 1..max(diff.maxStartFree, if (relaxFactor < 1f) 4 else diff.maxStartFree)
            val meetsAvgFree = analysis.avgFreeCount <= diff.maxAvgFree / relaxFactor

            val score = computeCandidateScore(analysis, diff, candidate.size)
            if (score > bestScore) {
                bestScore = score
                bestLevel = buildGeneratedLevel(l, attemptSeed, mask, candidate, analysis, diff)
            }

            if (meetsDepth && meetsBlocked && meetsStartFree && meetsAvgFree) {
                return buildGeneratedLevel(l, attemptSeed, mask, candidate, analysis, diff)
            }
        }

        if (bestLevel != null) return bestLevel

        // Second pass allowing single-cell fallback + relaxed constraints on intricate masks
        for (attempt in 0 until 28) {
            val seed = hashSeed(l, 400 + attempt)
            val candidate = tryConstructBoard(
                seed = seed,
                mask = mask,
                diff = diff,
                allowSingleCellFallback = true
            ) ?: continue
            val analysis = LevelSolver.solveAndAnalyze(candidate, mask)
            if (analysis.isValidStructure && analysis.isSolvable && analysis.singleCellRatio <= 0.01f) {
                val score = computeCandidateScore(analysis, diff, candidate.size)
                if (score > bestScore) {
                    bestScore = score
                    bestLevel = buildGeneratedLevel(l, seed, mask, candidate, analysis, diff)
                }
            }
        }
        if (bestLevel != null) return bestLevel

        val safeArrows = buildUltraSafeSnakeFallback(mask, baseSeed)
        val safeAnalysis = LevelSolver.solveAndAnalyze(safeArrows, mask)
        return buildGeneratedLevel(l, baseSeed, mask, safeArrows, safeAnalysis, diff)
    }

    private fun computeCandidateScore(
        analysis: SolverAnalysis,
        diff: LevelDifficulty,
        arrowCount: Int
    ): Float {
        var s = 0f
        s += min(analysis.dependencyDepth, diff.minDependencyDepth + 20) * 3.0f
        s += (analysis.blockedRatio * 100f)
        s -= (analysis.avgFreeCount - diff.maxAvgFree).coerceAtLeast(0f) * 45f
        s -= (analysis.startFreeCount - diff.maxStartFree).coerceAtLeast(0) * 35f
        s -= analysis.singleCellRatio * 500f
        s += min(arrowCount, 240) * 0.1f
        return s
    }

    private fun buildGeneratedLevel(
        levelNumber: Int,
        seed: Long,
        mask: LevelMask,
        arrows: List<Arrow>,
        analysis: SolverAnalysis,
        diff: LevelDifficulty
    ): GeneratedLevel {
        return GeneratedLevel(
            levelNumber = levelNumber,
            seed = seed,
            mask = mask,
            arrows = arrows,
            metrics = LevelMetrics(
                gridWidth = mask.width,
                gridHeight = mask.height,
                arrowCount = arrows.size,
                dependencyDepth = analysis.dependencyDepth,
                startFreeCount = analysis.startFreeCount,
                avgFreeCount = analysis.avgFreeCount,
                blockedRatio = analysis.blockedRatio,
                singleCellRatio = analysis.singleCellRatio,
                estimatedSolveSeconds = diff.estimatedSolveSeconds,
                shapeName = mask.shapeName
            ),
            isBoss = mask.isBoss
        )
    }

    private fun tryConstructBoard(
        seed: Long,
        mask: LevelMask,
        diff: LevelDifficulty,
        allowSingleCellFallback: Boolean
    ): List<Arrow>? {
        val rng = Random(seed)
        val w = mask.width
        val h = mask.height
        val total = w * h

        val grid = IntArray(total) { -2 }
        val maskIndices = ArrayList<Int>(mask.cellCount)
        val boundaryIndices = ArrayList<Int>(mask.width * 2)

        for (c in mask.cells) {
            val idx = c.y * w + c.x
            grid[idx] = -1
            maskIndices.add(idx)
        }

        for (c in mask.cells) {
            val idx = c.y * w + c.x
            if (c.x == 0 || c.x == w - 1 || c.y == 0 || c.y == h - 1 ||
                grid[idx - 1] == -2 || grid[idx + 1] == -2 ||
                grid[idx - w] == -2 || grid[idx + w] == -2
            ) {
                boundaryIndices.add(idx)
            }
        }

        var unassignedCount = mask.cellCount
        val placedArrows = ArrayList<Arrow>(mask.cellCount / 4)
        val arrowDepth = ArrayList<Int>(mask.cellCount / 4)
        val arrowDependents = ArrayList<Int>(mask.cellCount / 4)

        val targetStartFree = 1 + rng.nextInt(max(1, diff.maxStartFree))
        var startFreeSoFar = 0
        var singleCellCount = 0

        val inFrontier = BooleanArray(total)
        val frontier = ArrayList<Int>(mask.cellCount)

        // Sweep direction keeps the placed region compact on one side so rays pointing back across
        // concave wings/lobes never hit unassigned (-1) cells on the far side.
        val sweepDirs = arrayOf(
            1 to 0, -1 to 0, 0 to 1, 0 to -1,
            1 to 1, -1 to 1, 1 to -1, -1 to -1
        )
        val sweepVec = sweepDirs[rng.nextInt(sweepDirs.size)]
        val sxVec = sweepVec.first
        val syVec = sweepVec.second

        if (boundaryIndices.isNotEmpty()) {
            // Start near the leading edge of the sweep direction
            val seedCell = boundaryIndices.maxByOrNull { idx ->
                (idx % w) * sxVec + (idx / w) * syVec + rng.nextInt(3)
            } ?: boundaryIndices[rng.nextInt(boundaryIndices.size)]
            inFrontier[seedCell] = true
            frontier.add(seedCell)
        }

        fun addNeighborsToFrontier(idx: Int) {
            val x = idx % w
            val y = idx / w
            if (x > 0 && grid[idx - 1] == -1 && !inFrontier[idx - 1]) {
                inFrontier[idx - 1] = true
                frontier.add(idx - 1)
            }
            if (x < w - 1 && grid[idx + 1] == -1 && !inFrontier[idx + 1]) {
                inFrontier[idx + 1] = true
                frontier.add(idx + 1)
            }
            if (y > 0 && grid[idx - w] == -1 && !inFrontier[idx - w]) {
                inFrontier[idx - w] = true
                frontier.add(idx - w)
            }
            if (y < h - 1 && grid[idx + w] == -1 && !inFrontier[idx + w]) {
                inFrontier[idx + w] = true
                frontier.add(idx + w)
            }
        }

        fun unassignedDegree(idx: Int, excludeWalk: Set<Int>? = null): Int {
            val x = idx % w
            val y = idx / w
            var deg = 0
            if (x > 0 && grid[idx - 1] == -1 && (excludeWalk == null || (idx - 1) !in excludeWalk)) deg++
            if (x < w - 1 && grid[idx + 1] == -1 && (excludeWalk == null || (idx + 1) !in excludeWalk)) deg++
            if (y > 0 && grid[idx - w] == -1 && (excludeWalk == null || (idx - w) !in excludeWalk)) deg++
            if (y < h - 1 && grid[idx + w] == -1 && (excludeWalk == null || (idx + w) !in excludeWalk)) deg++
            return deg
        }

        data class RayEval(
            val valid: Boolean,
            val hitsPlacedArrow: Boolean,
            val maxDepth: Int,
            val blockerCount: Int,
            val minDepFanout: Int
        )

        val walkSet = HashSet<Int>(96)
        val distinctBlockers = HashSet<Int>(16)

        fun evalRay(headIdx: Int, dir: Direction): RayEval {
            var cx = (headIdx % w) + dir.dx
            var cy = (headIdx / w) + dir.dy
            var hitsArrow = false
            var maxD = 0
            var minFan = Int.MAX_VALUE
            distinctBlockers.clear()

            while (cx in 0 until w && cy in 0 until h) {
                val nIdx = cy * w + cx
                val g = grid[nIdx]
                if (g == -1 || nIdx in walkSet) {
                    return RayEval(false, false, 0, 0, 0)
                }
                if (g >= 0) {
                    hitsArrow = true
                    distinctBlockers.add(g)
                    val d = arrowDepth[g]
                    if (d > maxD) maxD = d
                    val f = arrowDependents[g]
                    if (f < minFan) minFan = f
                }
                cx += dir.dx
                cy += dir.dy
            }
            return RayEval(true, hitsArrow, maxD, distinctBlockers.size, if (hitsArrow) minFan else 0)
        }

        fun countStrandedNeighbors(curIdx: Int, nextIdx: Int, walkSetRef: Set<Int>): Int {
            val cx = curIdx % w
            val cy = curIdx / w
            var stranded = 0
            val neighbors = intArrayOf(
                if (cx > 0) curIdx - 1 else -1,
                if (cx < w - 1) curIdx + 1 else -1,
                if (cy > 0) curIdx - w else -1,
                if (cy < h - 1) curIdx + w else -1
            )
            for (nb in neighbors) {
                if (nb < 0 || nb == nextIdx || grid[nb] != -1 || nb in walkSetRef) continue
                val nx = nb % w
                val ny = nb / w
                var exits = 0
                if (nx > 0 && grid[nb - 1] == -1 && (nb - 1) != nextIdx && (nb - 1) !in walkSetRef) exits++
                if (nx < w - 1 && grid[nb + 1] == -1 && (nb + 1) != nextIdx && (nb + 1) !in walkSetRef) exits++
                if (ny > 0 && grid[nb - w] == -1 && (nb - w) != nextIdx && (nb - w) !in walkSetRef) exits++
                if (ny < h - 1 && grid[nb + w] == -1 && (nb + w) != nextIdx && (nb + w) !in walkSetRef) exits++
                if (exits == 0) stranded++
            }
            return stranded
        }

        val walk = IntArray(diff.maxLen + 12)
        val dirs = Direction.entries

        var safetyCounter = 0
        while (unassignedCount > 0 && safetyCounter < mask.cellCount * 3) {
            safetyCounter++

            var startIdx = -1
            var bestStartScore = Int.MIN_VALUE
            var i = frontier.size - 1
            var scanned = 0
            while (i >= 0) {
                val cand = frontier[i]
                if (grid[cand] != -1) {
                    frontier[i] = frontier[frontier.lastIndex]
                    frontier.removeAt(frontier.lastIndex)
                    i--
                    continue
                }
                scanned++
                val deg = unassignedDegree(cand)
                val x = cand % w
                val y = cand / w
                var adjMaxDepth = 0
                if (x > 0 && grid[cand - 1] >= 0) adjMaxDepth = max(adjMaxDepth, arrowDepth[grid[cand - 1]])
                if (x < w - 1 && grid[cand + 1] >= 0) adjMaxDepth = max(adjMaxDepth, arrowDepth[grid[cand + 1]])
                if (y > 0 && grid[cand - w] >= 0) adjMaxDepth = max(adjMaxDepth, arrowDepth[grid[cand - w]])
                if (y < h - 1 && grid[cand + w] >= 0) adjMaxDepth = max(adjMaxDepth, arrowDepth[grid[cand + w]])

                val degBonus = when (deg) {
                    0 -> 240
                    1 -> 130
                    2 -> 40
                    else -> 0
                }
                val sweepBonus = (x * sxVec + y * syVec) * 3
                val score = degBonus + adjMaxDepth * 4 + sweepBonus + rng.nextInt(7)
                if (score > bestStartScore) {
                    bestStartScore = score
                    startIdx = cand
                }
                if (scanned >= 180 && bestStartScore >= 240) break
                i--
            }

            if (startIdx == -1) {
                for (idx in maskIndices) {
                    if (grid[idx] == -1) {
                        startIdx = idx
                        break
                    }
                }
                if (startIdx == -1) break
            }

            val isSpineArrow = placedArrows.isEmpty() && mask.cellCount >= 180
            val makeLongKey = isSpineArrow || (placedArrows.size > 4 && rng.nextInt(8) == 0)
            val targetLen = if (isSpineArrow) {
                min(diff.maxLen, max(10, mask.cellCount / 14))
            } else if (makeLongKey) {
                (diff.maxLen * 0.65f).toInt().coerceIn(diff.minLen + 2, diff.maxLen)
            } else {
                val effMax = max(diff.minLen + 2, min(14, max(7, mask.cellCount / 28)))
                diff.minLen + rng.nextInt(max(1, effMax - diff.minLen + 1))
            }

            walkSet.clear()
            walk[0] = startIdx
            walkSet.add(startIdx)
            var walkLen = 1
            var prevDir: Direction? = null

            var bestStopLen = -1
            var bestStopReversed = false
            var bestStopScore = Float.NEGATIVE_INFINITY
            var fallbackStopLen = -1
            var fallbackStopReversed = false
            var fallbackStopScore = Float.NEGATIVE_INFINITY

            fun scoreRayEndpoint(len: Int, ray: RayEval): Float {
                val lenBonus = if (len >= diff.minLen) 48f else -24f
                val depthBonus = if (ray.hitsPlacedArrow) {
                    val fanoutPenalty = if (ray.minDepFanout >= 1) -68f * ray.minDepFanout else 35f
                    ray.maxDepth * 36f + ray.blockerCount * 5f + fanoutPenalty
                } else {
                    if (startFreeSoFar < targetStartFree) 12f else -140f
                }
                return lenBonus + depthBonus + len * 0.8f
            }

            fun considerWalkEndpoint(len: Int) {
                if (len < 2) return
                val allowAirOnly = startFreeSoFar < targetStartFree

                val h1 = walk[len - 1]
                val p1 = walk[len - 2]
                val dir1 = Direction.between(Cell(p1 % w, p1 / w), Cell(h1 % w, h1 / w))!!
                val r1 = evalRay(h1, dir1)
                if (r1.valid) {
                    val s1 = scoreRayEndpoint(len, r1)
                    if (r1.hitsPlacedArrow || allowAirOnly) {
                        if (s1 > bestStopScore) {
                            bestStopScore = s1
                            bestStopLen = len
                            bestStopReversed = false
                        }
                    } else if (s1 > fallbackStopScore) {
                        fallbackStopScore = s1
                        fallbackStopLen = len
                        fallbackStopReversed = false
                    }
                }

                val h2 = walk[0]
                val p2 = walk[1]
                val dir2 = Direction.between(Cell(p2 % w, p2 / w), Cell(h2 % w, h2 / w))!!
                val r2 = evalRay(h2, dir2)
                if (r2.valid) {
                    val s2 = scoreRayEndpoint(len, r2)
                    if (r2.hitsPlacedArrow || allowAirOnly) {
                        if (s2 > bestStopScore) {
                            bestStopScore = s2
                            bestStopLen = len
                            bestStopReversed = true
                        }
                    } else if (s2 > fallbackStopScore) {
                        fallbackStopScore = s2
                        fallbackStopLen = len
                        fallbackStopReversed = true
                    }
                }
            }

            val hardMaxLen = min(diff.maxLen, walk.size)
            while (walkLen < targetLen || (bestStopLen == -1 && walkLen < hardMaxLen)) {
                val cur = walk[walkLen - 1]
                val cx = cur % w
                val cy = cur / w

                var chosenNext = -1
                var chosenDir: Direction? = null
                var bestCandScore = Float.NEGATIVE_INFINITY
                val wantTurn = prevDir != null && rng.nextFloat() < diff.turnChance

                for (d in dirs) {
                    val nx = cx + d.dx
                    val ny = cy + d.dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val nIdx = ny * w + nx
                    if (grid[nIdx] != -1 || nIdx in walkSet) continue

                    val deg = unassignedDegree(nIdx, walkSet)
                    val stranded = countStrandedNeighbors(cur, nIdx, walkSet)

                    var candScore = rng.nextFloat() * 4f + (4 - deg) * 9f - stranded * 90f
                    if (prevDir != null) {
                        if (wantTurn && d != prevDir) candScore += 11f
                        if (!wantTurn && d == prevDir) candScore += 11f
                    }
                    if (walkLen >= diff.minLen - 1) {
                        val nextRay = evalRay(nIdx, d)
                        if (nextRay.valid) {
                            if (nextRay.hitsPlacedArrow) {
                                candScore += 55f + nextRay.maxDepth * 7f
                            } else if (startFreeSoFar < targetStartFree) {
                                candScore += 16f
                            }
                        }
                    }

                    if (candScore > bestCandScore) {
                        bestCandScore = candScore
                        chosenNext = nIdx
                        chosenDir = d
                    }
                }

                if (chosenNext == -1) break
                walk[walkLen] = chosenNext
                walkSet.add(chosenNext)
                walkLen++
                prevDir = chosenDir

                considerWalkEndpoint(walkLen)
                if (walkLen >= targetLen && bestStopLen >= 2) break
            }

            val useLen = if (bestStopLen >= 2) bestStopLen else fallbackStopLen
            val useRev = if (bestStopLen >= 2) bestStopReversed else fallbackStopReversed

            if (useLen >= 2) {
                val cellsList = ArrayList<Cell>(useLen)
                if (!useRev) {
                    for (k in 0 until useLen) {
                        val idx = walk[k]
                        cellsList.add(Cell(idx % w, idx / w))
                    }
                } else {
                    for (k in useLen - 1 downTo 0) {
                        val idx = walk[k]
                        cellsList.add(Cell(idx % w, idx / w))
                    }
                }

                val headDir = Direction.between(cellsList[cellsList.size - 2], cellsList.last())!!
                val headIdx = cellsList.last().y * w + cellsList.last().x
                walkSet.clear()
                for (c in cellsList) walkSet.add(c.y * w + c.x)
                val rayInfo = evalRay(headIdx, headDir)

                val arrowId = placedArrows.size
                val colorIdx = pickArrowColorIndex(cellsList, grid, placedArrows, w, h, rng)
                placedArrows.add(Arrow(arrowId, cellsList, headDir, colorIdx))
                val newDepth = if (rayInfo.hitsPlacedArrow) rayInfo.maxDepth + 1 else 1
                arrowDepth.add(newDepth)
                arrowDependents.add(0)

                var rx = (headIdx % w) + headDir.dx
                var ry = (headIdx / w) + headDir.dy
                val seenB = HashSet<Int>(8)
                while (rx in 0 until w && ry in 0 until h) {
                    val g = grid[ry * w + rx]
                    if (g >= 0 && seenB.add(g)) {
                        arrowDependents[g] = arrowDependents[g] + 1
                    }
                    rx += headDir.dx
                    ry += headDir.dy
                }
                if (!rayInfo.hitsPlacedArrow) startFreeSoFar++

                for (c in cellsList) {
                    val idx = c.y * w + c.x
                    grid[idx] = arrowId
                    unassignedCount--
                }
                for (c in cellsList) {
                    addNeighborsToFrontier(c.y * w + c.x)
                }
            } else {
                val sx = startIdx % w
                val sy = startIdx / w
                var absorbed = false

                // 1. Tail extension
                for (d in dirs) {
                    val nx = sx + d.dx
                    val ny = sy + d.dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val adjId = grid[ny * w + nx]
                    if (adjId >= 0) {
                        val adjArrow = placedArrows[adjId]
                        if (adjArrow.tail.x == nx && adjArrow.tail.y == ny &&
                            adjArrow.length < diff.maxLen + 12 &&
                            (adjArrow.length >= 2 || Direction.between(Cell(sx, sy), adjArrow.head) == adjArrow.headDirection)
                        ) {
                            val newCell = Cell(sx, sy)
                            placedArrows[adjId] = adjArrow.copy(cells = listOf(newCell) + adjArrow.cells)
                            grid[startIdx] = adjId
                            unassignedCount--
                            addNeighborsToFrontier(startIdx)
                            absorbed = true
                            break
                        }
                    }
                }

                // 2. Head extension
                if (!absorbed) {
                    for (d in dirs) {
                        val nx = sx + d.dx
                        val ny = sy + d.dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val adjId = grid[ny * w + nx]
                        if (adjId >= 0) {
                            val adjArrow = placedArrows[adjId]
                            if (adjArrow.head.x == nx && adjArrow.head.y == ny && adjArrow.length < diff.maxLen + 12) {
                                val newDir = d.opposite()
                                var cx = sx + newDir.dx
                                var cy = sy + newDir.dy
                                var ok = true
                                var hitsEarlier = false
                                while (cx in 0 until w && cy in 0 until h) {
                                    val g = grid[cy * w + cx]
                                    if (g == -1 || g >= adjId) {
                                        ok = false
                                        break
                                    }
                                    if (g >= 0) hitsEarlier = true
                                    cx += newDir.dx
                                    cy += newDir.dy
                                }
                                if (ok && (hitsEarlier || startFreeSoFar < targetStartFree)) {
                                    placedArrows[adjId] = adjArrow.copy(
                                        cells = adjArrow.cells + Cell(sx, sy),
                                        headDirection = newDir
                                    )
                                    grid[startIdx] = adjId
                                    unassignedCount--
                                    addNeighborsToFrontier(startIdx)
                                    absorbed = true
                                    break
                                }
                            }
                        }
                    }
                }

                // 3. 2-cell emergency walk
                if (!absorbed) {
                    var bestTwo: Triple<List<Cell>, Direction, RayEval>? = null
                    for (d in dirs) {
                        val nx = sx + d.dx
                        val ny = sy + d.dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val nIdx = ny * w + nx
                        if (grid[nIdx] != -1) continue
                        walkSet.clear()
                        walkSet.add(startIdx)
                        walkSet.add(nIdx)
                        val ev1 = evalRay(nIdx, d)
                        if (ev1.valid) {
                            val cand = Triple(listOf(Cell(sx, sy), Cell(nx, ny)), d, ev1)
                            if (bestTwo == null || (!bestTwo.third.hitsPlacedArrow && ev1.hitsPlacedArrow)) {
                                bestTwo = cand
                            }
                        }
                        val ev2 = evalRay(startIdx, d.opposite())
                        if (ev2.valid) {
                            val cand = Triple(listOf(Cell(nx, ny), Cell(sx, sy)), d.opposite(), ev2)
                            if (bestTwo == null || (!bestTwo.third.hitsPlacedArrow && ev2.hitsPlacedArrow)) {
                                bestTwo = cand
                            }
                        }
                    }
                    if (bestTwo != null) {
                        val (cells, dir, ev) = bestTwo
                        val arrowId = placedArrows.size
                        val colorIdx = pickArrowColorIndex(cells, grid, placedArrows, w, h, rng)
                        placedArrows.add(Arrow(arrowId, cells, dir, colorIdx))
                        arrowDepth.add(if (ev.hitsPlacedArrow) ev.maxDepth + 1 else 1)
                        arrowDependents.add(0)
                        if (!ev.hitsPlacedArrow) startFreeSoFar++
                        for (c in cells) {
                            val idx = c.y * w + c.x
                            grid[idx] = arrowId
                            unassignedCount--
                        }
                        for (c in cells) addNeighborsToFrontier(c.y * w + c.x)
                        absorbed = true
                    }
                }

                // 4. Split an adjacent >= 4-cell arrow at cell k
                if (!absorbed) {
                    outer@ for (d in dirs) {
                        val nx = sx + d.dx
                        val ny = sy + d.dy
                        if (nx !in 0 until w || ny !in 0 until h) continue
                        val adjId = grid[ny * w + nx]
                        if (adjId < 0) continue
                        val adj = placedArrows[adjId]
                        if (adj.length < 4) continue
                        val k = adj.cells.indexOfFirst { it.x == nx && it.y == ny }
                        if (k !in 1..(adj.length - 3)) continue

                        val newDir = d.opposite()
                        val a1Cells = adj.cells.subList(0, k + 1) + Cell(sx, sy)
                        val a1Set = a1Cells.toHashSet()
                        var cx = sx + newDir.dx
                        var cy = sy + newDir.dy
                        var ok = true
                        while (cx in 0 until w && cy in 0 until h) {
                            val g = grid[cy * w + cx]
                            if (g == -1 || g >= adjId || Cell(cx, cy) in a1Set) {
                                ok = false
                                break
                            }
                            cx += newDir.dx
                            cy += newDir.dy
                        }
                        if (ok) {
                            val a2Cells = adj.cells.subList(k + 1, adj.length).toList()
                            placedArrows[adjId] = adj.copy(cells = a2Cells)
                            val newId = placedArrows.size
                            val colorIdx = pickArrowColorIndex(a1Cells, grid, placedArrows, w, h, rng)
                            placedArrows.add(Arrow(newId, a1Cells, newDir, colorIdx))
                            arrowDepth.add(arrowDepth[adjId])
                            arrowDependents.add(0)
                            for (c in a1Cells) grid[c.y * w + c.x] = newId
                            unassignedCount--
                            addNeighborsToFrontier(startIdx)
                            absorbed = true
                            break@outer
                        }
                    }
                }

                // 5. Temporary 1-cell placement (post-pass absorbs via tail/head/split/pair)
                if (!absorbed) {
                    if (!allowSingleCellFallback) return null
                    walkSet.clear()
                    walkSet.add(startIdx)
                    var chosenDir: Direction? = null
                    var bestEval: RayEval? = null
                    for (d in dirs) {
                        val ev = evalRay(startIdx, d)
                        if (ev.valid && (bestEval == null ||
                                (!bestEval.hitsPlacedArrow && ev.hitsPlacedArrow) ||
                                (bestEval.hitsPlacedArrow == ev.hitsPlacedArrow && ev.maxDepth > bestEval.maxDepth))
                        ) {
                            bestEval = ev
                            chosenDir = d
                        }
                    }
                    val maxTempSingles = max(4, mask.cellCount / 30)
                    if (chosenDir != null && singleCellCount < maxTempSingles) {
                        val arrowId = placedArrows.size
                        val cell = Cell(sx, sy)
                        val colorIdx = pickArrowColorIndex(listOf(cell), grid, placedArrows, w, h, rng)
                        placedArrows.add(Arrow(arrowId, listOf(cell), chosenDir, colorIdx))
                        arrowDepth.add(if (bestEval!!.hitsPlacedArrow) bestEval.maxDepth + 1 else 1)
                        arrowDependents.add(0)
                        if (!bestEval.hitsPlacedArrow) startFreeSoFar++
                        grid[startIdx] = arrowId
                        unassignedCount--
                        singleCellCount++
                        addNeighborsToFrontier(startIdx)
                    } else {
                        return null
                    }
                }
            }
        }

        if (unassignedCount > 0) return null
        return mergeSingleCellArrowsIntoTails(placedArrows, grid, w, h)
    }

    private fun mergeSingleCellArrowsIntoTails(
        arrows: MutableList<Arrow>,
        grid: IntArray,
        w: Int,
        h: Int
    ): List<Arrow> {
        val removed = HashSet<Int>()
        val dirs = Direction.entries

        var pass = 0
        var changed = true
        while (changed && pass < 6) {
            changed = false
            pass++
            val origSize = arrows.size
            for (i in 0 until origSize) {
                val a = arrows[i]
                if (a.length != 1 || i in removed) continue
                val c = a.head
                var merged = false

                // 1. Prepend to adjacent earlier arrow's tail (always valid for adjId < i)
                for (d in dirs) {
                    val nx = c.x + d.dx
                    val ny = c.y + d.dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val adjId = grid[ny * w + nx]
                    if (adjId >= 0 && adjId < i && adjId !in removed) {
                        val adj = arrows[adjId]
                        if (adj.tail.x == nx && adj.tail.y == ny &&
                            (adj.length >= 2 || Direction.between(c, adj.head) == adj.headDirection)
                        ) {
                            val onRay = LevelSolver.rayCells(adj, w, h).any { it == c }
                            if (!onRay) {
                                arrows[adjId] = adj.copy(cells = listOf(c) + adj.cells)
                                grid[c.y * w + c.x] = adjId
                                removed.add(i)
                                merged = true
                                changed = true
                                break
                            }
                        }
                    }
                }
                if (merged) continue

                // 2. Append to adjacent earlier arrow's head if new ray only hits < adjId
                for (d in dirs) {
                    val nx = c.x + d.dx
                    val ny = c.y + d.dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val adjId = grid[ny * w + nx]
                    if (adjId >= 0 && adjId < i && adjId !in removed) {
                        val adj = arrows[adjId]
                        if (adj.head.x == nx && adj.head.y == ny) {
                            val newDir = d.opposite()
                            var cx = c.x + newDir.dx
                            var cy = c.y + newDir.dy
                            var ok = true
                            while (cx in 0 until w && cy in 0 until h) {
                                val g = grid[cy * w + cx]
                                if (g == -1 || g >= adjId) {
                                    ok = false
                                    break
                                }
                                cx += newDir.dx
                                cy += newDir.dy
                            }
                            if (ok) {
                                arrows[adjId] = adj.copy(cells = adj.cells + c, headDirection = newDir)
                                grid[c.y * w + c.x] = adjId
                                removed.add(i)
                                merged = true
                                changed = true
                                break
                            }
                        }
                    }
                }
                if (merged) continue

                // 3. Pair-merge with an adjacent 1-cell arrow
                for (d in dirs) {
                    val nx = c.x + d.dx
                    val ny = c.y + d.dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val adjId = grid[ny * w + nx]
                    if (adjId >= 0 && adjId != i && adjId !in removed) {
                        val adj = arrows[adjId]
                        if (adj.length == 1) {
                            val minId = min(i, adjId)
                            val maxId = max(i, adjId)
                            // Try c -> adj.head
                            val dir1 = d
                            var ok1 = true
                            var cx = nx + dir1.dx
                            var cy = ny + dir1.dy
                            while (cx in 0 until w && cy in 0 until h) {
                                val g = grid[cy * w + cx]
                                if (g == -1 || g >= minId) {
                                    ok1 = false
                                    break
                                }
                                cx += dir1.dx
                                cy += dir1.dy
                            }
                            if (ok1) {
                                arrows[minId] = Arrow(minId, listOf(c, adj.head), dir1, adj.colorIndex)
                                grid[c.y * w + c.x] = minId
                                grid[ny * w + nx] = minId
                                removed.add(maxId)
                                merged = true
                                changed = true
                                break
                            }
                            // Try adj.head -> c
                            val dir2 = d.opposite()
                            var ok2 = true
                            cx = c.x + dir2.dx
                            cy = c.y + dir2.dy
                            while (cx in 0 until w && cy in 0 until h) {
                                val g = grid[cy * w + cx]
                                if (g == -1 || g >= minId) {
                                    ok2 = false
                                    break
                                }
                                cx += dir2.dx
                                cy += dir2.dy
                            }
                            if (ok2) {
                                arrows[minId] = Arrow(minId, listOf(adj.head, c), dir2, adj.colorIndex)
                                grid[c.y * w + c.x] = minId
                                grid[ny * w + nx] = minId
                                removed.add(maxId)
                                merged = true
                                changed = true
                                break
                            }
                        }
                    }
                }
                if (merged) continue

                // 4. Split an adjacent >= 4-cell arrow at k so [0..k, c] becomes a valid arrow
                for (d in dirs) {
                    val nx = c.x + d.dx
                    val ny = c.y + d.dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val adjId = grid[ny * w + nx]
                    if (adjId >= 0 && adjId < i && adjId !in removed) {
                        val adj = arrows[adjId]
                        if (adj.length < 4) continue
                        val k = adj.cells.indexOfFirst { it.x == nx && it.y == ny }
                        if (k !in 1..(adj.length - 3)) continue

                        val newDir = d.opposite()
                        val a1Cells = adj.cells.subList(0, k + 1) + c
                        val a1Set = a1Cells.toHashSet()
                        var cx = c.x + newDir.dx
                        var cy = c.y + newDir.dy
                        var ok = true
                        while (cx in 0 until w && cy in 0 until h) {
                            val g = grid[cy * w + cx]
                            if (g == -1 || g >= adjId || Cell(cx, cy) in a1Set) {
                                ok = false
                                break
                            }
                            cx += newDir.dx
                            cy += newDir.dy
                        }
                        if (ok) {
                            val a2Cells = adj.cells.subList(k + 1, adj.length).toList()
                            arrows[adjId] = adj.copy(cells = a2Cells)
                            val newId = arrows.size
                            arrows.add(Arrow(newId, a1Cells, newDir, adj.colorIndex))
                            for (ac in a1Cells) grid[ac.y * w + ac.x] = newId
                            removed.add(i)
                            changed = true
                            break
                        }
                    }
                }
            }
        }

        // Rule 5: Generalized solvability-verified merge/split/borrow for any stubborn 1-cell arrow
        fun isCandidateSolvable(candList: List<Arrow>): Boolean {
            val cellGrid = IntArray(w * h) { -1 }
            for (idx in candList.indices) {
                for (cell in candList[idx].cells) {
                    cellGrid[cell.y * w + cell.x] = idx
                }
            }
            val alive = BooleanArray(candList.size) { true }
            var aliveCount = candList.size
            var progress = true
            while (progress && aliveCount > 0) {
                progress = false
                for (idx in candList.indices) {
                    if (!alive[idx]) continue
                    val arr = candList[idx]
                    var cx = arr.head.x + arr.headDirection.dx
                    var cy = arr.head.y + arr.headDirection.dy
                    var free = true
                    while (cx in 0 until w && cy in 0 until h) {
                        val g = cellGrid[cy * w + cx]
                        if (g >= 0 && alive[g]) {
                            free = false
                            break
                        }
                        cx += arr.headDirection.dx
                        cy += arr.headDirection.dy
                    }
                    if (free) {
                        alive[idx] = false
                        aliveCount--
                        progress = true
                    }
                }
            }
            return aliveCount == 0
        }

        fun buildSnapshot(
            replaceId: Int,
            replacement: Arrow,
            removeId: Int,
            extraArrow: Arrow? = null
        ): List<Arrow> {
            val out = ArrayList<Arrow>(arrows.size)
            for (k in arrows.indices) {
                if (k == removeId || k in removed) continue
                if (k == replaceId) {
                    out.add(replacement)
                } else {
                    out.add(arrows[k])
                }
            }
            if (extraArrow != null) out.add(extraArrow)
            return out
        }

        var rule5Changed = true
        var rule5Pass = 0
        while (rule5Changed && rule5Pass < 4) {
            rule5Changed = false
            rule5Pass++
            val snapSize = arrows.size
            for (i in 0 until snapSize) {
                val a = arrows[i]
                if (a.length != 1 || i in removed) continue
                val c = a.head
                var resolved = false

                // 5a: Tail prepend to ANY neighbor (even adjId > i)
                for (d in dirs) {
                    val nx = c.x + d.dx
                    val ny = c.y + d.dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val adjId = grid[ny * w + nx]
                    if (adjId >= 0 && adjId != i && adjId !in removed) {
                        val adj = arrows[adjId]
                        if (adj.tail.x == nx && adj.tail.y == ny &&
                            (adj.length >= 2 || Direction.between(c, adj.head) == adj.headDirection)
                        ) {
                            val newAdj = adj.copy(cells = listOf(c) + adj.cells)
                            if (LevelSolver.rayCells(newAdj, w, h).none { it in newAdj.cells } &&
                                isCandidateSolvable(buildSnapshot(adjId, newAdj, i))
                            ) {
                                arrows[adjId] = newAdj
                                grid[c.y * w + c.x] = adjId
                                removed.add(i)
                                resolved = true
                                rule5Changed = true
                                break
                            }
                        }
                    }
                }
                if (resolved) continue

                // 5b: Head append to ANY neighbor
                for (d in dirs) {
                    val nx = c.x + d.dx
                    val ny = c.y + d.dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val adjId = grid[ny * w + nx]
                    if (adjId >= 0 && adjId != i && adjId !in removed) {
                        val adj = arrows[adjId]
                        if (adj.head.x == nx && adj.head.y == ny) {
                            val newDir = d.opposite()
                            val newAdj = adj.copy(cells = adj.cells + c, headDirection = newDir)
                            if (LevelSolver.rayCells(newAdj, w, h).none { it in newAdj.cells } &&
                                isCandidateSolvable(buildSnapshot(adjId, newAdj, i))
                            ) {
                                arrows[adjId] = newAdj
                                grid[c.y * w + c.x] = adjId
                                removed.add(i)
                                resolved = true
                                rule5Changed = true
                                break
                            }
                        }
                    }
                }
                if (resolved) continue

                // 5c: Split ANY >= 4-cell neighbor at k
                outerSplit@ for (d in dirs) {
                    val nx = c.x + d.dx
                    val ny = c.y + d.dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val adjId = grid[ny * w + nx]
                    if (adjId >= 0 && adjId != i && adjId !in removed) {
                        val adj = arrows[adjId]
                        if (adj.length < 4) continue
                        val k = adj.cells.indexOfFirst { it.x == nx && it.y == ny }
                        if (k !in 1..(adj.length - 3)) continue

                        val newDir = d.opposite()
                        val a1Cells = adj.cells.subList(0, k + 1) + c
                        val newId = arrows.size
                        val a1 = Arrow(newId, a1Cells, newDir, adj.colorIndex)
                        if (LevelSolver.rayCells(a1, w, h).any { it in a1Cells }) continue
                        val a2 = adj.copy(cells = adj.cells.subList(k + 1, adj.length).toList())
                        if (isCandidateSolvable(buildSnapshot(adjId, a2, i, a1))) {
                            arrows[adjId] = a2
                            arrows.add(a1)
                            for (ac in a1Cells) grid[ac.y * w + ac.x] = newId
                            removed.add(i)
                            resolved = true
                            rule5Changed = true
                            break@outerSplit
                        }
                    }
                }
                if (resolved) continue

                // 5d: Borrow head of >= 3-cell neighbor to form 2-cell arrow [adj.head, c] or [c, adj.head]
                for (d in dirs) {
                    val nx = c.x + d.dx
                    val ny = c.y + d.dy
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val adjId = grid[ny * w + nx]
                    if (adjId >= 0 && adjId != i && adjId !in removed) {
                        val adj = arrows[adjId]
                        if (adj.length < 3 || adj.head.x != nx || adj.head.y != ny) continue
                        val shortCells = adj.cells.subList(0, adj.length - 1).toList()
                        val shortDir = Direction.between(shortCells[shortCells.size - 2], shortCells.last())!!
                        val shortAdj = adj.copy(cells = shortCells, headDirection = shortDir)
                        if (LevelSolver.rayCells(shortAdj, w, h).any { it in shortCells }) continue

                        val newId = arrows.size
                        val pair1 = Arrow(newId, listOf(adj.head, c), d.opposite(), a.colorIndex)
                        if (LevelSolver.rayCells(pair1, w, h).none { it in pair1.cells } &&
                            isCandidateSolvable(buildSnapshot(adjId, shortAdj, i, pair1))
                        ) {
                            arrows[adjId] = shortAdj
                            arrows.add(pair1)
                            grid[adj.head.y * w + adj.head.x] = newId
                            grid[c.y * w + c.x] = newId
                            removed.add(i)
                            resolved = true
                            rule5Changed = true
                            break
                        }
                        val pair2 = Arrow(newId, listOf(c, adj.head), d, a.colorIndex)
                        if (LevelSolver.rayCells(pair2, w, h).none { it in pair2.cells } &&
                            isCandidateSolvable(buildSnapshot(adjId, shortAdj, i, pair2))
                        ) {
                            arrows[adjId] = shortAdj
                            arrows.add(pair2)
                            grid[adj.head.y * w + adj.head.x] = newId
                            grid[c.y * w + c.x] = newId
                            removed.add(i)
                            resolved = true
                            rule5Changed = true
                            break
                        }
                    }
                }
            }
        }

        val finalArrows = ArrayList<Arrow>(arrows.size)
        for (i in arrows.indices) {
            if (i !in removed) {
                finalArrows.add(arrows[i].copy(id = finalArrows.size))
            }
        }
        return finalArrows
    }

    private fun pickArrowColorIndex(
        cells: List<Cell>,
        grid: IntArray,
        placedArrows: List<Arrow>,
        w: Int,
        h: Int,
        rng: Random
    ): Int {
        val neighborCounts = IntArray(4)
        for (c in cells) {
            for (d in Direction.entries) {
                val nx = c.x + d.dx
                val ny = c.y + d.dy
                if (nx in 0 until w && ny in 0 until h) {
                    val adjId = grid[ny * w + nx]
                    if (adjId >= 0 && adjId < placedArrows.size) {
                        neighborCounts[placedArrows[adjId].colorIndex % 4]++
                    }
                }
            }
        }
        val minCount = neighborCounts.min()
        val choices = (0 until 4).filter { neighborCounts[it] == minCount }
        return choices[rng.nextInt(choices.size)]
    }

    private fun buildUltraSafeSnakeFallback(mask: LevelMask, seed: Long): List<Arrow> {
        val rng = Random(seed)
        val w = mask.width
        val h = mask.height
        val grid = IntArray(w * h) { -2 }
        for (c in mask.cells) grid[c.y * w + c.x] = -1

        val arrows = ArrayList<Arrow>()
        var remaining = mask.cellCount
        val dirs = Direction.entries

        while (remaining > 0) {
            var bestC: Cell? = null
            var bestD: Direction? = null
            var bestScore = Int.MIN_VALUE

            for (c in mask.cells) {
                val idx = c.y * w + c.x
                if (grid[idx] != -1) continue

                var deg = 0
                for (nd in dirs) {
                    val nx = c.x + nd.dx
                    val ny = c.y + nd.dy
                    if (nx in 0 until w && ny in 0 until h && grid[ny * w + nx] == -1) deg++
                }

                for (d in dirs) {
                    var cx = c.x + d.dx
                    var cy = c.y + d.dy
                    var ok = true
                    var hits = false
                    while (cx in 0 until w && cy in 0 until h) {
                        val g = grid[cy * w + cx]
                        if (g == -1) {
                            ok = false
                            break
                        }
                        if (g >= 0) hits = true
                        cx += d.dx
                        cy += d.dy
                    }
                    if (!ok) continue

                    val bx = c.x - d.dx
                    val by = c.y - d.dy
                    val canGrowTwo = bx in 0 until w && by in 0 until h && grid[by * w + bx] == -1
                    val score = (if (canGrowTwo) 1000 else 0) +
                        (if (hits) 200 else 0) +
                        (4 - deg) * 25
                    if (score > bestScore) {
                        bestScore = score
                        bestC = c
                        bestD = d
                    }
                }
            }

            if (bestC == null || bestD == null) break
            val c: Cell = bestC
            val d: Direction = bestD

            val rev = ArrayList<Cell>()
            rev.add(c)
            var cur: Cell = c
            var prevStepDir = d.opposite()
            while (rev.size < 14) {
                val nextCell = cur.step(prevStepDir)
                val candidates = if (rev.size == 1) {
                    if (nextCell.x in 0 until w && nextCell.y in 0 until h &&
                        grid[nextCell.y * w + nextCell.x] == -1 && nextCell !in rev
                    ) listOf(nextCell) else emptyList()
                } else {
                    dirs.map { cur.step(it) }.filter { nc ->
                        nc.x in 0 until w && nc.y in 0 until h &&
                            grid[nc.y * w + nc.x] == -1 && nc !in rev
                    }
                }

                val candidate = candidates.filter { nc ->
                    var onRay = false
                    var rx = c.x + d.dx
                    var ry = c.y + d.dy
                    while (rx in 0 until w && ry in 0 until h) {
                        if (rx == nc.x && ry == nc.y) {
                            onRay = true
                            break
                        }
                        rx += d.dx
                        ry += d.dy
                    }
                    !onRay
                }.minByOrNull { nc ->
                    // Avoid stranding neighbors of cur and prefer lower-degree neighbors
                    var score = 0
                    for (nd in dirs) {
                        val nb = cur.step(nd)
                        if (nb != nc && nb.x in 0 until w && nb.y in 0 until h &&
                            grid[nb.y * w + nb.x] == -1 && nb !in rev
                        ) {
                            var nbDeg = 0
                            for (nd2 in dirs) {
                                val n2 = nb.step(nd2)
                                if (n2 != nc && n2.x in 0 until w && n2.y in 0 until h &&
                                    grid[n2.y * w + n2.x] == -1 && n2 !in rev
                                ) nbDeg++
                            }
                            if (nbDeg == 0) score += 100
                        }
                    }
                    if (nc == nextCell) score -= 5
                    score
                } ?: break

                rev.add(candidate)
                prevStepDir = Direction.between(cur, candidate)!!
                cur = candidate
            }

            val cells = rev.reversed()
            val headDir = if (cells.size >= 2) {
                Direction.between(cells[cells.size - 2], cells.last())!!
            } else d
            val id = arrows.size
            arrows.add(Arrow(id, cells, headDir, rng.nextInt(4)))
            for (ac in cells) {
                grid[ac.y * w + ac.x] = id
                remaining--
            }
        }
        return mergeSingleCellArrowsIntoTails(arrows, grid, w, h)
    }
}
