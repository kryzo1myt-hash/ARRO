package com.arrowking.arrowking.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arrowking.arrowking.ads.AdConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

private val Context.arrowKingDataStore by preferencesDataStore(name = "arrow_king_prefs")

class GameRepository(private val context: Context) {

    private object Keys {
        val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
        val MUSIC_ENABLED = booleanPreferencesKey("music_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val REDUCE_ANIMATIONS = booleanPreferencesKey("reduce_animations")
        val THREE_D_MODE = booleanPreferencesKey("three_d_mode")
        val SHOW_GRID = booleanPreferencesKey("show_grid")
        val THEME_ID = stringPreferencesKey("theme_id")
        val ADS_CONSENT_ASKED = booleanPreferencesKey("ads_consent_asked")
        val PERSONALIZED_ADS = booleanPreferencesKey("personalized_ads")

        val CURRENT_LEVEL = intPreferencesKey("current_level")
        val HIGHEST_UNLOCKED = intPreferencesKey("highest_unlocked_level")
        val IQ_SCORE = intPreferencesKey("iq_score")
        val HINTS_REMAINING = intPreferencesKey("hints_remaining")
        val BEST_STARS_JSON = stringPreferencesKey("best_stars_json")
        val LAST_DAILY_EPOCH_DAY = longPreferencesKey("last_daily_epoch_day")
        val DAILY_STREAK = intPreferencesKey("daily_streak")
        val FIRST_TUTORIAL_SHOWN = booleanPreferencesKey("first_tutorial_shown")
    }

    private val inProgressFile: File
        get() = File(context.filesDir, "in_progress_level.json")

    val settingsFlow: Flow<AppSettings> = context.arrowKingDataStore.data.map { prefs ->
        AppSettings(
            soundEnabled = prefs[Keys.SOUND_ENABLED] ?: true,
            musicEnabled = prefs[Keys.MUSIC_ENABLED] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            reduceAnimations = prefs[Keys.REDUCE_ANIMATIONS] ?: false,
            threeDMode = prefs[Keys.THREE_D_MODE] ?: false,
            showGrid = prefs[Keys.SHOW_GRID] ?: false,
            selectedThemeId = prefs[Keys.THEME_ID] ?: ThemeCatalog.classicParchment.id,
            adsConsentAsked = prefs[Keys.ADS_CONSENT_ASKED] ?: false,
            personalizedAds = prefs[Keys.PERSONALIZED_ADS] ?: true
        )
    }

    val progressFlow: Flow<PlayerProgress> = context.arrowKingDataStore.data.map { prefs ->
        PlayerProgress(
            currentLevel = prefs[Keys.CURRENT_LEVEL] ?: 1,
            highestUnlockedLevel = prefs[Keys.HIGHEST_UNLOCKED] ?: 1,
            iqScore = prefs[Keys.IQ_SCORE] ?: 100,
            hintsRemaining = (prefs[Keys.HINTS_REMAINING] ?: AdConfig.FREE_HINTS).coerceIn(0, 99),
            bestStarsByLevel = parseStarsJson(prefs[Keys.BEST_STARS_JSON]),
            lastDailyClaimEpochDay = prefs[Keys.LAST_DAILY_EPOCH_DAY] ?: -1L,
            dailyStreak = prefs[Keys.DAILY_STREAK] ?: 0,
            firstLaunchTutorialShown = prefs[Keys.FIRST_TUTORIAL_SHOWN] ?: false
        )
    }

    suspend fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val current = settingsFlow.first()
        val updated = transform(current)
        context.arrowKingDataStore.edit { prefs ->
            prefs[Keys.SOUND_ENABLED] = updated.soundEnabled
            prefs[Keys.MUSIC_ENABLED] = updated.musicEnabled
            prefs[Keys.HAPTICS_ENABLED] = updated.hapticsEnabled
            prefs[Keys.REDUCE_ANIMATIONS] = updated.reduceAnimations
            prefs[Keys.THREE_D_MODE] = updated.threeDMode
            prefs[Keys.SHOW_GRID] = updated.showGrid
            prefs[Keys.THEME_ID] = updated.selectedThemeId
            prefs[Keys.ADS_CONSENT_ASKED] = updated.adsConsentAsked
            prefs[Keys.PERSONALIZED_ADS] = updated.personalizedAds
        }
    }

