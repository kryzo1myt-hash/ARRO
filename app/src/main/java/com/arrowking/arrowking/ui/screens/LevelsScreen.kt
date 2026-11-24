package com.arrowking.arrowking.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.arrowking.arrowking.R
import com.arrowking.arrowking.data.PlayerProgress
import com.arrowking.arrowking.ui.theme.LocalGameTheme

@Composable
fun LevelsScreen(
    progress: PlayerProgress,
    onSelectLevel: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val theme = LocalGameTheme.current
    val gridState = rememberLazyGridState()
    var maxRenderedLevels by remember(progress.highestUnlockedLevel) {
        mutableIntStateOf(maxOf(120, progress.highestUnlockedLevel + 60))
    }

    val lastVisibleIndex by remember {
        derivedStateOf {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        }
    }

    // Endless scrolling expansion
    LaunchedEffect(lastVisibleIndex, maxRenderedLevels) {
        if (lastVisibleIndex >= maxRenderedLevels - 24) {
            maxRenderedLevels += 100
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.paperBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("levels_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Home",
                    tint = theme.primaryText
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Levels",
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.accentGold
                )
                Text(
                    text = stringResource(R.string.levels_header),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.primaryText.copy(alpha = 0.7f)
                )
            }
        }
        HorizontalDivider(color = theme.dividerColor, thickness = 1.dp)

        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = 84.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(count = maxRenderedLevels, key = { it + 1 }) { idx ->
                val levelNum = idx + 1
                val isUnlocked = levelNum <= progress.highestUnlockedLevel
                val isCurrent = levelNum == progress.currentLevel
                val isBoss = levelNum % 25 == 0
                val stars = progress.bestStarsByLevel[levelNum] ?: 0

                val containerColor = when {
                    isCurrent -> theme.accentGold
                    isUnlocked -> theme.paperSurface
                    else -> theme.boardWellBg.copy(alpha = 0.75f)
                }
                val contentColor = when {
                    isCurrent -> Color.White
                    isUnlocked -> theme.primaryText
                    else -> theme.primaryText.copy(alpha = 0.42f)
                }

                Surface(
                    modifier = Modifier
                        .aspectRatio(0.95f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = isUnlocked) { onSelectLevel(levelNum) }
                        .testTag("level_tile_$levelNum"),
                    color = containerColor,
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = if (isUnlocked) 2.dp else 0.dp
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = if (isCurrent || isBoss) 2.dp else 1.dp,
                                color = when {
                                    isCurrent -> theme.accentGold
                                    isBoss && isUnlocked -> Color(0xFFD9822B)
                                    else -> theme.dividerColor
                                },
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(8.dp)
                    ) {
                        if (isBoss) {
                            Icon(
                                imageVector = Icons.Filled.EmojiEvents,
                                contentDescription = "Boss Level",
                                tint = if (isCurrent) Color.White else Color(0xFFD9822B),
                                modifier = Modifier
                                    .size(15.dp)
                                    .align(Alignment.TopEnd)
                            )
                        }

                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$levelNum",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = contentColor
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (!isUnlocked) {
                                Icon(
                                    imageVector = Icons.Filled.Lock,
                                    contentDescription = "Locked",
                                    tint = contentColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    for (s in 1..3) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = when {
                                                s <= stars -> Color(0xFFFFB300)
                                                isCurrent -> Color.White.copy(alpha = 0.45f)
                                                else -> theme.dividerColor
                                            },
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
