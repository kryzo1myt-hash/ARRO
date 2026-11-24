package com.arrowking.arrowking.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.arrowking.arrowking.BuildConfig
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.ads.metadata.MetaData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AdsManager {
    val isRewardedReady: StateFlow<Boolean>
    val isInitialized: StateFlow<Boolean>
    val isShowingDebugTestAd: StateFlow<Boolean>

    fun updatePrivacyConsent(context: Context, personalizedAds: Boolean)
    fun initialize(context: Context, personalizedAds: Boolean)
    fun preloadRewarded()
    fun showRewarded(
        activity: Activity,
        onAdAudioPause: () -> Unit,
        onAdAudioResume: () -> Unit,
        onRewarded: () -> Unit,
        onFailedOrSkipped: (message: String) -> Unit
    )
    fun completeDebugTestAd(granted: Boolean)
}

class UnityAdsManager : AdsManager {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isRewardedReady = MutableStateFlow(false)
    override val isRewardedReady: StateFlow<Boolean> = _isRewardedReady.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    override val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _isShowingDebugTestAd = MutableStateFlow(false)
    override val isShowingDebugTestAd: StateFlow<Boolean> = _isShowingDebugTestAd.asStateFlow()

    private var pendingDebugOnRewarded: (() -> Unit)? = null
    private var pendingDebugOnFailed: ((String) -> Unit)? = null
    private var pendingDebugOnResumeAudio: (() -> Unit)? = null

    private fun isPlaceholderGameId(): Boolean {
        val id = AdConfig.UNITY_GAME_ID.trim()
        return id.isEmpty() || id == "YOUR_UNITY_GAME_ID"
    }

    override fun updatePrivacyConsent(context: Context, personalizedAds: Boolean) {
        if (isPlaceholderGameId()) return
        try {
            val appCtx = context.applicationContext
            val gdpr = MetaData(appCtx)
            gdpr.set("gdpr.consent", personalizedAds)
            gdpr.commit()

            val privacy = MetaData(appCtx)
            privacy.set("privacy.consent", personalizedAds)
            privacy.commit()

            val userMeta = MetaData(appCtx)
            userMeta.set("user.nonbehavioral", !personalizedAds)
            userMeta.commit()
        } catch (_: Throwable) {
        }
    }

    override fun initialize(context: Context, personalizedAds: Boolean) {
        if (isPlaceholderGameId()) {
            // Placeholder fallback behavior: debug uses simulated 3s dialog; release skips init.
            _isInitialized.value = BuildConfig.DEBUG
            _isRewardedReady.value = BuildConfig.DEBUG
            return
        }
        if (_isInitialized.value) return

        val appCtx = context.applicationContext
        updatePrivacyConsent(appCtx, personalizedAds)

        try {
            UnityAds.initialize(
                appCtx,
                AdConfig.UNITY_GAME_ID,
                AdConfig.TEST_MODE,
                object : IUnityAdsInitializationListener {
                    override fun onInitializationComplete() {
                        mainHandler.post {
                            _isInitialized.value = true
                            preloadRewarded()
                        }
                    }

                    override fun onInitializationFailed(
                        error: UnityAds.UnityAdsInitializationError?,
                        message: String?
                    ) {
                        mainHandler.post {
                            _isInitialized.value = false
                            _isRewardedReady.value = false
                        }
                    }
                }
            )
        } catch (_: Throwable) {
            _isInitialized.value = false
            _isRewardedReady.value = false
        }
    }

    override fun preloadRewarded() {
        if (isPlaceholderGameId()) {
            _isRewardedReady.value = BuildConfig.DEBUG
            return
        }
        if (!_isInitialized.value) return

        try {
            UnityAds.load(
                AdConfig.REWARDED_PLACEMENT_ID,
                object : IUnityAdsLoadListener {
                    override fun onUnityAdsAdLoaded(placementId: String?) {
                        mainHandler.post {
                            _isRewardedReady.value = true
                        }
                    }

                    override fun onUnityAdsFailedToLoad(
                        placementId: String?,
                        error: UnityAds.UnityAdsLoadError?,
                        message: String?
                    ) {
                        mainHandler.post {
                            _isRewardedReady.value = false
                        }
                    }
                }
            )
        } catch (_: Throwable) {
            _isRewardedReady.value = false
        }
    }

    override fun showRewarded(
        activity: Activity,
        onAdAudioPause: () -> Unit,
        onAdAudioResume: () -> Unit,
        onRewarded: () -> Unit,
        onFailedOrSkipped: (message: String) -> Unit
    ) {
        if (isPlaceholderGameId()) {
            if (BuildConfig.DEBUG) {
                onAdAudioPause()
                pendingDebugOnRewarded = onRewarded
                pendingDebugOnFailed = onFailedOrSkipped
                pendingDebugOnResumeAudio = onAdAudioResume
                _isShowingDebugTestAd.value = true
            } else {
                onFailedOrSkipped("Ads not set up yet")
            }
            return
        }

        if (!_isInitialized.value || !_isRewardedReady.value) {
            preloadRewarded()
            onFailedOrSkipped("Ad not available, try again")
            return
        }

        onAdAudioPause()
        _isRewardedReady.value = false

        try {
            UnityAds.show(
                activity,
                AdConfig.REWARDED_PLACEMENT_ID,
                UnityAdsShowOptions(),
                object : IUnityAdsShowListener {
                    override fun onUnityAdsShowFailure(
                        placementId: String?,
                        error: UnityAds.UnityAdsShowError?,
                        message: String?
                    ) {
                        mainHandler.post {
                            onAdAudioResume()
                            preloadRewarded()
                            onFailedOrSkipped("Ad not available, try again")
                        }
                    }

                    override fun onUnityAdsShowStart(placementId: String?) = Unit

                    override fun onUnityAdsShowClick(placementId: String?) = Unit

                    override fun onUnityAdsShowComplete(
                        placementId: String?,
                        state: UnityAds.UnityAdsShowCompletionState?
                    ) {
                        mainHandler.post {
                            onAdAudioResume()
                            preloadRewarded()
                            if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                                onRewarded()
                            } else {
                                onFailedOrSkipped("Ad not available, try again")
                            }
                        }
                    }
                }
            )
        } catch (_: Throwable) {
            onAdAudioResume()
            preloadRewarded()
            onFailedOrSkipped("Ad not available, try again")
        }
    }

    override fun completeDebugTestAd(granted: Boolean) {
        _isShowingDebugTestAd.value = false
        pendingDebugOnResumeAudio?.invoke()
        if (granted) {
            pendingDebugOnRewarded?.invoke()
        } else {
            pendingDebugOnFailed?.invoke("Ad not available, try again")
        }
        pendingDebugOnRewarded = null
        pendingDebugOnFailed = null
        pendingDebugOnResumeAudio = null
    }
}
