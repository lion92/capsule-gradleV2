package capsule.audio

import java.io.File

/**
 * Pure builder for the FFmpeg complex filter graph and argv of
 * [AudioPostProcessorImpl] (CAP-AUDIO US-2).
 *
 * Pure object — no I/O, no Gradle dependencies, fully testable.
 *
 * [buildFilterGraph] assembles the `filter_complex` chain:
 * - loudness normalization (`loudnorm=I=<target>:TP=-1.5:LRA=11`) is
 *   always applied (even when BGM/ducking are disabled — a capsule
 *   without loudness normalization sounds amateur).
 * - BGM mix (`volume + amix`) is applied only when
 *   [AudioPostConfig.bgmEnabled] and `hasBgmInput` is true.
 * - sidechain ducking (`sidechaincompress`) is applied only when
 *   [AudioPostConfig.duckingEnabled] **and** BGM is enabled — ducking
 *   without BGM is meaningless and is ignored.
 *
 * [buildArgv] assembles the full FFmpeg command line. When BGM is
 * enabled, the BGM file is a second `-i` input and `filter_complex` is
 * used. When BGM is disabled, only `-af` is used (single input).
 */
object AudioPostCommand {

    /**
     * Builds the FFmpeg filter graph string for [config].
     *
     * @param config      the audio post-production config
     * @param hasBgmInput `true` if the BGM file is available as a second
     *        ffmpeg input (when `false` and `bgmEnabled`, the BGM mix is
     *        skipped — degraded mode is the caller's responsibility)
     * @return the filter graph string (for `-filter_complex` or `-af`)
     */
    fun buildFilterGraph(config: AudioPostConfig, hasBgmInput: Boolean): String {
        val loudness = "loudnorm=I=${config.loudnessTarget}:TP=-1.5:LRA=11"

        // Ducking without BGM is meaningless — ignore it.
        val effectiveBgm = config.bgmEnabled && hasBgmInput
        if (!effectiveBgm) {
            return loudness
        }

        // BGM mix path: voice → loudnorm, BGM → volume, amix both.
        val bgmVolume = "volume=${config.bgmLevel}dB"
        val amix = "amix=inputs=2:duration=first:dropout_transition=0"

        if (!config.duckingEnabled) {
            // loudness on voice, volume on BGM, amix — filter_complex chain.
            return "[0:a]${loudness}[voice];" +
                "[1:a]${bgmVolume}[bgm];" +
                "[voice][bgm]${amix}[aout]"
        }

        // Ducking: sidechaincompress on BGM driven by voice.
        val ducking = "sidechaincompress=threshold=0.1:ratio=4:attack=5:release=300"
        return "[0:a]${loudness}[voice];" +
            "[1:a]${bgmVolume}[bgm];" +
            "[voice][bgm]${ducking}[ducked];" +
            "[voice][ducked]${amix}[aout]"
    }

    /**
     * Builds the full FFmpeg argv for audio post-production.
     *
     * @param videoFile  the input WebM video
     * @param bgmFile    the BGM audio file, or `null` when BGM is disabled
     * @param outputFile the output video file
     * @param config     the audio post-production config
     * @param ffmpegPath the ffmpeg executable path
     * @return the argv list (first element is the ffmpeg path)
     */
    fun buildArgv(
        videoFile: File,
        bgmFile: File?,
        outputFile: File,
        config: AudioPostConfig,
        ffmpegPath: String
    ): List<String> {
        val argv = mutableListOf(ffmpegPath, "-y", "-i", videoFile.absolutePath)

        val hasBgm = config.bgmEnabled && bgmFile != null
        if (hasBgm) {
            argv.add("-i")
            argv.add(bgmFile.absolutePath)
            argv.add("-filter_complex")
            argv.add(buildFilterGraph(config, hasBgmInput = true))
            argv.add("-map")
            argv.add("0:v")
            argv.add("-map")
            argv.add("[aout]")
        } else {
            argv.add("-af")
            argv.add(buildFilterGraph(config, hasBgmInput = false))
        }

        argv.add("-c:v")
        argv.add("copy")
        argv.add("-c:a")
        argv.add("libvorbis")
        argv.add("-shortest")
        argv.add(outputFile.absolutePath)

        return argv
    }
}