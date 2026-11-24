package com.arrowking.arrowking.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * 100% procedurally synthesized SFX + ambient music engine.
 * Zero bundled audio files.
 */
class SoundEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sampleRate = 22050

    @Volatile private var soundEnabled = true
    @Volatile private var musicEnabled = true
    @Volatile private var appForeground = true
    @Volatile private var adPaused = false

    private var musicJob: Job? = null
    private var musicTrack: AudioTrack? = null

    private var whooshBuf: ShortArray = ShortArray(0)
    private var errorBuf: ShortArray = ShortArray(0)
    private var tapBuf: ShortArray = ShortArray(0)
    private var hintBuf: ShortArray = ShortArray(0)
    private var winBuf: ShortArray = ShortArray(0)
    private var bossWinBuf: ShortArray = ShortArray(0)
    private var splashWhooshBuf: ShortArray = ShortArray(0)

    @Volatile private var initialized = false

    fun initAsync(onReady: (() -> Unit)? = null) {
        if (initialized) {
            onReady?.invoke()
            return
        }
        scope.launch {
            whooshBuf = synthWhoosh(durationMs = 190, startFreq = 420.0, endFreq = 980.0)
            errorBuf = synthError(durationMs = 210)
            tapBuf = synthTap(durationMs = 45)
            hintBuf = synthChimeArpeggio(listOf(587.33, 739.99, 880.0, 1174.66), noteMs = 75)
            winBuf = synthChimeArpeggio(listOf(523.25, 659.25, 783.99, 987.77, 1046.50), noteMs = 130)
            bossWinBuf = synthChimeArpeggio(
                listOf(392.0, 493.88, 587.33, 783.99, 987.77, 1174.66, 1567.98),
                noteMs = 150
            )
            splashWhooshBuf = synthWhoosh(durationMs = 420, startFreq = 260.0, endFreq = 860.0)
            initialized = true
            onReady?.invoke()
            updateMusicPlayback()
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundEnabled = enabled
    }

    fun setMusicEnabled(enabled: Boolean) {
        musicEnabled = enabled
        updateMusicPlayback()
    }

    fun onAppForegroundChanged(foreground: Boolean) {
        appForeground = foreground
        updateMusicPlayback()
    }

    fun pauseForAd() {
        adPaused = true
        updateMusicPlayback()
    }

    fun resumeFromAd() {
        adPaused = false
        updateMusicPlayback()
    }

    fun playWhoosh() = playBuffer(whooshBuf, volume = 0.55f)
    fun playError() = playBuffer(errorBuf, volume = 0.65f)
    fun playTap() = playBuffer(tapBuf, volume = 0.40f)
    fun playHint() = playBuffer(hintBuf, volume = 0.55f)
    fun playWin(isBoss: Boolean) = playBuffer(if (isBoss) bossWinBuf else winBuf, volume = 0.65f)
    fun playSplashWhoosh() = playBuffer(splashWhooshBuf, volume = 0.50f)

    private fun playBuffer(samples: ShortArray, volume: Float) {
        if (!soundEnabled || !initialized || samples.isEmpty()) return
        scope.launch {
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(samples.size * 2)
                    .build()
                track.write(samples, 0, samples.size)
                track.setVolume(volume)
                track.play()
                val durationMs = (samples.size * 1000L) / sampleRate + 60L
                kotlinx.coroutines.delay(durationMs)
                track.stop()
                track.release()
            } catch (_: Throwable) {
                // AudioTrack may be unavailable in headless unit tests
            }
        }
    }

    private fun updateMusicPlayback() {
        val shouldPlay = initialized && musicEnabled && appForeground && !adPaused
        if (shouldPlay && musicJob == null) {
            startAmbientMusicLoop()
        } else if (!shouldPlay && musicJob != null) {
            stopAmbientMusicLoop()
        }
    }

    private fun startAmbientMusicLoop() {
        musicJob = scope.launch {
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(sampleRate)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(minBuf)
                    .build()
                musicTrack = track
                track.setVolume(0.22f)
                track.play()

                // Warm calming pentatonic marimba/harp pattern
                val scale = doubleArrayOf(261.63, 293.66, 329.63, 392.00, 440.00, 523.25, 587.33, 659.25)
                val pattern = intArrayOf(0, 2, 4, 5, 3, 2, 4, 7, 5, 4, 2, 1, 0, 3, 4, 2)
                val stepSamples = (sampleRate * 0.42).toInt()
                val chunk = ShortArray(stepSamples)
                var stepIdx = 0

                while (isActive && musicEnabled && appForeground && !adPaused) {
                    val f1 = scale[pattern[stepIdx % pattern.size]]
                    val f2 = scale[(pattern[stepIdx % pattern.size] + 2) % scale.size] * 0.5
                    for (i in 0 until stepSamples) {
                        val t = i.toDouble() / sampleRate
                        val attack = (t / 0.015).coerceAtMost(1.0)
                        val decay = exp(-t * 5.2)
                        val wave = sin(2.0 * PI * f1 * t) * 0.72 +
                            sin(2.0 * PI * f1 * 2.0 * t) * 0.14 +
                            sin(2.0 * PI * f2 * t) * 0.28
                        chunk[i] = (wave * attack * decay * 14000.0).toInt().coerceIn(-32767, 32767).toShort()
                    }
                    track.write(chunk, 0, chunk.size)
                    stepIdx++
                }
            } catch (_: Throwable) {
                // Safe no-op in non-audio test environment
            } finally {
                try {
                    musicTrack?.stop()
                    musicTrack?.release()
                } catch (_: Throwable) {
                }
                musicTrack = null
            }
        }
    }

    private fun stopAmbientMusicLoop() {
        musicJob?.cancel()
        musicJob = null
        try {
            musicTrack?.pause()
            musicTrack?.flush()
            musicTrack?.release()
        } catch (_: Throwable) {
        }
        musicTrack = null
    }

    private fun synthWhoosh(durationMs: Int, startFreq: Double, endFreq: Double): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        val rng = Random(42)
        var lp = 0.0
        var phase = 0.0
        for (i in 0 until count) {
            val u = i.toDouble() / count
            val env = sin(PI * u) * exp(-u * 1.1)
            val freq = startFreq + (endFreq - startFreq) * u
            phase += 2.0 * PI * freq / sampleRate
            val noise = rng.nextDouble() * 2.0 - 1.0
            val alpha = 0.08 + 0.25 * sin(PI * u)
            lp += alpha * (noise - lp)
            val sample = (lp * 0.65 + sin(phase) * 0.35) * env * 22000.0
            out[i] = sample.toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthError(durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val u = i.toDouble() / count
            val env = (1.0 - u) * exp(-t * 9.0)
            val wave = sin(2.0 * PI * 145.0 * t) * 0.65 + sin(2.0 * PI * 155.0 * t) * 0.35
            out[i] = (wave * env * 24000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthTap(durationMs: Int): ShortArray {
        val count = (sampleRate * durationMs) / 1000
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 75.0)
            val wave = sin(2.0 * PI * 680.0 * t)
            out[i] = (wave * env * 18000.0).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    private fun synthChimeArpeggio(freqs: List<Double>, noteMs: Int): ShortArray {
        val noteSamples = (sampleRate * noteMs) / 1000
        val tailSamples = (sampleRate * 260) / 1000
        val total = freqs.size * noteSamples + tailSamples
        val accum = DoubleArray(total)
        for (n in freqs.indices) {
            val f = freqs[n]
            val start = n * noteSamples
            val len = total - start
            for (i in 0 until len) {
                val t = i.toDouble() / sampleRate
                val att = (t / 0.01).coerceAtMost(1.0)
                val dec = exp(-t * 6.5)
                accum[start + i] += (sin(2.0 * PI * f * t) + 0.25 * sin(2.0 * PI * f * 2.0 * t)) * att * dec
            }
        }
        return ShortArray(total) { idx ->
            (accum[idx] * 9500.0).toInt().coerceIn(-32767, 32767).toShort()
        }
    }
}
