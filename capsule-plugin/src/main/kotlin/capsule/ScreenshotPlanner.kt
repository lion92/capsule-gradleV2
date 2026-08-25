package capsule

import java.io.File

/**
 * Plans the screenshot-based capture of a multi-slide deck (CAP-CR3-3 US-3).
 *
 * Pure object — no I/O, no Playwright, no FFmpeg. Produces a
 * [ScreenshotCapturePlan] that [ScreenshotCaptureImpl] executes.
 *
 * For each slide, the plan describes:
 * - the PNG screenshot path (`slide-0.png`, `slide-1.png`, ...),
 * - the per-slide WebM path (`slide-0.webm`, `slide-1.webm`, ...),
 * - the slide duration (seconds, from [slideDurations]).
 *
 * The plan also describes the concat step:
 * - the concat list file path (`concat-list.txt`),
 * - the final merged WebM path (`capsule.webm`).
 */
object ScreenshotPlanner {

    /**
     * Name of the concatenated deck video produced by the SCREENSHOT strategy.
     * Consumers must select this file, never a per-slide `slide-N.webm`.
     */
    const val FINAL_WEBM_NAME: String = "capsule.webm"

    /**
     * Builds a [ScreenshotCapturePlan] for the given slide durations.
     *
     * @param outputDir       the directory where PNGs, per-slide WebMs,
     *        the concat list and the final WebM will be written
     * @param slideDurations  one duration (seconds) per slide, non-empty
     * @return the capture plan
     */
    fun plan(outputDir: File, slideDurations: List<Double>): ScreenshotCapturePlan {
        require(slideDurations.isNotEmpty()) { "slideDurations must not be empty" }
        val entries = slideDurations.mapIndexed { index, duration ->
            ScreenshotSlideEntry(
                index = index,
                pngFile = outputDir.resolve("slide-$index.png"),
                webmFile = outputDir.resolve("slide-$index.webm"),
                durationSecs = duration
            )
        }
        return ScreenshotCapturePlan(
            slides = entries,
            concatListFile = outputDir.resolve("concat-list.txt"),
            finalWebm = outputDir.resolve(FINAL_WEBM_NAME)
        )
    }

    /**
     * Renders the ffmpeg concat demuxer list file content for the plan.
     *
     * Format: one `file 'slide-N.webm'` line per slide, in order.
     */
    fun renderConcatList(plan: ScreenshotCapturePlan): String =
        plan.slides.joinToString("\n") { "file '${it.webmFile.name}'" } + "\n"

    /**
     * Builds the FFmpeg command line (argv) to convert a single PNG into a
     * WebM of the exact slide duration (1 static frame, libvpx).
     *
     * The source is one still image, so the output only needs a keyframe and a
     * duration: `-r 1` keeps the encoder to one frame per second instead of the
     * 25 it would otherwise emit, which is where most of the encoding time went.
     */
    fun ffmpegPngToWebmArgs(
        entry: ScreenshotSlideEntry,
        viewportWidth: Int,
        viewportHeight: Int,
        ffmpegPath: String = "ffmpeg",
    ): List<String> = listOf(
        ffmpegPath, "-y",
        "-loop", "1",
        "-framerate", "1",
        "-i", entry.pngFile.absolutePath,
        "-t", entry.durationSecs.toString(),
        "-c:v", "libvpx",
        "-b:v", "500k",
        "-vf", "scale=$viewportWidth:$viewportHeight",
        "-pix_fmt", "yuv420p",
        "-auto-alt-ref", "0",
        "-r", "1",
        "-cpu-used", "8",
        "-deadline", "realtime",
        entry.webmFile.absolutePath
    )

    /**
     * Builds the FFmpeg concat demuxer command line (argv) to merge
     * per-slide WebMs into the final `capsule.webm`.
     */
    fun ffmpegConcatArgs(plan: ScreenshotCapturePlan, ffmpegPath: String = "ffmpeg"): List<String> = listOf(
        ffmpegPath, "-y",
        "-f", "concat",
        "-safe", "0",
        "-i", plan.concatListFile.absolutePath,
        "-c", "copy",
        plan.finalWebm.absolutePath
    )
}

/**
 * Immutable plan describing the screenshot-based capture of a deck.
 */
data class ScreenshotCapturePlan(
    val slides: List<ScreenshotSlideEntry>,
    val concatListFile: File,
    val finalWebm: File
) {
    val size: Int get() = slides.size
    val isEmpty: Boolean get() = slides.isEmpty()
}

/**
 * One slide entry in a [ScreenshotCapturePlan].
 */
data class ScreenshotSlideEntry(
    val index: Int,
    val pngFile: File,
    val webmFile: File,
    val durationSecs: Double
)