    suspend fun setAdsConsent(personalized: Boolean) {
        context.arrowKingDataStore.edit { prefs ->
            prefs[Keys.ADS_CONSENT_ASKED] = true
            prefs[Keys.PERSONALIZED_ADS] = personalized
        }
    }

    suspend fun markTutorialShown() {
        context.arrowKingDataStore.edit { prefs ->
            prefs[Keys.FIRST_TUTORIAL_SHOWN] = true
        }
    }

    suspend fun updateHints(delta: Int) {
        context.arrowKingDataStore.edit { prefs ->
            val cur = prefs[Keys.HINTS_REMAINING] ?: AdConfig.FREE_HINTS
            prefs[Keys.HINTS_REMAINING] = (cur + delta).coerceIn(0, 99)
        }
    }

    suspend fun setCurrentLevel(level: Int) {
        context.arrowKingDataStore.edit { prefs ->
            prefs[Keys.CURRENT_LEVEL] = level.coerceAtLeast(1)
        }
    }

    suspend fun saveLevelWin(
        levelNumber: Int,
        starsEarned: Int,
        iqDelta: Int
    ) {
        context.arrowKingDataStore.edit { prefs ->
            val curStars = parseStarsJson(prefs[Keys.BEST_STARS_JSON]).toMutableMap()
            val prev = curStars[levelNumber] ?: 0
            if (starsEarned > prev) {
                curStars[levelNumber] = starsEarned
            }
            prefs[Keys.BEST_STARS_JSON] = encodeStarsJson(curStars)

            val prevHighest = prefs[Keys.HIGHEST_UNLOCKED] ?: 1
            val nextLevel = levelNumber + 1
            if (nextLevel > prevHighest) {
                prefs[Keys.HIGHEST_UNLOCKED] = nextLevel
            }
            prefs[Keys.CURRENT_LEVEL] = nextLevel
            val prevIq = prefs[Keys.IQ_SCORE] ?: 100
            prefs[Keys.IQ_SCORE] = prevIq + iqDelta
        }
        clearInProgressBoard()
    }

