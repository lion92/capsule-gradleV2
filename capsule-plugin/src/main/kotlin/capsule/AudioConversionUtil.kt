package capsule

import org.gradle.api.logging.Logging
import java.io.File

object AudioConversionUtil {

    private val logger = Logging.getLogger(AudioConversionUtil::class.java)

    /**
     * Transcodes [wavFile] to MP3 at [mp3File].
     *
     * Degraded mode: when ffmpeg is missing or fails, the WAV bytes are copied
     * under the `.mp3` name so the pipeline keeps going. That fallback is
     * logged — a silent copy left an `.mp3` that is really a WAV, which some
     * players refuse and which no downstream check could explain.
     */
    fun wavToMp3(wavFile: File, mp3File: File, ffmpegPath: String = "ffmpeg") {
        mp3File.parentFile.mkdirs()
        try {
            val result = ProcessRunner.run(
                listOf(
                    ffmpegPath, "-y",
                    "-i", wavFile.absolutePath,
                    "-codec:a", "libmp3lame",
                    "-qscale:a", "2",
                    mp3File.absolutePath,
                )
            )
            if (!result.isSuccess) {
                logger.warn(
                    "  MP3 encode failed (exit {}) for {} — keeping raw WAV bytes under the .mp3 name: {}",
                    result.exitCode, wavFile.name, result.tail(6)
                )
                wavFile.copyTo(mp3File, overwrite = true)
            }
        } catch (e: Exception) {
            logger.warn(
                "  MP3 encode could not run ({}) — keeping raw WAV bytes under the .mp3 name for {}",
                e.message, wavFile.name
            )
            wavFile.copyTo(mp3File, overwrite = true)
        }
    }

    fun isAvailable(ffmpegPath: String = "ffmpeg"): Boolean = ProcessRunner.probe(ffmpegPath, "-version")
}
