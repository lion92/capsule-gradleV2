package capsule.audio

import org.gradle.api.logging.Logging
import java.io.File

/**
 * FFmpeg-backed implementation of [AudioPostProcessor] (CAP-AUDIO US-2).
 *
 * Applies loudness normalization (EBU R128), BGM mix, and sidechain
 * ducking to the final capsule WebM via a complex `filter_complex` chain.
 *
 * Economy of ink (AGENT.adoc):
 * - Returns `false` when ffmpeg is unavailable (degraded — caller keeps
 *   the original video, pattern [capsule.NoOpVideoFormatConverter]).
 * - Returns `false` when `bgmEnabled` but `bgmFile` is blank/missing
 *   (degraded — keeps the original video).
 * - Throws [AudioPostException] only on unrecoverable ffmpeg failures
 *   (non-zero exit with an existing input — pattern [capsule.BurnInException]).
 */
class AudioPostProcessorImpl(
    private val ffmpegPath: String = "ffmpeg"
) : AudioPostProcessor {

    private val logger = Logging.getLogger(AudioPostProcessorImpl::class.java)

    override fun isAvailable(): Boolean {
        return try {
            capsule.ProcessRunner.probe(ffmpegPath, "-version")
        } catch (_: Exception) {
            false
        }
    }

    override fun name(): String = "ffmpeg-audio-post"

    override fun process(videoFile: File, outputFile: File, config: AudioPostConfig): Boolean {
        if (!isAvailable()) return false
        if (!videoFile.exists()) return false

        // BGM enabled but file blank/missing → degraded (keep original).
        val bgmFile: File? = if (config.bgmEnabled && config.bgmFile.isNotBlank()) {
            val f = File(config.bgmFile)
            if (f.exists()) f else null
        } else null

        // BGM enabled but no usable file → degraded.
        if (config.bgmEnabled && bgmFile == null) {
            logger.warn("Audio post: BGM enabled but file missing/blank ('{}') — keeping original", config.bgmFile)
            return false
        }

        outputFile.parentFile.mkdirs()

        val argv = AudioPostCommand.buildArgv(videoFile, bgmFile, outputFile, config, ffmpegPath)

        return try {
            val result = capsule.ProcessRunner.run(argv)
            if (!result.isSuccess) {
                logger.warn("ffmpeg audio post failed (exit {}): {}", result.exitCode, result.tail())
                return false
            }
            outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            logger.warn("ffmpeg audio post error: {}", e.message)
            false
        }
    }
}

/**
 * No-op fallback for [AudioPostProcessor] (CAP-AUDIO US-2).
 *
 * Pattern [capsule.NoOpVideoFormatConverter]: `isAvailable() = true`,
 * `process()` returns `false` so the caller keeps the original video
 * (degraded mode, backward compatibility — no real audio post in tests).
 */
class NoOpAudioPostProcessor : AudioPostProcessor {
    override fun isAvailable(): Boolean = true
    override fun name(): String = "noop-audio-post"
    override fun process(videoFile: File, outputFile: File, config: AudioPostConfig): Boolean = false
}