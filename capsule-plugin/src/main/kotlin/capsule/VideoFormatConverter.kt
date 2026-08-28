package capsule

import org.gradle.api.logging.Logging
import java.io.File

/**
 * Transcodes a WebM capsule video to MP4/H.264 via FFmpeg (CAP-MP4 US-2).
 *
 * Design mirrors [SubtitleBurnInService]:
 * - [VideoFormatConverterImpl] uses ffmpeg `-c:v libx264 -c:a aac -movflags +faststart`
 * - [NoOpVideoFormatConverter] is the test/no-ffmpeg fallback (returns `false`)
 * - Factory: [CapsuleManager.resolveFormatConverter] (pattern
 *   `resolveSubtitleBurnInService` — ffmpegPath="noop" → NoOp, unavailable +
 *   non-strict → NoOp, unavailable + strict → throw via [StrictModeGuard])
 *
 * Economy of ink (AGENT.adoc): the transcode is *not* invoked here. The caller
 * ([FormatConversion.convertIfNeeded]) decides whether to call [convertToMp4]
 * based on the existing MP4's probed duration.
 */
interface VideoFormatConverter {
    /**
     * Transcodes [webmFile] (WebM) to [mp4File] (MP4/H.264).
     *
     * @return `true` if the MP4 was produced successfully, `false` otherwise
     *         (NoOp fallback, ffmpeg failure, missing input). On `false`, the
     *         caller keeps the WebM intermediate (degraded mode).
     */
    fun convertToMp4(webmFile: File, mp4File: File): Boolean

    /** Returns true if ffmpeg is available at the configured path. */
    fun isAvailable(): Boolean

    /** Returns the converter name for logging. */
    fun name(): String
}

/**
 * FFmpeg-backed implementation. Uses libx264 + AAC + faststart for
 * LMS/YouTube/OF compatibility.
 */
class VideoFormatConverterImpl(
    private val ffmpegPath: String = "ffmpeg"
) : VideoFormatConverter {

    private val logger = Logging.getLogger(VideoFormatConverterImpl::class.java)

    override fun isAvailable(): Boolean {
        return try {
            ProcessRunner.probe(ffmpegPath, "-version")
        } catch (_: Exception) {
            false
        }
    }

    override fun name(): String = "ffmpeg-format"

    override fun convertToMp4(webmFile: File, mp4File: File): Boolean {
        if (!isAvailable()) return false
        if (!webmFile.exists()) return false
        mp4File.parentFile.mkdirs()
        val cmd = mutableListOf(
            ffmpegPath, "-y",
            "-i", webmFile.absolutePath,
            "-c:v", "libx264",
            "-c:a", "aac",
            "-movflags", "+faststart",
            mp4File.absolutePath
        )
        return try {
            val result = ProcessRunner.run(cmd)
            if (!result.isSuccess) {
                logger.warn("ffmpeg format conversion failed (exit {}): {}", result.exitCode, result.tail())
                return false
            }
            mp4File.exists() && mp4File.length() > 0
        } catch (e: Exception) {
            logger.warn("ffmpeg format conversion error: {}", e.message)
            false
        }
    }
}

/**
 * No-op fallback. Returns `false` from [convertToMp4] so the caller keeps
 * the WebM intermediate (degraded mode, backward compatibility).
 */
class NoOpVideoFormatConverter : VideoFormatConverter {
    override fun isAvailable(): Boolean = true
    override fun name(): String = "noop-format"
    override fun convertToMp4(webmFile: File, mp4File: File): Boolean = false
}