package com.arrowking.arrowking.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Hexagon
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arrowking.arrowking.data.AppSettings
import com.arrowking.arrowking.data.PlayerProgress
import com.arrowking.arrowking.game.model.Arrow
import com.arrowking.arrowking.render.BoardRenderer.drawPuzzleBoard
import com.arrowking.arrowking.render.BoardTransform
import com.arrowking.arrowking.render.HitTester
import com.arrowking.arrowking.ui.BoardUiState
import com.arrowking.arrowking.ui.theme.LocalGameTheme
import com.arrowking.arrowking.ui.theme.StreakFlameOrange
import com.arrowking.arrowking.ui.theme.WaterDropBlue
import com.arrowking.arrowking.ui.theme.WaterDropEmpty
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun GameScreen(
    boardState: BoardUiState,
    progress: PlayerProgress,
    settings: AppSettings,
    onBackPressed: () -> Unit,
    onThemesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onArrowTapped: (Arrow) -> Unit,
    onArrowLongPressed: (Arrow) -> Unit,
    onClearGuidance: () -> Unit,
    onToggleGrid: () -> Unit,
    onHintClick: () -> Unit
) {
    BackHandler { onBackPressed() }

    val theme = LocalGameTheme.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val flingDecay = rememberSplineBasedDecay<Float>()

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    val panX = remember { Animatable(0f) }
    val panY = remember { Animatable(0f) }

    // Reset zoom/pan when level changes
    LaunchedEffect(boardState.level?.levelNumber) {
        zoom = 1f
        panX.updateBounds(null, null)
        panY.updateBounds(null, null)
        panX.snapTo(0f)
        panY.snapTo(0f)
    }

    // Continuous frame clock while sliding, error highlight, or hint pulse is active
    var frameNanos by remember { mutableLongStateOf(System.nanoTime()) }
    val needsAnimationTick = boardState.slidingAnims.isNotEmpty() ||
        boardState.errorState != null ||
        boardState.hintArrowId != null ||
        boardState.showFirstTapPointer
    LaunchedEffect(needsAnimationTick) {
        if (needsAnimationTick) {
            while (true) {
                withFrameNanos { frameNanos = it }
            }
        }
    }

    val shakeOffsetPx = remember(boardState.errorState, frameNanos, settings.reduceAnimations) {
        val err = boardState.errorState
        if (err == null || settings.reduceAnimations) Offset.Zero else {
            val sec = ((frameNanos - err.startTimeNanos) / 1_000_000_000f).coerceIn(0f, 0.3f)
            val decay = (1f - sec / 0.3f).coerceIn(0f, 1f)
            val dx = sin(sec * 75.0).toFloat() * with(density) { 5.dp.toPx() } * decay
            Offset(dx, 0f)
        }
    }

    val marginPx = with(density) { 20.dp.toPx() }
    val minTouchPx = with(density) { 24.dp.toPx() }

    val transform = remember(canvasSize, boardState.level, zoom, panX.value, panY.value) {
        val lvl = boardState.level
        if (lvl == null || canvasSize.width == 0 || canvasSize.height == 0) null
        else BoardTransform.compute(
            canvasWidth = canvasSize.width.toFloat(),
            canvasHeight = canvasSize.height.toFloat(),
            gridWidth = lvl.mask.width,
            gridHeight = lvl.mask.height,
            marginPx = marginPx,
            zoom = zoom,
            panX = panX.value,
            panY = panY.value
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.paperBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Top bar: Back (left), centered Level N (golden-brown #C48A1D), Palette & Hexagon Settings (right)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(
                onClick = onBackPressed,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .testTag("game_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Pause & Menu",
                    tint = theme.primaryText
                )
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val lvlNum = boardState.level?.levelNumber ?: progress.currentLevel
                Text(
                    text = "Level $lvlNum",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = theme.accentGold
                )
                if (boardState.level?.isBoss == true) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = Color(0xFFD9822B),
                        shape = RoundedCornerShape(99.dp)
                    ) {
                        Text(
                            text = "BOSS",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onThemesClick,
                    modifier = Modifier.testTag("game_palette_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Palette,
                        contentDescription = "Themes & Arrow Colours",
                        tint = theme.primaryText
                    )
                }
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.testTag("game_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Hexagon,
                        contentDescription = "Settings",
                        tint = theme.primaryText
                    )
                }
            }
        }

        HorizontalDivider(color = theme.dividerColor, thickness = 1.dp)

        // Sub-header: 3 blue water drops (left), IQ + streak flame >= 5 (right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (lifeIdx in 1..3) {
                    val alive = lifeIdx <= boardState.livesLeft
                    val dropScale by animateFloatAsState(
                        targetValue = if (alive) 1f else 0.86f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                        label = "drop_$lifeIdx"
                    )
                    Icon(
                        imageVector = Icons.Filled.WaterDrop,
                        contentDescription = if (alive) "Life active" else "Life lost",
                        tint = if (alive) WaterDropBlue else WaterDropEmpty,
                        modifier = Modifier
                            .size(26.dp)
                            .scale(dropScale)
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(visible = boardState.streak >= 5) {
                    Surface(
                        color = StreakFlameOrange.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(99.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.LocalFireDepartment,
                                contentDescription = "Streak",
                                tint = StreakFlameOrange,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${boardState.streak}",
                                style = MaterialTheme.typography.labelSmall,
                                color = StreakFlameOrange
                            )
                        }
                    }
                }
                Surface(
                    color = theme.paperSurface,
                    shape = RoundedCornerShape(99.dp)
                ) {
                    Text(
                        text = "IQ ${progress.iqScore}",
                        style = MaterialTheme.typography.labelLarge,
                        color = theme.primaryText,
                        modifier = Modifier
                            .border(1.dp, theme.dividerColor, RoundedCornerShape(99.dp))
                            .padding(horizontal = 11.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // Center Puzzle Board Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { canvasSize = it }
        ) {
            if (boardState.isLoading || boardState.level == null || transform == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = theme.accentGold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Shaping puzzle...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.primaryText.copy(alpha = 0.7f)
                    )
                }
            } else {
                val lvl = boardState.level
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("puzzle_board_canvas")
                        .pointerInput(lvl.levelNumber) {
                            awaitEachGesture {
                                val velocityTracker = VelocityTracker()
                                var zoomDeltaAcc = 1f
                                var panAcc = Offset.Zero
                                var pastTouchSlop = false
                                val touchSlop = viewConfiguration.touchSlop

                                awaitFirstDown(requireUnconsumed = false)
                                scope.launch {
                                    panX.stop()
                                    panY.stop()
                                }

                                do {
                                    val event = awaitPointerEvent()
                                    val canceled = event.changes.any { it.isConsumed }
                                    if (!canceled) {
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()

                                        if (!pastTouchSlop) {
                                            zoomDeltaAcc *= zoomChange
                                            panAcc += panChange
                                            val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                            val zoomMotion = abs(1 - zoomDeltaAcc) * centroidSize
                                            val panMotion = panAcc.getDistance()
                                            if (zoomMotion > touchSlop || panMotion > touchSlop) {
                                                pastTouchSlop = true
                                            }
                                        }

                                        if (pastTouchSlop) {
                                            if (event.changes.count { it.pressed } == 1) {
                                                val p = event.changes.first { it.pressed }
                                                velocityTracker.addPosition(p.uptimeMillis, p.position)
                                            }
                                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                                val newZoom = (zoom * zoomChange).coerceIn(1f, 5f)
                                                val maxPanX = size.width * 0.5f * newZoom
                                                val maxPanY = size.height * 0.5f * newZoom
                                                zoom = newZoom
                                                scope.launch {
                                                    panX.updateBounds(-maxPanX, maxPanX)
                                                    panY.updateBounds(-maxPanY, maxPanY)
                                                    panX.snapTo((panX.value + panChange.x).coerceIn(-maxPanX, maxPanX))
                                                    panY.snapTo((panY.value + panChange.y).coerceIn(-maxPanY, maxPanY))
                                                }
                                            }
                                            event.changes.forEach {
                                                if (it.positionChanged()) it.consume()
                                            }
                                        }
                                    }
                                } while (!canceled && event.changes.any { it.pressed })

                                if (pastTouchSlop) {
                                    val vel = velocityTracker.calculateVelocity()
                                    val maxPanX = size.width * 0.5f * zoom
                                    val maxPanY = size.height * 0.5f * zoom
                                    scope.launch {
                                        panX.updateBounds(-maxPanX, maxPanX)
                                        panY.updateBounds(-maxPanY, maxPanY)
                                        launch { panX.animateDecay(vel.x, flingDecay) }
                                        launch { panY.animateDecay(vel.y, flingDecay) }
                                    }
                                }
                            }
                        }
                        .pointerInput(lvl.levelNumber, boardState.remainingArrows, transform) {
                            detectTapGestures(
                                onDoubleTap = {
                                    scope.launch {
                                        zoom = 1f
                                        panX.updateBounds(null, null)
                                        panY.updateBounds(null, null)
                                        launch { panX.animateTo(0f, spring()) }
                                        launch { panY.animateTo(0f, spring()) }
                                    }
                                },
                                onLongPress = { offset ->
                                    val hit = HitTester.findNearestArrow(
                                        touchScreen = offset,
                                        transform = transform,
                                        arrows = boardState.remainingArrows,
                                        slidingArrowIds = boardState.slidingAnims.keys,
                                        minTouchPx = minTouchPx
                                    )
                                    if (hit != null) {
                                        onArrowLongPressed(hit)
                                    }
                                },
                                onTap = { offset ->
                                    if (boardState.guidanceState != null) {
                                        onClearGuidance()
                                        return@detectTapGestures
                                    }
                                    val hit = HitTester.findNearestArrow(
                                        touchScreen = offset,
                                        transform = transform,
                                        arrows = boardState.remainingArrows,
                                        slidingArrowIds = boardState.slidingAnims.keys,
                                        minTouchPx = minTouchPx
                                    )
                                    if (hit != null) {
                                        onArrowTapped(hit)
                                    }
                                }
                            )
                        }
                ) {
                    drawPuzzleBoard(
                        mask = lvl.mask,
                        remainingArrows = boardState.remainingArrows,
                        slidingAnims = boardState.slidingAnims,
                        errorState = boardState.errorState,
                        hintArrowId = boardState.hintArrowId,
                        guidanceState = boardState.guidanceState,
                        theme = theme,
                        transform = transform,
                        showGrid = settings.showGrid,
                        threeDMode = settings.threeDMode,
                        nowNanos = frameNanos,
                        shakeOffsetPx = shakeOffsetPx
                    )
                }

                // Floating hand icon over active Hint arrow OR first-launch tutorial arrow
                val pointerArrow = when {
                    boardState.hintArrowId != null -> boardState.remainingArrows.firstOrNull { it.id == boardState.hintArrowId }
                    boardState.showFirstTapPointer -> boardState.firstFreeArrow
                    else -> null
                }

                if (pointerArrow != null) {
                    val handPos = transform.gridToScreen(
                        pointerArrow.head.x + 0.5f,
                        pointerArrow.head.y + 0.5f
                    )
                    val bob = sin((frameNanos / 1_000_000_000.0) * 5.0).toFloat() * with(density) { 6.dp.toPx() }
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (handPos.x - 18.dp.toPx()).roundToInt(),
                                    (handPos.y + 6.dp.toPx() + bob).roundToInt()
                                )
                            }
                            .size(40.dp)
                            .shadow(6.dp, CircleShape)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.TouchApp,
                            contentDescription = "Tap this arrow",
                            tint = theme.accentGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Bottom: two clean white round buttons side-by-side (Grid & Bulb), clean bottom margin
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp, top = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Grid toggle button
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .shadow(6.dp, CircleShape)
                    .clip(CircleShape)
                    .background(if (settings.showGrid) theme.accentGold else Color.White)
                    .clickable(onClick = onToggleGrid)
                    .testTag("game_grid_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.GridOn,
                    contentDescription = "Toggle Grid",
                    tint = if (settings.showGrid) Color.White else Color(0xFF3E2B22),
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(28.dp))

            // Hint Bulb button with red count badge or AD badge
            Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = onHintClick)
                        .testTag("game_hint_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lightbulb,
                        contentDescription = "Hint",
                        tint = Color(0xFFE5A93B),
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Badge (top-end)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color(0xFFD32F2F))
                        .padding(horizontal = if (progress.hintsRemaining > 0) 6.dp else 5.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (progress.hintsRemaining > 0) {
                        Text(
                            text = "${progress.hintsRemaining}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.PlayCircle,
                                contentDescription = "Watch Ad",
                                tint = Color.White,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "AD",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}
