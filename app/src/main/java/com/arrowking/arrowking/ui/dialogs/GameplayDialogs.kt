package com.arrowking.arrowking.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arrowking.arrowking.ads.AdConfig
import com.arrowking.arrowking.ui.LevelCompleteInfo
import com.arrowking.arrowking.ui.theme.LocalGameTheme
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun PauseDialog(
    levelNumber: Int,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onHome: () -> Unit
) {
    val theme = LocalGameTheme.current
    Dialog(onDismissRequest = onResume) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = theme.paperSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, theme.dividerColor, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Level $levelNumber Paused",
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.primaryText
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Your board progress is saved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.primaryText.copy(alpha = 0.65f)
                )
                Spacer(modifier = Modifier.height(22.dp))
                Button(
                    onClick = onResume,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("pause_resume_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentGold, contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESUME", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("pause_restart_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = theme.primaryText)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESTART LEVEL", style = MaterialTheme.typography.labelLarge, color = theme.primaryText)
                }
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(
                    onClick = onHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("pause_home_button")
                ) {
                    Icon(Icons.Filled.Home, contentDescription = null, tint = theme.primaryText.copy(alpha = 0.8f))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("HOME", style = MaterialTheme.typography.labelLarge, color = theme.primaryText.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
fun GameOverDialog(
    revivesUsed: Int,
    onWatchAdRevive: () -> Unit,
    onRestartLevel: () -> Unit
) {
    val theme = LocalGameTheme.current
    val canRevive = revivesUsed < AdConfig.MAX_REVIVES_PER_LEVEL

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = theme.paperSurface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, theme.dividerColor, RoundedCornerShape(26.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD32F2F).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.WaterDrop,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Out of Lives!",
                    style = MaterialTheme.typography.headlineMedium,
                    color = theme.primaryText
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (canRevive) {
                        "Watch a short video to refill all 3 lives and continue right where you left off."
                    } else {
                        "Maximum revives reached for this level. Give it a fresh run!"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.primaryText.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                if (canRevive) {
                    Button(
                        onClick = onWatchAdRevive,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("gameover_continue_ad_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E8B57),
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(Icons.Filled.PlayCircle, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("CONTINUE", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedButton(
                    onClick = onRestartLevel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("gameover_restart_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = theme.primaryText, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("RESTART LEVEL", style = MaterialTheme.typography.labelLarge, color = theme.primaryText)
                }
            }
        }
    }
}

@Composable
fun LevelCompleteCelebrationDialog(
    info: LevelCompleteInfo,
    reduceAnimations: Boolean,
    onNextLevel: () -> Unit,
    onReplay: () -> Unit,
    onHome: () -> Unit
) {
    val theme = LocalGameTheme.current
    val confettiAnim = remember { Animatable(0f) }
    var displayedIq by remember(info) { mutableIntStateOf(info.previousIq) }

    val particles = remember(info.levelNumber) {
        val rng = Random(info.levelNumber * 31L)
        val count = if (info.isBoss) 56 else 34
        List(count) {
            Triple(
                rng.nextFloat() * 2f * PI.toFloat(),
                0.4f + rng.nextFloat() * 0.9f,
                theme.arrowColors[it % theme.arrowColors.size]
            )
        }
    }

    LaunchedEffect(info, reduceAnimations) {
        if (!reduceAnimations) {
            confettiAnim.snapTo(0f)
            confettiAnim.animateTo(1f, tween(1300, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(info) {
        for (v in info.previousIq..info.newIq) {
            displayedIq = v
            delay(75L)
        }
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center
        ) {
            // Celebration particles
            if (!reduceAnimations) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width * 0.5f
                    val cy = size.height * 0.42f
                    val maxR = size.minDimension * 0.55f
                    val t = confettiAnim.value
                    val alpha = (1f - t * 0.85f).coerceIn(0f, 1f)
                    for ((angle, speed, color) in particles) {
                        val r = maxR * speed * t
                        val px = cx + cos(angle) * r
                        val py = cy + sin(angle) * r + (t * t * 90f)
                        drawCircle(color = color.copy(alpha = alpha), radius = 6.dp.toPx(), center = Offset(px, py))
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = theme.paperSurface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .border(2.dp, if (info.isBoss) theme.accentGold else theme.dividerColor, RoundedCornerShape(28.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (info.isBoss) {
                        Surface(
                            color = Color(0xFFD9822B),
                            shape = RoundedCornerShape(99.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("BOSS CLEARED!", style = MaterialTheme.typography.labelSmall, color = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Text(
                        text = "Level ${info.levelNumber} Complete!",
                        style = MaterialTheme.typography.headlineMedium,
                        color = theme.accentGold
                    )
                    Text(
                        text = "${info.shapeName} solved in ${info.elapsedSeconds}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.primaryText.copy(alpha = 0.68f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stars row
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (s in 1..3) {
                            val earned = s <= info.starsEarned
                            val starScale = remember { Animatable(if (reduceAnimations) 1f else 0.4f) }
                            LaunchedEffect(info, s) {
                                if (!reduceAnimations) {
                                    delay(s * 140L)
                                    starScale.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = Spring.StiffnessMedium))
                                }
                            }
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = null,
                                tint = if (earned) Color(0xFFFFB300) else theme.dividerColor,
                                modifier = Modifier
                                    .size(44.dp)
                                    .scale(starScale.value)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // IQ Boost card
                    Surface(
                        color = theme.boardWellBg,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Psychology, contentDescription = null, tint = theme.accentGold)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "IQ ${info.previousIq} → $displayedIq",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = theme.primaryText
                                )
                            }
                            Text(
                                text = "+${info.iqDelta} IQ",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color(0xFF2E8B57)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Button(
                        onClick = onNextLevel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("complete_next_button"),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accentGold, contentColor = Color.White)
                    ) {
                        Text("NEXT LEVEL (${info.levelNumber + 1})", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        TextButton(onClick = onReplay, modifier = Modifier.testTag("complete_replay_button")) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Replay")
                        }
                        TextButton(onClick = onHome, modifier = Modifier.testTag("complete_home_button")) {
                            Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Home")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WatchAdForHintDialog(
    onConfirmWatchAd: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalGameTheme.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = theme.paperSurface
        ) {
            Column(
                modifier = Modifier
                    .border(1.dp, theme.dividerColor, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Color(0xFFE5A93B), modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text("Need a Hint?", style = MaterialTheme.typography.titleLarge, color = theme.primaryText)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Watch a short video to get +1 Hint and highlight the smartest next move.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.primaryText.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onConfirmWatchAd,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("hint_watch_ad_confirm"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentGold, contentColor = Color.White)
                ) {
                    Icon(Icons.Filled.PlayCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("WATCH AD (+1 HINT)", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = onDismiss) {
                    Text("Not Now", color = theme.primaryText.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun DebugTestAdDialog(
    onFinished: (Boolean) -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(3) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        onFinished(true)
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF1F2430)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Test ad (Debug)", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Simulating rewarded ad... ${secondsLeft}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(14.dp))
                TextButton(onClick = { onFinished(false) }) {
                    Text("Skip (No Reward)", color = Color(0xFFFF8A80))
                }
            }
        }
    }
}

@Composable
fun AdsConsentDialog(
    onChoice: (personalized: Boolean) -> Unit
) {
    val theme = LocalGameTheme.current
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(shape = RoundedCornerShape(24.dp), color = theme.paperSurface) {
            Column(
                modifier = Modifier
                    .border(1.dp, theme.dividerColor, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Ad Privacy Preferences", style = MaterialTheme.typography.titleLarge, color = theme.primaryText)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "ARROW KING only shows optional rewarded ads when you choose to revive or unlock a hint. Choose whether Unity Ads may show personalised ads. You can change this anytime in Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.primaryText.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = { onChoice(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("consent_accept_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentGold, contentColor = Color.White)
                ) {
                    Text("Accept Personalised Ads", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onChoice(false) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("consent_decline_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Use Non-Personalised Ads", style = MaterialTheme.typography.labelLarge, color = theme.primaryText)
                }
            }
        }
    }
}
