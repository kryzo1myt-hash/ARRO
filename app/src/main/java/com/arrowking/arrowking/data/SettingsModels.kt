package com.arrowking.arrowking.data

import androidx.compose.ui.graphics.Color

data class GameThemePalette(
    val id: String,
    val name: String,
    val paperBg: Color,
    val paperSurface: Color,
    val boardWellBg: Color,
    val boardWellBorder: Color,
    val primaryText: Color,
    val accentGold: Color,
    val dividerColor: Color,
    val arrowColors: List<Color>,
    val isDark: Boolean = false
)

object ThemeCatalog {
    val classicParchment = GameThemePalette(
        id = "parchment",
        name = "Royal Parchment",
        paperBg = Color(0xFFF5EBDD),
        paperSurface = Color(0xFFFFFBF4),
        boardWellBg = Color(0xFFEDE0CE),
        boardWellBorder = Color(0xFFD9C7AE),
        primaryText = Color(0xFF3E2B22),
        accentGold = Color(0xFFC48A1D),
        dividerColor = Color(0xFFD8C6AE),
        arrowColors = listOf(
            Color(0xFF5A392B), // Espresso Brown
            Color(0xFF1E5BB8), // Royal Cobalt Blue
            Color(0xFFC63A32), // Crimson Red
            Color(0xFF2B7A69)  // Deep Teal
        )
    )

    val sageStudio = GameThemePalette(
        id = "sage",
        name = "Sage Botanical",
        paperBg = Color(0xFFEEF3EC),
        paperSurface = Color(0xFFF9FCF8),
        boardWellBg = Color(0xFFE0E9DD),
        boardWellBorder = Color(0xFFC2D2BE),
        primaryText = Color(0xFF223429),
        accentGold = Color(0xFF4A7C59),
        dividerColor = Color(0xFFC6D5C3),
        arrowColors = listOf(
            Color(0xFF2E5E4E), // Forest Green
            Color(0xFFB55D3D), // Terracotta
            Color(0xFF3A608F), // Slate Blue
            Color(0xFF8C6D2E)  // Ochre
        )
    )

    val oceanSlate = GameThemePalette(
        id = "ocean",
        name = "Nordic Mist",
        paperBg = Color(0xFFEDF2F7),
        paperSurface = Color(0xFFFAFCFF),
        boardWellBg = Color(0xFFDFE8F0),
        boardWellBorder = Color(0xFFC0CFE0),
        primaryText = Color(0xFF1F2D3D),
        accentGold = Color(0xFF2A6FA8),
        dividerColor = Color(0xFFC5D2E0),
        arrowColors = listOf(
            Color(0xFF1D4E89), // Navy
            Color(0xFFD1495B), // Coral
            Color(0xFF00798C), // Cerulean
            Color(0xFF5C4D7D)  // Indigo
        )
    )

    val terracottaWarm = GameThemePalette(
        id = "terracotta",
        name = "Amber Clay",
        paperBg = Color(0xFFF8EDE3),
        paperSurface = Color(0xFFFFF8F2),
        boardWellBg = Color(0xFFF0DEC9),
        boardWellBorder = Color(0xFFDCC2A6),
        primaryText = Color(0xFF44281D),
        accentGold = Color(0xFFB85D19),
        dividerColor = Color(0xFFE0C7AF),
        arrowColors = listOf(
            Color(0xFF8C3B2B), // Rust
            Color(0xFF28587B), // Steel Blue
            Color(0xFF4A6B46), // Olive
            Color(0xFF6B3F69)  // Plum
        )
    )

    val royalNight = GameThemePalette(
        id = "royal_night",
        name = "Royal Midnight",
        paperBg = Color(0xFF1B1F2A),
        paperSurface = Color(0xFF252B3B),
        boardWellBg = Color(0xFF141821),
        boardWellBorder = Color(0xFF333C52),
        primaryText = Color(0xFFF4EDE2),
        accentGold = Color(0xFFE6AC3C),
        dividerColor = Color(0xFF323A4E),
        arrowColors = listOf(
            Color(0xFFE6AC3C), // Gold
            Color(0xFF58A4F0), // Sky Blue
            Color(0xFFEB5E55), // Coral Red
            Color(0xFF45C4A0)  // Mint
        ),
        isDark = true
    )

    val allThemes: List<GameThemePalette> = listOf(
        classicParchment,
        sageStudio,
        oceanSlate,
        terracottaWarm,
        royalNight
    )

    fun byId(id: String): GameThemePalette =
        allThemes.firstOrNull { it.id == id } ?: classicParchment
}

data class AppSettings(
    val soundEnabled: Boolean = true,
    val musicEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val reduceAnimations: Boolean = false,
    val threeDMode: Boolean = false,
    val showGrid: Boolean = false,
    val selectedThemeId: String = ThemeCatalog.classicParchment.id,
    val adsConsentAsked: Boolean = false,
    val personalizedAds: Boolean = true
)

data class PlayerProgress(
    val currentLevel: Int = 1,
    val highestUnlockedLevel: Int = 1,
    val iqScore: Int = 100,
    val hintsRemaining: Int = 3,
    val bestStarsByLevel: Map<Int, Int> = emptyMap(),
    val lastDailyClaimEpochDay: Long = -1L,
    val dailyStreak: Int = 0,
    val firstLaunchTutorialShown: Boolean = false
)

data class InProgressBoardState(
    val levelNumber: Int,
    val generatorVersion: Int,
    val remainingArrowIds: Set<Int>,
    val livesLeft: Int,
    val revivesUsed: Int,
    val elapsedSeconds: Int,
    val streak: Int,
    val hintUsedThisLevel: Boolean,
    val lifeLostThisLevel: Boolean
)

data class RivalEntry(
    val rank: Int,
    val name: String,
    val iq: Int,
    val levelsCleared: Int,
    val isPlayer: Boolean
)
