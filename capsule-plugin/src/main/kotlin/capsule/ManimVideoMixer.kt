package capsule

import org.gradle.api.logging.Logging
import java.io.File

/**
 * Muxes a Manim MP4 video with a TTS MP3 audio track into a final MP4.
 *
 * When a slide has type MANIM, the ManimEngine produces an MP4 video.
 * This mixer replaces the silent Manim video with a version that
 * includes the TTS audio for that slide.
 *
 * Design:
 * - ManimVideoMixerImpl uses ffmpeg for real muxing
 * - NoOpManimVideoMixer produces placeholder for testing
 * - Factory: CapsuleManager.resolveManimVideoMixer(ffmpegPath)
 */
interface ManimVideoMixer {
    /**
     * Muxes [videoFile] (MP4 from ManimEngine) with [audioFile] (MP3 from TTS)
     * and writes the result to [outputFile] (MP4 with audio).
     *
     * If [audioFile] does not exist, the video is copied as-is to [outputFile].
     *
     * @return the output [File]
     * @throws MixerException if muxing fails
     */
    fun mix(videoFile: File, audioFile: File, outputFile: File): File

    /** Returns true if the mixer is available (e.g. ffmpeg found) */
    fun isAvailable(): Boolean

    /** Returns the mixer name for logging */
    fun name(): String

    /** Probes the duration of a video file in seconds using ffprobe */
    fun probeDuration(videoFile: File): Double
}

class ManimVideoMixerImpl(
    private val ffmpegPath: String = "ffmpeg"
) : ManimVideoMixer {

    private val logger = Logging.getLogger(ManimVideoMixerImpl::class.java)

    // Sondé une fois : `mix()` appelle isAvailable() et le renderer parallèle
    // appelle `mix()` une fois par diapo — un `ffmpeg -version` par diapo pour
    // une réponse qui ne change pas d'un rendu à l'autre.
    private var availabilityProbe: Boolean? = null

    override fun isAvailable(): Boolean =
        availabilityProbe ?: ProcessRunner.probe(ffmpegPath, "-version").also { availabilityProbe = it }

    override fun name(): String = "ffmpeg-mixer"

    override fun mix(videoFile: File, audioFile: File, outputFile: File): File {
        if (!isAvailable()) {
            throw MixerException("ffmpeg not found at: $ffmpegPath — cannot mux Manim video with TTS audio")
        }

        outputFile.parentFile.mkdirs()

        if (!audioFile.exists()) {
            // No audio: copy video as-is
            videoFile.copyTo(outputFile, overwrite = true)
            logger.lifecycle("ManimVideoMixer: no audio for {}, copying video as-is", videoFile.name)
            return outputFile
        }

        val cmd = mutableListOf(
            ffmpegPath, "-y",
            "-i", videoFile.absolutePath,
            "-i", audioFile.absolutePath,
            "-c:v", "copy",
            "-c:a", "aac",
            "-map", "0:v:0",
            "-map", "1:a:0",
            "-shortest",
            outputFile.absolutePath
        )

        val result = ProcessRunner.run(cmd)
        if (!result.isSuccess) {
            throw MixerException("ffmpeg mux failed (exit ${result.exitCode}): ${result.tail()}")
        }

        if (!outputFile.exists()) {
            throw MixerException("ffmpeg mux produced no output file: ${outputFile.absolutePath}")
        }

        logger.lifecycle("ManimVideoMixer: {} + {} → {} ({} bytes)",
            videoFile.name, audioFile.name, outputFile.name, outputFile.length())

        return outputFile
    }

    override fun probeDuration(videoFile: File): Double = MediaProbeUtil.probeDuration(videoFile)
}

class NoOpManimVideoMixer : ManimVideoMixer {
    override fun isAvailable(): Boolean = true
    override fun name(): String = "noop-mixer"

    override fun mix(videoFile: File, audioFile: File, outputFile: File): File {
        outputFile.parentFile.mkdirs()
        val placeholder = listOf(
            "# MANIM MIXER PLACEHOLDER (noop-mixer engine)",
            "# Video: ${videoFile.name}",
            "# Audio: ${audioFile.name}"
        ).joinToString("\n")
        outputFile.writeText(placeholder)
        return outputFile
    }

    override fun probeDuration(videoFile: File): Double = 0.0
}

class MixerException(message: String) : RuntimeException(message)