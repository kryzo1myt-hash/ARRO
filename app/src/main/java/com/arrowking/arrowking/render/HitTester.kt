package com.arrowking.arrowking.render

import androidx.compose.ui.geometry.Offset
import com.arrowking.arrowking.game.model.Arrow
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class BoardTransform(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val gridWidth: Int,
    val gridHeight: Int,
    val baseCellSize: Float,
    val originX: Float,
    val originY: Float,
    val zoom: Float,
    val panX: Float,
    val panY: Float
) {
    val effectiveCellSize: Float get() = baseCellSize * zoom
    private val centerX: Float get() = canvasWidth * 0.5f
    private val centerY: Float get() = canvasHeight * 0.5f

    fun gridToScreen(gx: Float, gy: Float): Offset {
        val bx = originX + gx * baseCellSize
        val by = originY + gy * baseCellSize
        return Offset(
            x = centerX + (bx - centerX) * zoom + panX,
            y = centerY + (by - centerY) * zoom + panY
        )
    }

    fun screenToGrid(sx: Float, sy: Float): Offset {
        val bx = (sx - panX - centerX) / zoom + centerX
        val by = (sy - panY - centerY) / zoom + centerY
        return Offset(
            x = (bx - originX) / baseCellSize,
            y = (by - originY) / baseCellSize
        )
    }

    companion object {
        fun compute(
            canvasWidth: Float,
            canvasHeight: Float,
            gridWidth: Int,
            gridHeight: Int,
            marginPx: Float,
            zoom: Float = 1f,
            panX: Float = 0f,
            panY: Float = 0f
        ): BoardTransform {
            val availW = max(1f, canvasWidth - 2f * marginPx)
            val availH = max(1f, canvasHeight - 2f * marginPx)
            val baseCell = min(availW / max(1, gridWidth), availH / max(1, gridHeight))
            val boardW = baseCell * gridWidth
            val boardH = baseCell * gridHeight
            val originX = (canvasWidth - boardW) * 0.5f
            val originY = (canvasHeight - boardH) * 0.5f
            return BoardTransform(
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                gridWidth = gridWidth,
                gridHeight = gridHeight,
                baseCellSize = baseCell,
                originX = originX,
                originY = originY,
                zoom = zoom,
                panX = panX,
                panY = panY
            )
        }
    }
}

object HitTester {

    /**
     * Converts touch point [touchScreen] using [transform] and picks the arrow
     * whose polyline is nearest within tolerance = max(0.7 * cellSize, minTouchPx).
     * Ignores arrows in [slidingArrowIds]. Returns null if nothing is in tolerance.
     */
    fun findNearestArrow(
        touchScreen: Offset,
        transform: BoardTransform,
        arrows: List<Arrow>,
        slidingArrowIds: Set<Int>,
        minTouchPx: Float
    ): Arrow? {
        if (transform.effectiveCellSize <= 0f || arrows.isEmpty()) return null

        val gridPt = transform.screenToGrid(touchScreen.x, touchScreen.y)
        val tolerancePx = max(0.7f * transform.effectiveCellSize, minTouchPx)
        val toleranceGrid = tolerancePx / transform.effectiveCellSize

        var bestArrow: Arrow? = null
        var bestDist = Float.MAX_VALUE

        for (arrow in arrows) {
            if (arrow.id in slidingArrowIds) continue
            val d = distanceToArrowPolylineGrid(gridPt.x, gridPt.y, arrow)
            if (d <= toleranceGrid && d < bestDist) {
                bestDist = d
                bestArrow = arrow
            }
        }
        return bestArrow
    }

    fun distanceToArrowPolylineGrid(gx: Float, gy: Float, arrow: Arrow): Float {
        val cells = arrow.cells
        if (cells.isEmpty()) return Float.MAX_VALUE
        if (cells.size == 1) {
            val cx = cells[0].x + 0.5f
            val cy = cells[0].y + 0.5f
            return hypot(gx - cx, gy - cy)
        }

        var minDist = Float.MAX_VALUE
        for (i in 0 until cells.size - 1) {
            val ax = cells[i].x + 0.5f
            val ay = cells[i].y + 0.5f
            val bx = cells[i + 1].x + 0.5f
            val by = cells[i + 1].y + 0.5f
            val d = pointToSegmentDist(gx, gy, ax, ay, bx, by)
            if (d < minDist) minDist = d
        }
        return minDist
    }

    private fun pointToSegmentDist(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float
    ): Float {
        val vx = bx - ax
        val vy = by - ay
        val wx = px - ax
        val wy = py - ay
        val lenSq = vx * vx + vy * vy
        val t = if (lenSq <= 1e-6f) 0f else ((wx * vx + wy * vy) / lenSq).coerceIn(0f, 1f)
        val projX = ax + t * vx
        val projY = ay + t * vy
        val dx = px - projX
        val dy = py - projY
        return sqrt(dx * dx + dy * dy)
    }
}
