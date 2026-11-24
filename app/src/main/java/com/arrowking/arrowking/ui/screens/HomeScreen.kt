package com.arrowking.arrowking.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arrowking.arrowking.R
import com.arrowking.arrowking.data.AppSettings
import com.arrowking.arrowking.data.PlayerProgress
import com.arrowking.arrowking.ui.components.ArrowKingLogoMark
import com.arrowking.arrowking.ui.theme.LocalGameTheme

@Composable
fun HomeScreen(
    progress: PlayerProgress,
    settings: AppSettings,
    hasSavedInProgress: Boolean,
    canClaimDailyReward: Boolean,
    onPlayClick: () -> Unit,
    onLevelsClick: () -> Unit,
    onDailyRewardClick: () -> Unit,
    onLeaderboardClick: () -> Unit,
    onThemesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleThreeD: () -> Unit
) {
    val theme = LocalGameTheme.current
    val infinite = rememberInfiniteTransition(label = "home_idle")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )
    val pulseScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (settings.reduceAnimations) 1f else 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.paperBg)
    ) {
        // Gentle idle sliding snake arrows in the background
        if (!settings.reduceAnimations) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val lanes = listOf(0.16f, 0.34f, 0.68f, 0.85f)
                lanes.forEachIndexed { idx, yNorm ->
                    val color = theme.arrowColors[idx % theme.arrowColors.size].copy(alpha = 0.14f)
                    val offsetFrac = ((drift + idx * 0.25f) % 1f)
                    val startX = if (idx % 2 == 0) -w * 0.25f + offsetFrac * w * 1.5f else w * 1.25f - offsetFrac * w * 1.5f
                    val dirX = if (idx % 2 == 0) 1f else -1f
                    val y = h * yNorm
                    val seg = w * 0.18f
                    val bend = 24.dp.toPx() * (if (idx % 2 == 0) 1f else -1f)
                    val p = Path().apply {
                        moveTo(startX - dirX * seg, y + bend)
                        lineTo(startX - dirX * seg * 0.45f, y + bend)
                        lineTo(startX - dirX * seg * 0.45f, y)
                        lineTo(startX, y)
                    }
                    drawPath(p, color = color, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(startX, y))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Top stat chips (IQ, Stars, Hints)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatChip(
                    icon = Icons.Filled.Psychology,
                    label = "IQ ${progress.iqScore}",
                    tint = theme.accentGold,
                    surface = theme.paperSurface,
                    textColor = theme.primaryText,
                    borderColor = theme.dividerColor
                )
                StatChip(
                    icon = Icons.Filled.Star,
                    label = "${progress.bestStarsByLevel.values.sum()} ★",
                    tint = Color(0xFFE5A93B),
                    surface = theme.paperSurface,
                    textColor = theme.primaryText,
                    borderColor = theme.dividerColor
                )
                StatChip(
                    icon = Icons.Filled.Lightbulb,
                    label = "${progress.hintsRemaining}",
                    tint = Color(0xFF4DA3E8),
                    surface = theme.paperSurface,
                    textColor = theme.primaryText,
                    borderColor = theme.dividerColor
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            ArrowKingLogoMark(size = 104.dp)

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge.copy(
                    letterSpacing = 2.5.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = theme.primaryText
            )
            Text(
                text = "Tap the free arrow. Untangle the crown.",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.primaryText.copy(alpha = 0.68f)
            )

            Spacer(modifier = Modifier.height(28.dp))

            val mainLabel = if (hasSavedInProgress) {
                "CONTINUE LEVEL ${progress.currentLevel}"
            } else {
                "PLAY LEVEL ${progress.currentLevel}"
            }

            Button(
                onClick = onPlayClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .height(64.dp)
                    .scale(pulseScale)
                    .testTag("home_play_button"),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.accentGold,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = mainLabel,
                    style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 1.sp)
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // 3D Mode tactile switch card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onToggleThreeD() }
                    .testTag("home_3d_toggle"),
                color = theme.paperSurface,
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .border(1.dp, theme.dividerColor, RoundedCornerShape(18.dp))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.ViewInAr,
                            contentDescription = "3D Mode",
                            tint = theme.accentGold,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "3D Depth Mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = theme.primaryText
                            )
                            Text(
                                text = "Raised bevel & shadow snake arrows",
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.primaryText.copy(alpha = 0.62f)
                            )
                        }
                    }
                    Switch(
                        checked = settings.threeDMode,
                        onCheckedChange = { onToggleThreeD() },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = theme.accentGold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Feature menu grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeActionTile(
                        title = "Levels",
                        subtitle = "1000+ endless",
                        icon = Icons.Filled.GridView,
                        onClick = onLevelsClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_levels_button")
                    )
                    HomeActionTile(
                        title = "Daily Reward",
                        subtitle = if (canClaimDailyReward) "Claim now!" else "Day ${progress.dailyStreak}/7",
                        icon = Icons.Filled.CardGiftcard,
                        showBadgeDot = canClaimDailyReward,
                        onClick = onDailyRewardClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_daily_button")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HomeActionTile(
                        title = "Leaderboard",
                        subtitle = "Rivals & IQ",
                        icon = Icons.Filled.Leaderboard,
                        onClick = onLeaderboardClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_leaderboard_button")
                    )
                    HomeActionTile(
                        title = "Themes",
                        subtitle = "5 paper styles",
                        icon = Icons.Filled.Palette,
                        onClick = onThemesClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_themes_button")
                    )
                }
                HomeActionTile(
                    title = "Settings & Privacy",
                    subtitle = "Audio, haptics, animations, ad choices",
                    icon = Icons.Filled.Settings,
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_settings_button")
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun StatChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    surface: Color,
    textColor: Color,
    borderColor: Color
) {
    Surface(
        color = surface,
        shape = RoundedCornerShape(99.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, borderColor, RoundedCornerShape(99.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = textColor
            )
        }
    }
}

@Composable
private fun HomeActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBadgeDot: Boolean = false
) {
    val theme = LocalGameTheme.current
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        color = theme.paperSurface,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .border(1.dp, theme.dividerColor, RoundedCornerShape(18.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.accentGold.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = theme.accentGold,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.primaryText
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.primaryText.copy(alpha = 0.65f)
                    )
                }
            }
            if (showBadgeDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                )
            }
        }
    }
}