    fun currentEpochDay(): Long =
        TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())

    fun canClaimDailyReward(progress: PlayerProgress): Boolean {
        val today = currentEpochDay()
        return progress.lastDailyClaimEpochDay < today
    }

    suspend fun claimDailyReward(): Pair<Int, Int> {
        val today = currentEpochDay()
        var hintsAwarded = 1
        var iqAwarded = 2
        context.arrowKingDataStore.edit { prefs ->
            val lastDay = prefs[Keys.LAST_DAILY_EPOCH_DAY] ?: -1L
            val prevStreak = prefs[Keys.DAILY_STREAK] ?: 0
            val newStreak = if (lastDay == today - 1L) (prevStreak % 7) + 1 else 1
            hintsAwarded = when (newStreak) {
                1, 2 -> 1
                3, 4 -> 2
                5, 6 -> 2
                else -> 3
            }
            iqAwarded = if (newStreak == 7) 4 else 2

            val curHints = prefs[Keys.HINTS_REMAINING] ?: AdConfig.FREE_HINTS
            prefs[Keys.HINTS_REMAINING] = (curHints + hintsAwarded).coerceIn(0, 99)
            val curIq = prefs[Keys.IQ_SCORE] ?: 100
            prefs[Keys.IQ_SCORE] = curIq + iqAwarded
            prefs[Keys.LAST_DAILY_EPOCH_DAY] = today
            prefs[Keys.DAILY_STREAK] = newStreak
        }
        return hintsAwarded to iqAwarded
    }

    suspend fun saveInProgressBoard(state: InProgressBoardState) = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("levelNumber", state.levelNumber)
            put("generatorVersion", state.generatorVersion)
            put("livesLeft", state.livesLeft)
            put("revivesUsed", state.revivesUsed)
            put("elapsedSeconds", state.elapsedSeconds)
            put("streak", state.streak)
            put("hintUsedThisLevel", state.hintUsedThisLevel)
            put("lifeLostThisLevel", state.lifeLostThisLevel)
            val arr = JSONArray()
            state.remainingArrowIds.forEach { arr.put(it) }
            put("remainingArrowIds", arr)
        }
        val tmp = File(context.filesDir, "in_progress_level.json.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(inProgressFile)) {
            inProgressFile.writeText(json.toString())
            tmp.delete()
        }
    }

    suspend fun loadInProgressBoard(): InProgressBoardState? = withContext(Dispatchers.IO) {
        if (!inProgressFile.exists()) return@withContext null
        try {
            val json = JSONObject(inProgressFile.readText())
            val arr = json.getJSONArray("remainingArrowIds")
            val ids = HashSet<Int>(arr.length())
            for (i in 0 until arr.length()) {
                ids.add(arr.getInt(i))
            }
            InProgressBoardState(
                levelNumber = json.getInt("levelNumber"),
                generatorVersion = json.getInt("generatorVersion"),
                remainingArrowIds = ids,
                livesLeft = json.getInt("livesLeft"),
                revivesUsed = json.getInt("revivesUsed"),
                elapsedSeconds = json.getInt("elapsedSeconds"),
                streak = json.getInt("streak"),
                hintUsedThisLevel = json.getBoolean("hintUsedThisLevel"),
                lifeLostThisLevel = json.getBoolean("lifeLostThisLevel")
            )
        } catch (_: Throwable) {
            null
        }
    }

    suspend fun clearInProgressBoard() = withContext(Dispatchers.IO) {
        if (inProgressFile.exists()) {
            inProgressFile.delete()
        }
    }

    suspend fun resetAllProgress() {
        context.arrowKingDataStore.edit { prefs ->
            prefs[Keys.CURRENT_LEVEL] = 1
            prefs[Keys.HIGHEST_UNLOCKED] = 1
            prefs[Keys.IQ_SCORE] = 100
            prefs[Keys.HINTS_REMAINING] = AdConfig.FREE_HINTS
            prefs.remove(Keys.BEST_STARS_JSON)
            prefs.remove(Keys.LAST_DAILY_EPOCH_DAY)
            prefs[Keys.DAILY_STREAK] = 0
            prefs[Keys.FIRST_TUTORIAL_SHOWN] = false
        }
        clearInProgressBoard()
    }

    fun buildRivalsLeaderboard(progress: PlayerProgress): List<RivalEntry> {
        val playerLevels = (progress.highestUnlockedLevel - 1).coerceAtLeast(0)
        val baseIq = progress.iqScore
        val rivalTemplates = listOf(
            "Sir Rowan" to 42,
            "Lady Lyra" to 28,
            "Duke Kael" to 16,
            "Aria Voss" to 7,
            "Master Orion" to -6,
            "Sage Mirella" to -18,
            "Vance Vale" to -30
        )

        val entries = mutableListOf<RivalEntry>()
        entries.add(
            RivalEntry(
                rank = 0,
                name = "You (Arrow King)",
                iq = baseIq,
                levelsCleared = playerLevels,
                isPlayer = true
            )
        )
        for ((name, iqDelta) in rivalTemplates) {
            val rivalIq = (baseIq + iqDelta).coerceAtLeast(85)
            val rivalLevels = (playerLevels + iqDelta / 3).coerceAtLeast(0)
            entries.add(
                RivalEntry(
                    rank = 0,
                    name = name,
                    iq = rivalIq,
                    levelsCleared = rivalLevels,
                    isPlayer = false
                )
            )
        }
        return entries
            .sortedWith(compareByDescending<RivalEntry> { it.iq }.thenByDescending { it.levelsCleared })
            .mapIndexed { idx, entry -> entry.copy(rank = idx + 1) }
    }

    private fun parseStarsJson(raw: String?): Map<Int, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val map = HashMap<Int, Int>(obj.length())
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k.toInt()] = obj.getInt(k)
            }
            map
        } catch (_: Throwable) {
            emptyMap()
        }
    }

    private fun encodeStarsJson(stars: Map<Int, Int>): String {
        val obj = JSONObject()
        for ((k, v) in stars) {
            obj.put(k.toString(), v)
        }
        return obj.toString()
    }
}
