package com.arrowking.arrowking

import androidx.compose.ui.geometry.Offset
import com.arrowking.arrowking.game.generator.LevelGenerator
import com.arrowking.arrowking.game.model.Arrow
import com.arrowking.arrowking.game.model.Cell
import com.arrowking.arrowking.game.model.Direction
import com.arrowking.arrowking.game.shapes.MaskSizeTier
import com.arrowking.arrowking.game.shapes.ShapeLibrary
import com.arrowking.arrowking.render.BoardTransform
import com.arrowking.arrowking.render.HitTester
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeLibraryAndHitTest {

    @Test
    fun `shape library has at least 60 templates and every template and size is single connected component`() {
        assertTrue("Expected at least 60 silhouettes", ShapeLibrary.templates.size >= 60)

        for (template in ShapeLibrary.templates) {
            for (tier in MaskSizeTier.entries) {
                val mask = ShapeLibrary.buildMaskFromTemplate(template, tier.scaleTarget)
                assertTrue("Mask ${template.id} tier $tier empty", mask.cellCount > 0)
                assertTrue(
                    "Mask ${template.id} tier $tier disconnected specks",
                    ShapeLibrary.isSingleConnectedComponent(mask.cells)
                )
                assertTrue(
                    "Mask ${template.id} tier $tier fill ratio too low: ${mask.fillRatio}",
                    mask.fillRatio >= 0.25f
                )
            }
        }
    }

    @Test
    fun `consecutive levels never repeat the same shape`() {
        var prevId = ""
        for (lvl in 1..200) {
            val diff = LevelGenerator.difficultyForLevel(lvl)
            val mask = ShapeLibrary.shapeForLevel(lvl, diff.gridMaxSide, LevelGenerator.hashSeed(lvl, 0))
            assertNotEquals("Repeated shape at level $lvl: $prevId", prevId, mask.shapeId)
            prevId = mask.shapeId
        }
    }

    @Test
    fun `hit detection picks nearest arrow within tolerance and ignores empty space or sliding arrows`() {
        val arrowA = Arrow(
            id = 1,
            cells = listOf(Cell(1, 1), Cell(2, 1), Cell(3, 1)),
            headDirection = Direction.RIGHT,
            colorIndex = 0
        )
        val arrowB = Arrow(
            id = 2,
            cells = listOf(Cell(1, 4), Cell(2, 4), Cell(3, 4)),
            headDirection = Direction.RIGHT,
            colorIndex = 1
        )
        val transform = BoardTransform.compute(
            canvasWidth = 600f,
            canvasHeight = 600f,
            gridWidth = 10,
            gridHeight = 10,
            marginPx = 50f,
            zoom = 1f,
            panX = 0f,
            panY = 0f
        )

        val touchNearA = transform.gridToScreen(2.2f, 1.6f)
        val hitA = HitTester.findNearestArrow(
            touchScreen = touchNearA,
            transform = transform,
            arrows = listOf(arrowA, arrowB),
            slidingArrowIds = emptySet(),
            minTouchPx = 48f
        )
        assertNotNull(hitA)
        assertEquals(1, hitA!!.id)

        // Ignored when arrowA is currently sliding
        val hitWhileSliding = HitTester.findNearestArrow(
            touchScreen = touchNearA,
            transform = transform,
            arrows = listOf(arrowA, arrowB),
            slidingArrowIds = setOf(1),
            minTouchPx = 48f
        )
        assertNull(hitWhileSliding)

        // Ignored when tapping far empty space
        val touchFar = transform.gridToScreen(8.5f, 8.5f)
        val hitEmpty = HitTester.findNearestArrow(
            touchScreen = touchFar,
            transform = transform,
            arrows = listOf(arrowA, arrowB),
            slidingArrowIds = emptySet(),
            minTouchPx = 48f
        )
        assertNull(hitEmpty)
    }
}
