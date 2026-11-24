package com.arrowking.arrowking.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arrowking.arrowking.ads.AdConfig
import com.arrowking.arrowking.ads.AdsManager
import com.arrowking.arrowking.ads.UnityAdsManager
import com.arrowking.arrowking.audio.SoundEngine
import com.arrowking.arrowking.data.AppSettings
import com.arrowking.arrowking.data.GameRepository
import com.arrowking.arrowking.data.InProgressBoardState
import com.arrowking.arrowking.data.PlayerProgress
import com.arrowking.arrowking.data.RivalEntry
import com.arrowking.arrowking.game.generator.LevelGenerator
import com.arrowking.arrowking.game.model.Arrow
import com.arrowking.arrowking.game.model.GeneratedLevel
import com.arrowking.arrowking.game.solver.LevelSolver
import com.arrowking.arrowking.render.ErrorHighlightState
import com.arrowking.arrowking.render.GuidanceState
import com.arrowking.arrowking.render.SlidingArrowAnim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class AppScreen {
    SPLASH,
    HOME,
    GAME,
    LEVELS
}

data class LevelCompleteInfo(
    val levelNumber: Int,
    val isBoss: Boolean,
    val starsEarned: Int,
    val previousIq: Int,
    val newIq: Int,
    val iqDelta: Int,
    val speedBonus: Int,
    val elapsedSeconds: Int,
    val shapeName: String
)

