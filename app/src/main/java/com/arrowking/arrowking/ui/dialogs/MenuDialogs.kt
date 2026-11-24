package com.arrowking.arrowking.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.arrowking.arrowking.R
import com.arrowking.arrowking.data.AppSettings
import com.arrowking.arrowking.data.PlayerProgress
import com.arrowking.arrowking.data.RivalEntry
import com.arrowking.arrowking.data.ThemeCatalog
import com.arrowking.arrowking.ui.theme.LocalGameTheme

@Composable
fun SettingsDialog(
    settings: AppSettings,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onUpdateConsent: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalGameTheme.current
    var showResetConfirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = theme.paperSurface) {
            Column(
                modifier = Modifier
                    .border(1.dp, theme.dividerColor, RoundedCornerShape(24.dp))
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Settings", style = MaterialTheme.typography.headlineMedium, color = theme.primaryText)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = theme.primaryText)
                    }
                }
                HorizontalDivider(color = theme.dividerColor)
                Spacer(modifier = Modifier.height(10.dp))

                SettingSwitchRow("Sound Effects", settings.soundEnabled) {
                    onUpdateSettings { s -> s.copy(soundEnabled = !s.soundEnabled) }
                }
                SettingSwitchRow("Background Music", settings.musicEnabled) {
                    onUpdateSettings { s -> s.copy(musicEnabled = !s.musicEnabled) }
                }
                SettingSwitchRow("Haptic Feedback", settings.hapticsEnabled) {
                    onUpdateSettings { s -> s.copy(hapticsEnabled = !s.hapticsEnabled) }
                }
                SettingSwitchRow("3D Arrow Depth", settings.threeDMode) {
                    onUpdateSettings { s -> s.copy(threeDMode = !s.threeDMode) }
                }
                SettingSwitchRow("Reduce Animations", settings.reduceAnimations) {
                    onUpdateSettings { s -> s.copy(reduceAnimations = !s.reduceAnimations) }
                }
                SettingSwitchRow("Personalised Ads", settings.personalizedAds) {
                    onUpdateConsent(!settings.personalizedAds)
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = theme.dividerColor)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Developer: ${stringResource(R.string.developer_name)} • ${stringResource(R.string.contact_email)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.primaryText.copy(alpha = 0.65f)
                )
                Spacer(modifier = Modifier.height(14.dp))

                TextButton(
                    onClick = { showResetConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_reset_progress_button")
                ) {
                    Text("Reset All Progress", color = Color(0xFFD32F2F), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Reset Progress?") },
            text = { Text("This clears completed levels, stars, IQ score, and saved board progress back to Level 1.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onDismiss()
                        onResetProgress()
                    },
                    modifier = Modifier.testTag("confirm_reset_button")
                ) {
                    Text("Reset", color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingSwitchRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val theme = LocalGameTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = theme.primaryText)
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = theme.accentGold)
        )
    }
}

@Composable
fun ThemesDialog(
    selectedThemeId: String,
    onSelectTheme: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalGameTheme.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = theme.paperSurface) {
            Column(
                modifier = Modifier
                    .border(1.dp, theme.dividerColor, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Themes & Colours", style = MaterialTheme.typography.headlineMedium, color = theme.primaryText)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = theme.primaryText)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (item in ThemeCatalog.allThemes) {
                        val selected = item.id == selectedThemeId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(item.paperBg)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) theme.accentGold else theme.dividerColor,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable { onSelectTheme(item.id) }
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .testTag("theme_option_${item.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = item.primaryText
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (c in item.arrowColors) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(c)
                                        )
                                    }
                                }
                            }
                            if (selected) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(theme.accentGold),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DailyRewardDialog(
    progress: PlayerProgress,
    canClaim: Boolean,
    onClaim: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalGameTheme.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = theme.paperSurface) {
            Column(
                modifier = Modifier
                    .border(1.dp, theme.dividerColor, RoundedCornerShape(24.dp))
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = theme.accentGold, modifier = Modifier.size(42.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Daily Crown Reward", style = MaterialTheme.typography.headlineMedium, color = theme.primaryText)
                Text(
                    text = "Current Streak: Day ${progress.dailyStreak} / 7",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.primaryText.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (day in 1..7) {
                        val claimed = day <= progress.dailyStreak
                        val isNext = canClaim && day == (progress.dailyStreak % 7) + 1
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        claimed -> theme.accentGold.copy(alpha = 0.2f)
                                        isNext -> theme.accentGold
                                        else -> theme.boardWellBg
                                    }
                                )
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "D$day",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isNext) Color.White else theme.primaryText
                            )
                            Text(
                                text = if (day == 7) "+3" else if (day >= 3) "+2" else "+1",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isNext) Color.White else theme.accentGold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onClaim,
                    enabled = canClaim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("claim_daily_reward_button"),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accentGold, contentColor = Color.White)
                ) {
                    Text(if (canClaim) "CLAIM TODAY'S REWARD" else "CLAIMED TODAY", style = MaterialTheme.typography.labelLarge)
                }
                TextButton(onClick = onDismiss) {
                    Text("Close", color = theme.primaryText.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
fun LeaderboardDialog(
    rivals: List<RivalEntry>,
    onDismiss: () -> Unit
) {
    val theme = LocalGameTheme.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = theme.paperSurface) {
            Column(
                modifier = Modifier
                    .border(1.dp, theme.dividerColor, RoundedCornerShape(24.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = theme.accentGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rivals Leaderboard", style = MaterialTheme.typography.titleLarge, color = theme.primaryText)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = theme.primaryText)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rivals, key = { it.name }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (entry.isPlayer) theme.accentGold.copy(alpha = 0.16f) else theme.boardWellBg
                                )
                                .border(
                                    width = if (entry.isPlayer) 1.5.dp else 0.dp,
                                    color = if (entry.isPlayer) theme.accentGold else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "#${entry.rank}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = theme.accentGold,
                                    modifier = Modifier.width(32.dp)
                                )
                                Column {
                                    Text(
                                        text = entry.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = theme.primaryText
                                    )
                                    Text(
                                        text = "${entry.levelsCleared} levels cleared",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = theme.primaryText.copy(alpha = 0.65f)
                                    )
                                }
                            }
                            Text(
                                text = "IQ ${entry.iq}",
                                style = MaterialTheme.typography.labelLarge,
                                color = theme.primaryText
                            )
                        }
                    }
                }
            }
        }
    }
}
