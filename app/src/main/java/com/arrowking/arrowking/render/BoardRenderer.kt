package com.arrowking.arrowking.render

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.arrowking.arrowking.data.GameThemePalette
import com.arrowking.arrowking.game.model.Arrow
import com.arrowking.arrowking.game.model.Cell
import com.arrowking.arrowking.game.model.Direction
import com.arrowking.arrowking.game.model.LevelMask
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class SlidingArrowAnim(
    val arrow: Arrow,
    val startTimeNanos: Long,
    val exitPathCells: List<Cell>
)

data class ErrorHighlightState(
    val tappedArrowId: Int,
    val blockerArrowId: Int,
    val blockerHeadCell: Cell,
    val blockerDirection: Direction,
    val startTimeNanos: Long
)

data class GuidanceState(
    val arrow: Arrow,
    val blockerCell: Cell?,
    val blockerArrowId: Int?
)

object BoardRenderer {

    fun DrawScope.drawPuzzleBoard(
        mask: LevelMask,
        remainingArrows: List<Arrow>,
        slidingAnims: Map<Int, SlidingArrowAnim>,
        errorState: ErrorHighlightState?,
        hintArrowId: Int?,
        guidanceState: GuidanceState?,
        theme: GameThemePalette,
        transform: BoardTransform,
        showGrid: Boolean,
        threeDMode: Boolean,
        nowNanos: Long,
        shakeOffsetPx: Offset
    ) {
        val cellPx = transform.effectiveCellSize
        if (cellPx <= 0.5f) return

        val strokeWidth = max(cellPx * 0.16f, 1.6.dp.toPx())
        val padRadius = CornerRadius(cellPx * 0.22f, cellPx * 0.22f)

        // 1. Subtle recessed silhouette pad so the overall shape reads immediately
        for (cell in mask.cells) {
            val tl = transform.gridToScreen(cell.x.toFloat(), cell.y.toFloat()) + shakeOffsetPx
            drawRoundRect(
                color = theme.boardWellBg.copy(alpha = 0.72f),
                topLeft = Offset(tl.x - 0.5f, tl.y - 0.5f),
                size = Size(cellPx + 1f, cellPx + 1f),
                cornerRadius = padRadius
            )
        }

        // 2. Optional faint dotted grid guides
        if (showGrid) {
            val dotColor = theme.boardWellBorder.copy(alpha = 0.65f)
            val dotRadius = max(1f, cellPx * 0.045f)
            for (cell in mask.cells) {
                val cPos = transform.gridToScreen(cell.x + 0.5f, cell.y + 0.5f) + shakeOffsetPx
                drawCircle(color = dotColor, radius = dotRadius, center = cPos)
            }
        }

        // 3. Stationary remaining arrows
        for (arrow in remainingArrows) {
            if (arrow.id in slidingAnims) continue

            val baseColor = theme.arrowColors[arrow.colorIndex % theme.arrowColors.size]
            var bumpOffset = Offset.Zero
            if (errorState != null && errorState.tappedArrowId == arrow.id) {
                val elapsedSec = ((nowNanos - errorState.startTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.32f)
                val norm = elapsedSec / 0.32f
                val bump = sin(norm * PI).toFloat() * 0.35f * cellPx
                bumpOffset = Offset(
                    arrow.headDirection.dx * bump,
                    arrow.headDirection.dy * bump
                )
            }

            val isHinted = hintArrowId == arrow.id
            if (isHinted) {
                val pulse = 0.5f + 0.5f * sin((nowNanos / 1_000_000_000.0) * 6.0).toFloat()
                drawSnakeArrow(
                    points = arrow.cells.map {
                        transform.gridToScreen(it.x + 0.5f, it.y + 0.5f) + shakeOffsetPx + bumpOffset
                    },
                    headDir = arrow.headDirection,
                    color = Color(0xFFFFD54F).copy(alpha = 0.45f + pulse * 0.35f),
                    strokeWidth = strokeWidth * (1.85f + pulse * 0.35f),
                    cellPx = cellPx,
                    threeDMode = false,
                    alpha = 1f
                )
            }

            val screenPoints = arrow.cells.map {
                transform.gridToScreen(it.x + 0.5f, it.y + 0.5f) + shakeOffsetPx + bumpOffset
            }

            drawSnakeArrow(
                points = screenPoints,
                headDir = arrow.headDirection,
                color = baseColor,
                strokeWidth = strokeWidth,
                cellPx = cellPx,
                threeDMode = threeDMode,
                alpha = 1f
            )

            // Pulsing gold/white halo + small floating hand indicator on hinted arrow head
            if (isHinted && screenPoints.isNotEmpty()) {
                val headScreen = screenPoints.last()
                val pulse = 0.5f + 0.5f * sin((nowNanos / 1_000_000_000.0) * 6.0).toFloat()
                drawCircle(
                    color = Color(0xFFFFFFFF).copy(alpha = 0.55f + 0.3f * pulse),
                    radius = cellPx * (0.44f + 0.14f * pulse),
                    center = headScreen,
                    style = Stroke(width = max(2.dp.toPx(), strokeWidth * 0.45f))
                )
                drawCircle(
                    color = Color(0xFFFFB300).copy(alpha = 0.30f + 0.2f * pulse),
                    radius = cellPx * (0.62f + 0.12f * pulse),
                    center = headScreen
                )
            }
        }

        // 4. Sliding-out snake animations
        for (anim in slidingAnims.values) {
            val elapsedSec = ((nowNanos - anim.startTimeNanos) / 1_000_000_000f).coerceAtLeast(0f)
            val speedCellsPerSec = max(14f, anim.exitPathCells.size * 2.6f)
            val steps = elapsedSec * speedCellsPerSec
            val baseColor = theme.arrowColors[anim.arrow.colorIndex % theme.arrowColors.size]
            val totalTrack = anim.arrow.cells + anim.exitPathCells
            val len = anim.arrow.cells.size
            val maxShift = totalTrack.size - 1

            val alpha = if (steps > anim.exitPathCells.size) {
                (1f - (steps - anim.exitPathCells.size) / max(1f, len.toFloat())).coerceIn(0f, 1f)
            } else 1f

            if (alpha <= 0.01f) continue

            val points = buildInterpolatedSnakePoints(
                track = totalTrack,
                snakeLen = len,
                shiftCells = steps.coerceAtMost(maxShift.toFloat()),
                transform = transform,
                shakeOffsetPx = shakeOffsetPx
            )
            if (points.size >= 1) {
                drawSnakeArrow(
                    points = points,
                    headDir = anim.arrow.headDirection,
                    color = baseColor,
                    strokeWidth = strokeWidth,
                    cellPx = cellPx,
                    threeDMode = threeDMode,
                    alpha = alpha
                )
            }
        }

        // 5. Long-press Guidance ray + blocker highlight
        if (guidanceState != null) {
            val arrow = guidanceState.arrow
            val baseColor = theme.arrowColors[arrow.colorIndex % theme.arrowColors.size]
            val headPt = transform.gridToScreen(arrow.head.x + 0.5f, arrow.head.y + 0.5f) + shakeOffsetPx
            val endGridCell = guidanceState.blockerCell ?: run {
                var cur = arrow.head
                while (cur.x in 0 until mask.width && cur.y in 0 until mask.height) {
                    cur = cur.step(arrow.headDirection)
                }
                cur
            }
            val endPt = transform.gridToScreen(endGridCell.x + 0.5f, endGridCell.y + 0.5f) + shakeOffsetPx
            drawLine(
                color = baseColor.copy(alpha = 0.85f),
                start = headPt,
                end = endPt,
                strokeWidth = max(2.dp.toPx(), strokeWidth * 0.65f),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(cellPx * 0.25f, cellPx * 0.22f))
            )
            drawCircle(
                color = baseColor.copy(alpha = 0.30f),
                radius = cellPx * 0.45f,
                center = endPt
            )
            drawCircle(
                color = baseColor,
                radius = cellPx * 0.18f,
                center = endPt
            )
            if (guidanceState.blockerCell != null) {
                drawCircle(
                    color = Color(0xFFFF5252),
                    radius = cellPx * 0.48f,
                    center = endPt,
                    style = Stroke(width = max(2.dp.toPx(), strokeWidth * 0.5f))
                )
            }
        }

        // 6. Error state: red glow ring + crisp X marker on blocking arrow head (colour-independent clarity!)
        if (errorState != null) {
            val elapsedSec = ((nowNanos - errorState.startTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.9f)
            val fade = (1f - elapsedSec / 0.9f).coerceIn(0f, 1f)
            if (fade > 0.02f) {
                val center = transform.gridToScreen(
                    errorState.blockerHeadCell.x + 0.5f,
                    errorState.blockerHeadCell.y + 0.5f
                ) + shakeOffsetPx

                val ringRadius = max(14.dp.toPx(), cellPx * (0.58f + 0.18f * (1f - fade)))
                drawCircle(
                    color = Color(0xFFE53935).copy(alpha = 0.35f * fade),
                    radius = ringRadius * 1.25f,
                    center = center
                )
                drawCircle(
                    color = Color(0xFFD32F2F).copy(alpha = 0.92f * fade),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = max(2.5.dp.toPx(), strokeWidth * 0.6f))
                )
                // White-backed crisp X badge for high contrast even over red arrows
                val badgeR = max(9.dp.toPx(), cellPx * 0.34f)
                drawCircle(
                    color = Color.White.copy(alpha = 0.95f * fade),
                    radius = badgeR,
                    center = center
                )
                val xHalf = badgeR * 0.48f
                val xStroke = max(2.2.dp.toPx(), badgeR * 0.28f)
                drawLine(
                    color = Color(0xFFD32F2F).copy(alpha = fade),
                    start = Offset(center.x - xHalf, center.y - xHalf),
                    end = Offset(center.x + xHalf, center.y + xHalf),
                    strokeWidth = xStroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFFD32F2F).copy(alpha = fade),
                    start = Offset(center.x + xHalf, center.y - xHalf),
                    end = Offset(center.x - xHalf, center.y + xHalf),
                    strokeWidth = xStroke,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    private fun buildInterpolatedSnakePoints(
        track: List<Cell>,
        snakeLen: Int,
        shiftCells: Float,
        transform: BoardTransform,
        shakeOffsetPx: Offset
    ): List<Offset> {
        val tailPos = shiftCells
        val headPos = shiftCells + (snakeLen - 1)
        val firstInt = kotlin.math.ceil(tailPos.toDouble()).toInt().coerceIn(0, track.size - 1)
        val lastInt = kotlin.math.floor(headPos.toDouble()).toInt().coerceIn(0, track.size - 1)

        fun sampleAt(pos: Float): Offset {
            val clamped = pos.coerceIn(0f, (track.size - 1).toFloat())
            val i0 = kotlin.math.floor(clamped.toDouble()).toInt()
            val i1 = min(track.size - 1, i0 + 1)
            val frac = clamped - i0
            val gx = track[i0].x + (track[i1].x - track[i0].x) * frac + 0.5f
            val gy = track[i0].y + (track[i1].y - track[i0].y) * frac + 0.5f
            return transform.gridToScreen(gx, gy) + shakeOffsetPx
        }

        val pts = ArrayList<Offset>(snakeLen + 2)
        pts.add(sampleAt(tailPos))
        for (idx in firstInt..lastInt) {
            val pt = transform.gridToScreen(track[idx].x + 0.5f, track[idx].y + 0.5f) + shakeOffsetPx
            if (pts.isEmpty() || (pts.last() - pt).getDistance() > 0.25f) {
                pts.add(pt)
            }
        }
        val headPt = sampleAt(headPos)
        if (pts.isEmpty() || (pts.last() - headPt).getDistance() > 0.25f) {
            pts.add(headPt)
        }
        return pts
    }

    private fun DrawScope.drawSnakeArrow(
        points: List<Offset>,
        headDir: Direction,
        color: Color,
        strokeWidth: Float,
        cellPx: Float,
        threeDMode: Boolean,
        alpha: Float
    ) {
        if (points.isEmpty()) return

        val headTip = points.last()
        val dirVec = Offset(headDir.dx.toFloat(), headDir.dy.toFloat())
        val perpVec = Offset(-dirVec.y, dirVec.x)

        // Arrowhead sizing proportional to cell & stroke
        val headLength = max(strokeWidth * 2.35f, cellPx * 0.34f)
        val headHalfWidth = max(strokeWidth * 1.45f, cellPx * 0.21f)

        if (threeDMode) {
            val shadowOffset = Offset(strokeWidth * 0.35f, strokeWidth * 0.55f)
            drawBodyAndHead(
                points = points.map { it + shadowOffset },
                headTip = headTip + shadowOffset,
                dirVec = dirVec,
                perpVec = perpVec,
                headLength = headLength,
                headHalfWidth = headHalfWidth,
                color = Color.Black.copy(alpha = 0.22f * alpha),
                strokeWidth = strokeWidth * 1.08f
            )
        }

        drawBodyAndHead(
            points = points,
            headTip = headTip,
            dirVec = dirVec,
            perpVec = perpVec,
            headLength = headLength,
            headHalfWidth = headHalfWidth,
            color = color.copy(alpha = alpha),
            strokeWidth = strokeWidth
        )

        if (threeDMode) {
            val highlightOffset = Offset(-strokeWidth * 0.12f, -strokeWidth * 0.16f)
            drawBodyAndHead(
                points = points.map { it + highlightOffset },
                headTip = headTip + highlightOffset,
                dirVec = dirVec,
                perpVec = perpVec,
                headLength = headLength * 0.72f,
                headHalfWidth = headHalfWidth * 0.65f,
                color = Color.White.copy(alpha = 0.22f * alpha),
                strokeWidth = strokeWidth * 0.38f
            )
        }
    }

    private fun DrawScope.drawBodyAndHead(
        points: List<Offset>,
        headTip: Offset,
        dirVec: Offset,
        perpVec: Offset,
        headLength: Float,
        headHalfWidth: Float,
        color: Color,
        strokeWidth: Float
    ) {
        if (points.size >= 2) {
            val bodyPath = Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
            drawPath(
                path = bodyPath,
                color = color,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        } else {
            // 1-cell arrow: short crisp stem behind head
            val stemStart = headTip - dirVec * (headLength * 0.75f)
            drawLine(
                color = color,
                start = stemStart,
                end = headTip,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Solid crisp arrowhead extending cleanly forward from the head point
        val tip = headTip + dirVec * (headLength * 0.45f)
        val baseCenter = headTip - dirVec * (headLength * 0.45f)
        val leftWing = baseCenter + perpVec * headHalfWidth
        val rightWing = baseCenter - perpVec * headHalfWidth

        val headPath = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(leftWing.x, leftWing.y)
            lineTo(baseCenter.x + dirVec.x * (headLength * 0.12f), baseCenter.y + dirVec.y * (headLength * 0.12f))
            lineTo(rightWing.x, rightWing.y)
            close()
        }
        drawPath(path = headPath, color = color)
    }
}
