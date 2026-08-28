package capsule

import java.io.File

/**
 * Shared utility for probing media file durations via ffprobe.
 *
 * Extracted to eliminate duplication across CapsuleVideoTask, ManimEngineImpl,
 * and ManimVideoMixerImpl.
 */
object MediaProbeUtil {

    /**
     * Probes the duration of a media file with ffprobe (default binary).
     */
    fun probeDuration(file: File): Double = probeDuration(file, "ffprobe")

    /**
     * Probes the duration of a media file with the ffprobe binary at [ffprobePath].
     *
     * @return duration in seconds, or 0.0 when ffprobe is missing, fails, or
     *         prints something that is not a number.
     */
    fun probeDuration(file: File, ffprobePath: String): Double {
        return try {
            val result = ProcessRunner.run(
                listOf(
                    ffprobePath, "-v", "quiet",
                    "-show_entries", "format=duration",
                    "-of", "csv=p=0",
                    file.absolutePath,
                ),
                timeoutMinutes = ProcessRunner.PROBE_TIMEOUT_MINUTES,
            )
            result.output.trim().toDoubleOrNull() ?: 0.0
        } catch (_: Exception) {
            0.0
        }
    }

    /**
     * Returns true if ffprobe is available in the system PATH.
     */
    fun isFfprobeAvailable(): Boolean = isFfprobeAvailable("ffprobe")

    /**
     * Returns true if the ffprobe binary at [ffprobePath] is available and executable.
     */
    fun isFfprobeAvailable(ffprobePath: String): Boolean = ProcessRunner.probe(ffprobePath, "-version")
}