data class BoardUiState(
    val isLoading: Boolean = true,
    val level: GeneratedLevel? = null,
    val remainingArrows: List<Arrow> = emptyList(),
    val slidingAnims: Map<Int, SlidingArrowAnim> = emptyMap(),
    val errorState: ErrorHighlightState? = null,
    val hintArrowId: Int? = null,
    val guidanceState: GuidanceState? = null,
    val livesLeft: Int = 3,
    val revivesUsed: Int = 0,
    val streak: Int = 0,
    val elapsedSeconds: Int = 0,
    val hintUsedThisLevel: Boolean = false,
    val lifeLostThisLevel: Boolean = false,
    val isPaused: Boolean = false,
    val isGameOver: Boolean = false,
    val levelComplete: LevelCompleteInfo? = null,
    val showFirstTapPointer: Boolean = false,
    val firstFreeArrow: Arrow? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository(application)
    val adsManager: AdsManager = UnityAdsManager()
    val soundEngine = SoundEngine()

    private val levelCache = ConcurrentHashMap<Int, GeneratedLevel>()
    private var timerJob: Job? = null
    private var pregenJob: Job? = null

    private val _currentScreen = MutableStateFlow(AppScreen.SPLASH)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _splashProgress = MutableStateFlow(0f)
    val splashProgress: StateFlow<Float> = _splashProgress.asStateFlow()

    private val _hasSavedInProgress = MutableStateFlow(false)
    val hasSavedInProgress: StateFlow<Boolean> = _hasSavedInProgress.asStateFlow()

    private val _boardState = MutableStateFlow(BoardUiState())
    val boardState: StateFlow<BoardUiState> = _boardState.asStateFlow()

    private val _showWatchAdHintConfirm = MutableStateFlow(false)
    val showWatchAdHintConfirm: StateFlow<Boolean> = _showWatchAdHintConfirm.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    val settings: StateFlow<AppSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val progress: StateFlow<PlayerProgress> = repository.progressFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerProgress())

    val rivals: StateFlow<List<RivalEntry>> = progress
        .combine(MutableStateFlow(Unit)) { p, _ -> repository.buildRivalsLeaderboard(p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val canClaimDailyReward: StateFlow<Boolean> = progress
        .combine(MutableStateFlow(Unit)) { p, _ -> repository.canClaimDailyReward(p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var splashStarted = false

    init {
        viewModelScope.launch {
            val s = repository.settingsFlow.first()
            soundEngine.setSoundEnabled(s.soundEnabled)
            soundEngine.setMusicEnabled(s.musicEnabled)
            soundEngine.initAsync()

            if (s.adsConsentAsked) {
                adsManager.initialize(getApplication(), s.personalizedAds)
            }

            val savedBoard = repository.loadInProgressBoard()
            _hasSavedInProgress.value = savedBoard != null && savedBoard.remainingArrowIds.isNotEmpty()
        }

        viewModelScope.launch {
            settings.collect { s ->
                soundEngine.setSoundEnabled(s.soundEnabled)
                soundEngine.setMusicEnabled(s.musicEnabled)
            }
        }
    }

    fun startColdSplashSequence() {
        if (splashStarted) return
        splashStarted = true

        viewModelScope.launch {
            val s = repository.settingsFlow.first()
            val prog = repository.progressFlow.first()
            val savedBoard = repository.loadInProgressBoard()

            // Preload level 1 (or current level) during splash
            val initialLevelNum = savedBoard?.levelNumber ?: prog.currentLevel
            val preGen = launch(Dispatchers.Default) {
                val lvl = getOrGenerateLevel(initialLevelNum)
                levelCache[initialLevelNum] = lvl
            }

            val totalMs = if (s.reduceAnimations) 900L else 2800L
            val stepMs = 40L
            var elapsed = 0L
            var whooshPlayed = false
            while (elapsed < totalMs) {
                delay(stepMs)
                elapsed += stepMs
                val frac = (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
                _splashProgress.value = frac
                if (!s.reduceAnimations && !whooshPlayed && elapsed >= 1300L) {
                    whooshPlayed = true
                    soundEngine.playSplashWhoosh()
                }
            }
            _splashProgress.value = 1f
            // Cap wait at +450ms if first level is still generating so splash never exceeds ~3.3s
            withContext(Dispatchers.Default) {
                kotlinx.coroutines.withTimeoutOrNull(450L) { preGen.join() }
            }

            // Section 7.2: very first launch goes straight to a Level 1 preview/board with hand pointer
            if (!prog.firstLaunchTutorialShown && prog.currentLevel == 1 && prog.highestUnlockedLevel == 1 && savedBoard == null) {
                openLevel(1, forceFresh = false, isFirstLaunchPreview = true)
            } else {
                _currentScreen.value = AppScreen.HOME
            }
        }
    }

    fun onAppForegroundChanged(foreground: Boolean) {
        soundEngine.onAppForegroundChanged(foreground)
    }

    fun acceptOrDeclineAdsConsent(personalized: Boolean) {
        viewModelScope.launch {
            repository.setAdsConsent(personalized)
            adsManager.initialize(getApplication(), personalized)
        }
    }

    fun updateAdsPrivacyFromSettings(personalized: Boolean) {
        viewModelScope.launch {
            repository.setAdsConsent(personalized)
            adsManager.updatePrivacyConsent(getApplication(), personalized)
            if (!adsManager.isInitialized.value) {
                adsManager.initialize(getApplication(), personalized)
            }
        }
    }

    fun navigateToHome() {
        stopTimer()
        saveBoardSnapshotIfActive()
        _currentScreen.value = AppScreen.HOME
    }

    fun navigateToLevels() {
        stopTimer()
        _currentScreen.value = AppScreen.LEVELS
    }

    fun playOrContinueFromHome() {
        val lvl = progress.value.currentLevel
        openLevel(lvl, forceFresh = false)
    }

    fun openLevel(
        levelNumber: Int,
        forceFresh: Boolean = false,
        isFirstLaunchPreview: Boolean = false
    ) {
        stopTimer()
        _currentScreen.value = AppScreen.GAME
        _boardState.value = BoardUiState(isLoading = true)

        viewModelScope.launch {
            repository.setCurrentLevel(levelNumber)
            val generated = withContext(Dispatchers.Default) {
                getOrGenerateLevel(levelNumber)
            }

            val saved = if (!forceFresh) repository.loadInProgressBoard() else null
            val useSaved = saved != null &&
                saved.levelNumber == levelNumber &&
                saved.generatorVersion == LevelGenerator.GENERATOR_VERSION &&
                saved.remainingArrowIds.isNotEmpty()

            val remaining = if (useSaved) {
                generated.arrows.filter { it.id in saved!!.remainingArrowIds }
            } else {
                generated.arrows
            }

            val showPointer = isFirstLaunchPreview || (!progress.value.firstLaunchTutorialShown && levelNumber == 1 && remaining.size == generated.arrows.size)
            val pointerArrow = if (showPointer) {
                LevelSolver.pickBestHintArrow(remaining, generated.mask.width, generated.mask.height)
            } else null

            _boardState.value = BoardUiState(
                isLoading = false,
                level = generated,
                remainingArrows = remaining,
                livesLeft = if (useSaved) saved!!.livesLeft.coerceIn(1, 3) else 3,
                revivesUsed = if (useSaved) saved!!.revivesUsed else 0,
                streak = if (useSaved) saved!!.streak else 0,
                elapsedSeconds = if (useSaved) saved!!.elapsedSeconds else 0,
                hintUsedThisLevel = if (useSaved) saved!!.hintUsedThisLevel else false,
                lifeLostThisLevel = if (useSaved) saved!!.lifeLostThisLevel else false,
                showFirstTapPointer = showPointer && pointerArrow != null,
                firstFreeArrow = pointerArrow
            )
            _hasSavedInProgress.value = useSaved
            startTimer()
            pregenerateNextLevel(levelNumber + 1)
        }
    }

    private suspend fun getOrGenerateLevel(levelNumber: Int): GeneratedLevel {
        levelCache[levelNumber]?.let { return it }
        val gen = LevelGenerator.generateLevel(levelNumber)
        levelCache[levelNumber] = gen
        return gen
    }

    private fun pregenerateNextLevel(nextLevel: Int) {
        pregenJob?.cancel()
        pregenJob = viewModelScope.launch(Dispatchers.Default) {
            if (!levelCache.containsKey(nextLevel)) {
                levelCache[nextLevel] = LevelGenerator.generateLevel(nextLevel)
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val cur = _boardState.value
                if (!cur.isLoading && !cur.isPaused && !cur.isGameOver && cur.levelComplete == null) {
                    _boardState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun onArrowTapped(arrow: Arrow) {
        val state = _boardState.value
        val level = state.level ?: return
        if (state.isPaused || state.isGameOver || state.levelComplete != null) return
        if (arrow.id in state.slidingAnims) return
        if (state.remainingArrows.none { it.id == arrow.id }) return

        // Clear tutorial pointer once player interacts
        if (state.showFirstTapPointer) {
            viewModelScope.launch { repository.markTutorialShown() }
        }

        // Build occupancy map excluding already sliding arrows
        val activeArrows = state.remainingArrows.filter { it.id !in state.slidingAnims }
        val occupied = HashMap<com.arrowking.arrowking.game.model.Cell, Int>(activeArrows.size * 6)
        for (a in activeArrows) {
            for (c in a.cells) occupied[c] = a.id
        }

        val isFree = LevelSolver.isArrowFree(arrow, occupied, level.mask.width, level.mask.height)
        if (isFree) {
            handleFreeArrowTap(arrow, level)
        } else {
            val blocker = LevelSolver.firstBlocker(arrow, occupied, level.mask.width, level.mask.height)
            handleBlockedArrowTap(arrow, blocker?.second, activeArrows)
        }
    }

    private fun handleFreeArrowTap(arrow: Arrow, level: GeneratedLevel) {
        soundEngine.playWhoosh()
        triggerHaptic(light = true)

        val exitRay = LevelSolver.rayCells(arrow, level.mask.width, level.mask.height)
        // Extend slightly past board edge so snake glides smoothly completely off the mask
        val extraTailCells = buildList {
            addAll(exitRay)
            var cur = if (exitRay.isNotEmpty()) exitRay.last() else arrow.head
            repeat(3) {
                cur = cur.step(arrow.headDirection)
                add(cur)
            }
        }

        val nowNanos = System.nanoTime()
        val anim = SlidingArrowAnim(arrow = arrow, startTimeNanos = nowNanos, exitPathCells = extraTailCells)

        val newRemaining = _boardState.value.remainingArrows.filter { it.id != arrow.id }
        val newStreak = _boardState.value.streak + 1

        _boardState.update {
            it.copy(
                remainingArrows = newRemaining,
                slidingAnims = it.slidingAnims + (arrow.id to anim),
                hintArrowId = if (it.hintArrowId == arrow.id) null else it.hintArrowId,
                guidanceState = null,
                errorState = null,
                streak = newStreak,
                showFirstTapPointer = false,
                firstFreeArrow = null
            )
        }

        // Schedule cleanup of finished slide anim
        val animDurationMs = if (settings.value.reduceAnimations) 160L else {
            ((extraTailCells.size + arrow.length) * 32L).coerceIn(240L, 650L)
        }
        viewModelScope.launch {
            delay(animDurationMs)
            _boardState.update { it.copy(slidingAnims = it.slidingAnims - arrow.id) }
        }

        if (newRemaining.isEmpty()) {
            onLevelCleared(level)
        } else {
            saveBoardSnapshotIfActive()
        }
    }

    private fun handleBlockedArrowTap(
        tappedArrow: Arrow,
        blockerId: Int?,
        activeArrows: List<Arrow>
    ) {
        soundEngine.playError()
        triggerHaptic(light = false)

        val blockerArrow = activeArrows.firstOrNull { it.id == blockerId } ?: tappedArrow
        val nowNanos = System.nanoTime()
        val cur = _boardState.value
        val newLives = (cur.livesLeft - 1).coerceAtLeast(0)

        _boardState.update {
            it.copy(
                livesLeft = newLives,
                lifeLostThisLevel = true,
                streak = 0,
                guidanceState = null,
                showFirstTapPointer = false,
                errorState = ErrorHighlightState(
                    tappedArrowId = tappedArrow.id,
                    blockerArrowId = blockerArrow.id,
                    blockerHeadCell = blockerArrow.head,
                    blockerDirection = blockerArrow.headDirection,
                    startTimeNanos = nowNanos
                )
            )
        }

        viewModelScope.launch {
            delay(900L)
            _boardState.update { state ->
                if (state.errorState?.startTimeNanos == nowNanos) state.copy(errorState = null) else state
            }
        }

        if (newLives <= 0) {
            stopTimer()
            _boardState.update { it.copy(isGameOver = true) }
        }
        saveBoardSnapshotIfActive()
    }

    fun onArrowLongPressed(arrow: Arrow) {
        val state = _boardState.value
        val level = state.level ?: return
        if (state.isPaused || state.isGameOver || state.levelComplete != null) return

        val activeArrows = state.remainingArrows.filter { it.id !in state.slidingAnims }
        val occupied = HashMap<com.arrowking.arrowking.game.model.Cell, Int>(activeArrows.size * 6)
        for (a in activeArrows) {
            for (c in a.cells) occupied[c] = a.id
        }
        val blocker = LevelSolver.firstBlocker(arrow, occupied, level.mask.width, level.mask.height)
        soundEngine.playTap()
        triggerHaptic(light = true)

        _boardState.update {
            it.copy(
                guidanceState = GuidanceState(
                    arrow = arrow,
                    blockerCell = blocker?.first,
                    blockerArrowId = blocker?.second
                )
            )
        }
    }

    fun clearGuidance() {
        if (_boardState.value.guidanceState != null) {
            _boardState.update { it.copy(guidanceState = null) }
        }
    }

    private fun onLevelCleared(level: GeneratedLevel) {
        stopTimer()
        val state = _boardState.value
        val stars = when {
            !state.lifeLostThisLevel && !state.hintUsedThisLevel -> 3
            (3 - state.livesLeft == 1 && !state.hintUsedThisLevel) ||
                (!state.lifeLostThisLevel && state.hintUsedThisLevel) -> 2
            else -> 1
        }

        val est = level.metrics.estimatedSolveSeconds.coerceAtLeast(30)
        val speedBonus = when {
            state.elapsedSeconds <= (est * 0.75f).toInt() -> 2
            state.elapsedSeconds <= est -> 1
            else -> 0
        }
        val iqDelta = 1 + state.livesLeft + (if (!state.hintUsedThisLevel) 1 else 0) + speedBonus
        val prevIq = progress.value.iqScore
        val newIq = prevIq + iqDelta

        soundEngine.playWin(level.isBoss)
        triggerHaptic(light = false)

        viewModelScope.launch {
            repository.saveLevelWin(
                levelNumber = level.levelNumber,
                starsEarned = stars,
                iqDelta = iqDelta
            )
            _hasSavedInProgress.value = false
            _boardState.update {
                it.copy(
                    levelComplete = LevelCompleteInfo(
                        levelNumber = level.levelNumber,
                        isBoss = level.isBoss,
                        starsEarned = stars,
                        previousIq = prevIq,
                        newIq = newIq,
                        iqDelta = iqDelta,
                        speedBonus = speedBonus,
                        elapsedSeconds = state.elapsedSeconds,
                        shapeName = level.mask.shapeName
                    )
                )
            }
        }
    }

    fun onHintClicked() {
        val state = _boardState.value
        val level = state.level ?: return
        if (state.isPaused || state.isGameOver || state.levelComplete != null) return

        val hints = progress.value.hintsRemaining
        if (hints > 0) {
            showHintFromInventory(level, consumeHint = true)
        } else {
            _showWatchAdHintConfirm.value = true
        }
    }

    fun dismissWatchAdHintConfirm() {
        _showWatchAdHintConfirm.value = false
    }

    fun confirmWatchAdForHint(activity: Activity) {
        _showWatchAdHintConfirm.value = false
        val level = _boardState.value.level ?: return
        adsManager.showRewarded(
            activity = activity,
            onAdAudioPause = { soundEngine.pauseForAd() },
            onAdAudioResume = { soundEngine.resumeFromAd() },
            onRewarded = {
                viewModelScope.launch {
                    repository.updateHints(AdConfig.HINTS_PER_AD)
                    showHintFromInventory(level, consumeHint = true)
                }
            },
            onFailedOrSkipped = { msg ->
                _toastEvents.tryEmit(msg)
            }
        )
    }

    private fun showHintFromInventory(level: GeneratedLevel, consumeHint: Boolean) {
        val active = _boardState.value.remainingArrows.filter { it.id !in _boardState.value.slidingAnims }
        val chosen = LevelSolver.pickBestHintArrow(active, level.mask.width, level.mask.height)
        if (chosen == null) {
            _toastEvents.tryEmit("No hint available right now")
            return
        }
        soundEngine.playHint()
        triggerHaptic(light = true)
        _boardState.update {
            it.copy(
                hintArrowId = chosen.id,
                hintUsedThisLevel = true,
                showFirstTapPointer = false
            )
        }
        if (consumeHint) {
            viewModelScope.launch {
                repository.updateHints(-1)
                saveBoardSnapshotIfActive()
            }
        }
    }

    fun onReviveClicked(activity: Activity) {
        val state = _boardState.value
        if (state.revivesUsed >= AdConfig.MAX_REVIVES_PER_LEVEL) return

        if (!AdConfig.REVIVE_REQUIRES_AD) {
            applyRevive()
            return
        }

        adsManager.showRewarded(
            activity = activity,
            onAdAudioPause = { soundEngine.pauseForAd() },
            onAdAudioResume = { soundEngine.resumeFromAd() },
            onRewarded = {
                applyRevive()
            },
            onFailedOrSkipped = { msg ->
                _toastEvents.tryEmit(msg)
            }
        )
    }

    private fun applyRevive() {
        soundEngine.playHint()
        _boardState.update {
            it.copy(
                livesLeft = 3,
                revivesUsed = it.revivesUsed + 1,
                isGameOver = false,
                errorState = null
            )
        }
        startTimer()
        saveBoardSnapshotIfActive()
    }

    fun restartCurrentLevel() {
        val lvl = _boardState.value.level?.levelNumber ?: progress.value.currentLevel
        viewModelScope.launch {
            repository.clearInProgressBoard()
            _hasSavedInProgress.value = false
            openLevel(lvl, forceFresh = true)
        }
    }

    fun nextLevelFromComplete() {
        val next = (_boardState.value.level?.levelNumber ?: progress.value.currentLevel) + 1
        openLevel(next, forceFresh = true)
    }

    fun pauseGame() {
        _boardState.update { it.copy(isPaused = true) }
        saveBoardSnapshotIfActive()
    }

    fun resumeGame() {
        _boardState.update { it.copy(isPaused = false) }
    }

    fun toggleGrid() {
        soundEngine.playTap()
        viewModelScope.launch {
            repository.updateSettings { it.copy(showGrid = !it.showGrid) }
        }
    }

    fun toggleThreeDMode() {
        soundEngine.playTap()
        viewModelScope.launch {
            repository.updateSettings { it.copy(threeDMode = !it.threeDMode) }
        }
    }

    fun setTheme(themeId: String) {
        soundEngine.playTap()
        viewModelScope.launch {
            repository.updateSettings { it.copy(selectedThemeId = themeId) }
        }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            repository.updateSettings(transform)
        }
    }

    fun claimDailyReward() {
        viewModelScope.launch {
            if (!repository.canClaimDailyReward(progress.value)) return@launch
            val (hints, iq) = repository.claimDailyReward()
            soundEngine.playWin(false)
            _toastEvents.tryEmit("Daily Reward: +$hints Hint${if (hints > 1) "s" else ""}, +$iq IQ!")
        }
    }

    fun resetAllProgress() {
        viewModelScope.launch {
            repository.resetAllProgress()
            levelCache.clear()
            _hasSavedInProgress.value = false
            _currentScreen.value = AppScreen.HOME
            _toastEvents.tryEmit("Progress reset")
        }
    }

    private fun saveBoardSnapshotIfActive() {
        val state = _boardState.value
        val level = state.level ?: return
        if (state.levelComplete != null || state.remainingArrows.isEmpty()) return
        val snapshot = InProgressBoardState(
            levelNumber = level.levelNumber,
            generatorVersion = LevelGenerator.GENERATOR_VERSION,
            remainingArrowIds = state.remainingArrows.map { it.id }.toSet(),
            livesLeft = state.livesLeft.coerceAtLeast(1),
            revivesUsed = state.revivesUsed,
            elapsedSeconds = state.elapsedSeconds,
            streak = state.streak,
            hintUsedThisLevel = state.hintUsedThisLevel,
            lifeLostThisLevel = state.lifeLostThisLevel
        )
        viewModelScope.launch {
            repository.saveInProgressBoard(snapshot)
            _hasSavedInProgress.value = true
        }
    }

    private fun triggerHaptic(light: Boolean) {
        if (!settings.value.hapticsEnabled) return
        try {
            val ctx = getApplication<Application>()
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val mgr = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                mgr?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val dur = if (light) 18L else 55L
                val amp = if (light) 70 else 190
                vibrator.vibrate(VibrationEffect.createOneShot(dur, amp))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(if (light) 18L else 55L)
            }
        } catch (_: Throwable) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
        soundEngine.onAppForegroundChanged(false)
    }
}
