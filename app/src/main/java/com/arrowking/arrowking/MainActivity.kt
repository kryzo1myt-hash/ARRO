package com.arrowking.arrowking

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arrowking.arrowking.data.ThemeCatalog
import com.arrowking.arrowking.ui.AppScreen
import com.arrowking.arrowking.ui.MainViewModel
import com.arrowking.arrowking.ui.dialogs.AdsConsentDialog
import com.arrowking.arrowking.ui.dialogs.DailyRewardDialog
import com.arrowking.arrowking.ui.dialogs.DebugTestAdDialog
import com.arrowking.arrowking.ui.dialogs.GameOverDialog
import com.arrowking.arrowking.ui.dialogs.LeaderboardDialog
import com.arrowking.arrowking.ui.dialogs.LevelCompleteCelebrationDialog
import com.arrowking.arrowking.ui.dialogs.PauseDialog
import com.arrowking.arrowking.ui.dialogs.SettingsDialog
import com.arrowking.arrowking.ui.dialogs.ThemesDialog
import com.arrowking.arrowking.ui.dialogs.WatchAdForHintDialog
import com.arrowking.arrowking.ui.screens.GameScreen
import com.arrowking.arrowking.ui.screens.HomeScreen
import com.arrowking.arrowking.ui.screens.LevelsScreen
import com.arrowking.arrowking.ui.screens.SplashScreen
import com.arrowking.arrowking.ui.theme.ArrowKingTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) {
            viewModel.startColdSplashSequence()
        }

        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val progress by viewModel.progress.collectAsStateWithLifecycle()
            val screen by viewModel.currentScreen.collectAsStateWithLifecycle()
            val splashProgress by viewModel.splashProgress.collectAsStateWithLifecycle()
            val boardState by viewModel.boardState.collectAsStateWithLifecycle()
            val hasSavedInProgress by viewModel.hasSavedInProgress.collectAsStateWithLifecycle()
            val canClaimDaily by viewModel.canClaimDailyReward.collectAsStateWithLifecycle()
            val rivals by viewModel.rivals.collectAsStateWithLifecycle()
            val showWatchAdHintConfirm by viewModel.showWatchAdHintConfirm.collectAsStateWithLifecycle()
            val showDebugTestAd by viewModel.adsManager.isShowingDebugTestAd.collectAsStateWithLifecycle()

            var showSettingsDialog by remember { mutableStateOf(false) }
            var showThemesDialog by remember { mutableStateOf(false) }
            var showDailyDialog by remember { mutableStateOf(false) }
            var showLeaderboardDialog by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                viewModel.toastEvents.collect { msg ->
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }

            val activePalette = remember(settings.selectedThemeId) {
                ThemeCatalog.byId(settings.selectedThemeId)
            }

            ArrowKingTheme(palette = activePalette) {
                Crossfade(
                    targetState = screen,
                    animationSpec = tween(durationMillis = if (settings.reduceAnimations) 120 else 320),
                    label = "screen_crossfade"
                ) { target ->
                    when (target) {
                        AppScreen.SPLASH -> {
                            SplashScreen(
                                progress = splashProgress,
                                reduceAnimations = settings.reduceAnimations
                            )
                        }
                        AppScreen.HOME -> {
                            HomeScreen(
                                progress = progress,
                                settings = settings,
                                hasSavedInProgress = hasSavedInProgress,
                                canClaimDailyReward = canClaimDaily,
                                onPlayClick = { viewModel.playOrContinueFromHome() },
                                onLevelsClick = { viewModel.navigateToLevels() },
                                onDailyRewardClick = { showDailyDialog = true },
                                onLeaderboardClick = { showLeaderboardDialog = true },
                                onThemesClick = { showThemesDialog = true },
                                onSettingsClick = { showSettingsDialog = true },
                                onToggleThreeD = { viewModel.toggleThreeDMode() }
                            )
                        }
                        AppScreen.LEVELS -> {
                            LevelsScreen(
                                progress = progress,
                                onSelectLevel = { lvl -> viewModel.openLevel(lvl, forceFresh = false) },
                                onBack = { viewModel.navigateToHome() }
                            )
                        }
                        AppScreen.GAME -> {
                            GameScreen(
                                boardState = boardState,
                                progress = progress,
                                settings = settings,
                                onBackPressed = { viewModel.pauseGame() },
                                onThemesClick = { showThemesDialog = true },
                                onSettingsClick = { showSettingsDialog = true },
                                onArrowTapped = { arrow -> viewModel.onArrowTapped(arrow) },
                                onArrowLongPressed = { arrow -> viewModel.onArrowLongPressed(arrow) },
                                onClearGuidance = { viewModel.clearGuidance() },
                                onToggleGrid = { viewModel.toggleGrid() },
                                onHintClick = { viewModel.onHintClicked() }
                            )
                        }
                    }
                }

                // Consent dialog after splash exits on first launch
                if (screen != AppScreen.SPLASH && !settings.adsConsentAsked) {
                    AdsConsentDialog(
                        onChoice = { personalized ->
                            viewModel.acceptOrDeclineAdsConsent(personalized)
                        }
                    )
                }

                // Gameplay dialogs
                if (screen == AppScreen.GAME) {
                    if (boardState.isPaused) {
                        PauseDialog(
                            levelNumber = boardState.level?.levelNumber ?: progress.currentLevel,
                            onResume = { viewModel.resumeGame() },
                            onRestart = { viewModel.restartCurrentLevel() },
                            onHome = { viewModel.navigateToHome() }
                        )
                    }
                    if (boardState.isGameOver) {
                        GameOverDialog(
                            revivesUsed = boardState.revivesUsed,
                            onWatchAdRevive = { viewModel.onReviveClicked(this@MainActivity) },
                            onRestartLevel = { viewModel.restartCurrentLevel() }
                        )
                    }
                    boardState.levelComplete?.let { info ->
                        LevelCompleteCelebrationDialog(
                            info = info,
                            reduceAnimations = settings.reduceAnimations,
                            onNextLevel = { viewModel.nextLevelFromComplete() },
                            onReplay = { viewModel.restartCurrentLevel() },
                            onHome = { viewModel.navigateToHome() }
                        )
                    }
                    if (showWatchAdHintConfirm) {
                        WatchAdForHintDialog(
                            onConfirmWatchAd = { viewModel.confirmWatchAdForHint(this@MainActivity) },
                            onDismiss = { viewModel.dismissWatchAdHintConfirm() }
                        )
                    }
                }

                // Menu dialogs
                if (showSettingsDialog) {
                    SettingsDialog(
                        settings = settings,
                        onUpdateSettings = { viewModel.updateSettings(it) },
                        onUpdateConsent = { viewModel.updateAdsPrivacyFromSettings(it) },
                        onResetProgress = { viewModel.resetAllProgress() },
                        onDismiss = { showSettingsDialog = false }
                    )
                }
                if (showThemesDialog) {
                    ThemesDialog(
                        selectedThemeId = settings.selectedThemeId,
                        onSelectTheme = { viewModel.setTheme(it) },
                        onDismiss = { showThemesDialog = false }
                    )
                }
                if (showDailyDialog) {
                    DailyRewardDialog(
                        progress = progress,
                        canClaim = canClaimDaily,
                        onClaim = { viewModel.claimDailyReward() },
                        onDismiss = { showDailyDialog = false }
                    )
                }
                if (showLeaderboardDialog) {
                    LeaderboardDialog(
                        rivals = rivals,
                        onDismiss = { showLeaderboardDialog = false }
                    )
                }
                if (showDebugTestAd) {
                    DebugTestAdDialog(
                        onFinished = { granted -> viewModel.adsManager.completeDebugTestAd(granted) }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegroundChanged(true)
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppForegroundChanged(false)
    }
}
