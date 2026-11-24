package com.arrowking.arrowking

import com.arrowking.arrowking.game.generator.LevelGenerator
import com.arrowking.arrowking.game.model.Direction
import com.arrowking.arrowking.game.solver.LevelSolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelGeneratorAndSolverTest {

    @Test
    fun `structural checks pass across representative and milestone levels`() {
        val sampleLevels = listOf(1, 2, 3, 5, 10, 15, 25, 50, 75, 100, 150, 200, 300, 500, 1000, 1500)

        println("LEVEL | GRID  | ARROWS | DEPTH | START_FREE | AVG_FREE | EST_SEC | SHAPE")
        println("------+-------+--------+-------+------------+----------+---------+----------------")

        for (lvl in sampleLevels) {
            val generated = LevelGenerator.generateLevel(lvl)
            val mask = generated.mask
            val arrows = generated.arrows

            // 1. 100% mask coverage, no cell outside mask, no overlaps
            val seen = HashSet<com.arrowking.arrowking.game.model.Cell>(mask.cellCount)
            for (arrow in arrows) {
                assertTrue("Arrow empty in level $lvl", arrow.cells.isNotEmpty())
                val own = HashSet<com.arrowking.arrowking.game.model.Cell>(arrow.cells.size)
                for (i in arrow.cells.indices) {
                    val c = arrow.cells[i]
                    assertTrue("Cell $c outside mask in level $lvl", c in mask.cells)
                    assertTrue("Repeated cell $c in arrow ${arrow.id}", own.add(c))
                    assertTrue("Overlapping cell $c in level $lvl", seen.add(c))
                    if (i > 0) {
                        assertTrue("Disconnected arrow step in level $lvl", arrow.cells[i - 1].isAdjacentTo(c))
                    }
                }

                // 2. Head direction equals last step
                if (arrow.cells.size >= 2) {
                    val expected = Direction.between(arrow.cells[arrow.cells.size - 2], arrow.cells.last())
                    assertEquals("Head direction mismatch in level $lvl", expected, arrow.headDirection)
                }

                // 3. Arrow's own body is never on its own ray
                val ray = LevelSolver.rayCells(arrow, mask.width, mask.height)
                for (rc in ray) {
                    assertFalse("Self-ray intersection in level $lvl arrow ${arrow.id}", rc in own)
                }
            }
            assertEquals("Full mask coverage mismatch in level $lvl", mask.cells.size, seen.size)

            // 4. Greedy solver clears the whole board
            val analysis = LevelSolver.solveAndAnalyze(arrows, mask)
            assertTrue("Invalid structure in level $lvl: ${analysis.failureReason}", analysis.isValidStructure)
            assertTrue("Unsolvable level $lvl: ${analysis.failureReason}", analysis.isSolvable)
            assertEquals(arrows.size, analysis.removalOrder.size)

            // 5. 1-cell arrows at most 1%
            assertTrue(
                "Too many 1-cell arrows in level $lvl: ${analysis.singleCellRatio}",
                analysis.singleCellRatio <= 0.01f
            )

            // 6. Hint always selects a FREE arrow
            val hint = LevelSolver.pickBestHintArrow(arrows, mask.width, mask.height)
            assertNotNull("Hint should find a free arrow at start of level $lvl", hint)
            val occupied = HashMap<com.arrowking.arrowking.game.model.Cell, Int>()
            for (a in arrows) for (c in a.cells) occupied[c] = a.id
            assertTrue("Hint arrow must be FREE", LevelSolver.isArrowFree(hint!!, occupied, mask.width, mask.height))

            val m = generated.metrics
            println(
                String.format(
                    "%5d | %2dx%-2d | %6d | %5d | %10d | %8.2f | %7d | %s",
                    lvl, m.gridWidth, m.gridHeight, m.arrowCount,
                    m.dependencyDepth, m.startFreeCount, m.avgFreeCount,
                    m.estimatedSolveSeconds, m.shapeName
                )
            )
        }
    }

    @Test
    fun `levels 1 to 1500 generate deterministic valid and solvable boards`() {
        // Verify determinism on level 1 and 25
        val l1a = LevelGenerator.generateLevel(1)
        val l1b = LevelGenerator.generateLevel(1)
        assertEquals(l1a.arrows, l1b.arrows)
        assertTrue("Level 25 must be boss", LevelGenerator.generateLevel(25).isBoss)

        // Check dense sweep across 1..1500 (every 10th level + 1..30)
        val toCheck = (1..30).toList() + (40..1500 step 10).toList()
        for (lvl in toCheck) {
            val g = LevelGenerator.generateLevel(lvl)
            val analysis = LevelSolver.solveAndAnalyze(g.arrows, g.mask)
            assertTrue("Level $lvl failed validity: ${analysis.failureReason}", analysis.isValidStructure)
            assertTrue("Level $lvl failed solvability: ${analysis.failureReason}", analysis.isSolvable)
            assertTrue("Level $lvl single-cell > 1%", analysis.singleCellRatio <= 0.01f)
        }
    }
}
