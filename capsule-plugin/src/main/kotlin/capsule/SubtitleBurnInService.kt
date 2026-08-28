package capsule

import org.gradle.api.logging.Logging
import java.io.File

/**
 * Burns subtitles into a WebM video using FFmpeg's subtitles filter.
 *
 * When subtitleBurnIn is enabled, the generated SRT/VTT subtitle file
 * is hard-burned into the final WebM video as visible text overlay.
 *
 * Design:
 * - SubtitleBurnInServiceImpl uses ffmpeg subtitles filter
 * - NoOpSubtitleBurnInService produces placeholder for testing
 * - Factory: CapsuleManager.resolveSubtitleBurnInService(ffmpegPath)
 */
interface SubtitleBurnInService {
    /**
     * Burns [subtitleFile] (SRT or VTT) into [videoFile] (WebM) and writes
     * the result to [outputFile].
     *
     * @return the output [File] with hard-burned subtitles
     * @throws BurnInException if burn-in fails
     */
    fun burnIn(videoFile: File, subtitleFile: File, outputFile: File): File

    /** Returns true if the service is available (e.g. ffmpeg found) */
    fun isAvailable(): Boolean

    /** Returns the service name for logging */
    fun name(): String
}

class SubtitleBurnInServiceImpl(
    private val ffmpegPath: String = "ffmpeg",
    private val style: SubtitleBurnInStyle = SubtitleBurnInStyle()
) : SubtitleBurnInService {

    private val logger = Logging.getLogger(SubtitleBurnInServiceImpl::class.java)

    override fun isAvailable(): Boolean {
        return try {
            ProcessRunner.probe(ffmpegPath, "-version")
        } catch (e: Exception) {
            false
        }
    }

    override fun name(): String = "ffmpeg-burnin"

    override fun burnIn(videoFile: File, subtitleFile: File, outputFile: File): File {
        if (!isAvailable()) {
            throw BurnInException("ffmpeg not found at: $ffmpegPath — cannot burn-in subtitles")
        }

        if (!subtitleFile.exists()) {
            throw BurnInException("Subtitle file not found: ${subtitleFile.absolutePath}")
        }

        outputFile.parentFile.mkdirs()

        val cmd = SubtitleBurnInCommand.buildArgv(videoFile, subtitleFile, outputFile, style, ffmpegPath)

        val result = ProcessRunner.run(cmd)
        if (!result.isSuccess) {
            throw BurnInException("ffmpeg burn-in failed (exit ${result.exitCode}): ${result.tail()}")
        }

        if (!outputFile.exists()) {
            throw BurnInException("ffmpeg burn-in produced no output file: ${outputFile.absolutePath}")
        }

        logger.lifecycle("SubtitleBurnIn: {} + {} → {} ({} bytes)",
            videoFile.name, subtitleFile.name, outputFile.name, outputFile.length())

        return outputFile
    }
}

class NoOpSubtitleBurnInService : SubtitleBurnInService {
    override fun isAvailable(): Boolean = true
    override fun name(): String = "noop-burnin"

    override fun burnIn(videoFile: File, subtitleFile: File, outputFile: File): File {
        outputFile.parentFile.mkdirs()
        val placeholder = listOf(
            "# SUBTITLE BURN-IN PLACEHOLDER (noop-burnin engine)",
            "# Video: ${videoFile.name}",
            "# Subtitle: ${subtitleFile.name}"
        ).joinToString("\n")
        outputFile.writeText(placeholder)
        return outputFile
    }
}

class BurnInException(message: String) : RuntimeException(message)
