package com.arrowking.arrowking

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.arrowking.arrowking.ads.AdConfig
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArrowKingRobolectricTest {

    @Test
    fun `app name and ad config match step 0 settings`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ARROW KING", appName)
        assertEquals("800451406", AdConfig.UNITY_GAME_ID)
        assertEquals("BP_Rewarded_Android", AdConfig.REWARDED_PLACEMENT_ID)
        assertEquals(true, AdConfig.TEST_MODE)
    }
}
