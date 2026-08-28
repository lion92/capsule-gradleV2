package capsule.podcast

import org.gradle.api.logging.Logging
import java.io.File

/**
 * FFmpeg-backed implementation of [PodcastConcatenator] (CAP-PODCAST US-1).
 *
 * Concatenates the per-slide MP3 files (produced by
 * `synthesizeTtsForScript` in the capsule video pipeline) into a
 * single podcast MP3 via the FFmpeg concat demuxer
 * (`-f concat -safe 0 -i <listfile> -c copy <output>`). No
 * re-encoding — lossless and fast even for long capsules.
 *
 * Economy of ink (AGENT.adoc):
 * - Returns `false` when ffmpeg is unavailable (degraded — no
 *   podcast produced, caller keeps no podcast, pattern
 *   [capsule.NoOpVideoFormatConverter]).
 * - Returns `false` when the MP3 list is empty (nothing to concatenate).
 * - Writes the concat list file to a temporary location next to the
 *   output and deletes it after the run.
 */
class PodcastConcatenatorImpl(
    private val ffmpegPath: String = "ffmpeg"
) : PodcastConcatenator {

    private val logger = Logging.getLogger(PodcastConcatenatorImpl::class.java)

    override fun isAvailable(): Boolean {
        return try {
            capsule.ProcessRunner.probe(ffmpegPath, "-version")
        } catch (_: Exception) {
            false
        }
    }

    override fun name(): String = "ffmpeg-podcast-concat"

    override fun concatenate(mp3Files: List<File>, outputFile: File): Boolean {
        if (!isAvailable()) return false
        if (mp3Files.isEmpty()) return false

        outputFile.parentFile.mkdirs()

        val listFile = File(outputFile.parentFile, "${outputFile.nameWithoutExtension}-concat-list.txt")
        return try {
            listFile.writeText(PodcastConcatCommand.buildConcatListContent(mp3Files))
            val argv = PodcastConcatCommand.buildConcatArgv(listFile, outputFile, ffmpegPath)
            val result = capsule.ProcessRunner.run(argv)
            if (!result.isSuccess) {
                logger.warn("ffmpeg podcast concat failed (exit {}): {}", result.exitCode, result.tail())
                return false
            }
            outputFile.exists() && outputFile.length() > 0
        } catch (e: Exception) {
            logger.warn("ffmpeg podcast concat error: {}", e.message)
            false
        } finally {
            listFile.delete()
        }
    }
}

/**
 * No-op fallback for [PodcastConcatenator] (CAP-PODCAST US-1).
 *
 * Pattern [capsule.audio.NoOpAudioPostProcessor]: `isAvailable() = true`,
 * `concatenate()` returns `false` so the caller keeps no podcast
 * (degraded mode, backward compatibility — no real concat in tests).
 */
class NoOpPodcastConcatenator : PodcastConcatenator {
    override fun isAvailable(): Boolean = true
    override fun name(): String = "noop-podcast-concat"
    override fun concatenate(mp3Files: List<File>, outputFile: File): Boolean = false
}