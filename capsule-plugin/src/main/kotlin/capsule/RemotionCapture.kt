package capsule

import org.gradle.api.logging.Logging
import java.io.File

/**
 * Capture engine backed by Remotion (CAP-ANIM).
 *
 * Renders the deck frame by frame from the React composition bundled with the
 * plugin, which is what allows motion: entrance transitions, a slow drift and
 * cross-fades between slides, where [ScreenshotCaptureImpl] can only hold one
 * frozen image per slide.
 *
 * The output contract is deliberately identical to the other engines — a
 * `capsule.webm` of the summed slide durations in `outputDir` — so the rest of
 * the pipeline (audio mux, subtitle burn-in, MP4 conversion) is untouched. The
 * video it produces is silent; narration is muxed on afterwards exactly as
 * before.
 *
 * Consumers do not author React: [RemotionTemplate] materialises the
 * composition into the project directory on first use.
 */
class RemotionCaptureImpl(
    private val projectDir: File,
    private val nodeExecutablePath: String = "node",
    private val concurrency: Int = 4,
    private val fps: Int = 30,
    /** Directories searched for the Manim animation named by `data-manim`. */
    private val manimSources: List<File> = emptyList(),
) : PlaywrightCapture {

    private val logger = Logging.getLogger(RemotionCaptureImpl::class.java)

    private var availabilityProbe: Boolean? = null

    override fun name(): String = "remotion"

    /**
     * MP4/H.264. VP8 was measured at 9.1 frames per second against 13.1 for
     * H.264 on the same deck — it was the pipeline's bottleneck, not the frame
     * rendering — and an H.264 stream has no place in a WebM container.
     */
    override fun outputExtension(): String = "mp4"

    /**
     * Remotion needs Node and its own dependency tree. Probing runs
     * `node --version`, which is cheap, and checks that the template can be
     * materialised — a missing `node_modules` is reported by the render itself
     * with a far more actionable message than a silent NoOp fallback.
     */
    override fun isAvailable(): Boolean = availabilityProbe ?: (
        try {
            val process = ProcessBuilder(nodeExecutablePath, "--version")
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            process.waitFor() == 0
        } catch (e: Exception) {
            logger.info("Remotion capture unavailable: {}", e.message)
            false
        }
        ).also { availabilityProbe = it }

    override fun capture(
        deckHtmlPath: String,
        outputDir: File,
        viewportWidth: Int,
        viewportHeight: Int,
        slideDurations: List<Double>
    ) {
        require(slideDurations.isNotEmpty()) { "slideDurations must not be empty" }
        outputDir.mkdirs()

        val deckHtml = File(deckHtmlPath).readText()
        val sections = HtmlSectionParser.extractTopLevelSections(slidesMarkup(deckHtml))
        if (sections.isEmpty()) {
            throw CapturingException("No <section> found in deck '$deckHtmlPath': nothing to animate")
        }

        val plan = RemotionPlanner.plan(
            sections = sections,
            headHtml = headMarkup(deckHtml),
            slideDurations = slideDurations,
            outputDir = outputDir,
            viewportWidth = viewportWidth,
            viewportHeight = viewportHeight,
            fps = fps,
            containerExtension = outputExtension(),
        )

        RemotionTemplate.materialiseInto(projectDir)

        // Manim renders one animation per slide; Remotion can only load them
        // through its public directory, so they are staged there and referenced
        // by name. A raw <video> in the injected markup would not be frame-accurate.
        val assets = stageManimAssets(plan, projectDir)
        plan.propsFile.writeText(RemotionPlanner.toPropsJson(plan, assets))

        // Décoder une vidéo par diapo sature le compositeur : au-delà de deux
        // frames en parallèle il meurt sur SIGTERM et le rendu échoue. Mesuré :
        // 4 -> plantage vers la 237e image, 2 -> stable.
        val effectiveConcurrency =
            if (assets.isNotEmpty()) concurrency.coerceAtMost(VIDEO_MAX_CONCURRENCY) else concurrency
        if (effectiveConcurrency != concurrency) {
            logger.lifecycle(
                "  Remotion: concurrency ramenée de {} à {} (les diapos portent des vidéos)",
                concurrency, effectiveConcurrency
            )
        }
        logger.lifecycle(
            "  Remotion: {} slides, {} frames at {} fps, concurrency {}",
            plan.size, plan.totalFrames, plan.fps, effectiveConcurrency
        )

        val argv = RemotionPlanner.renderArgs(plan, projectDir, nodeExecutablePath, effectiveConcurrency, CODEC)
        val logFile = File(outputDir, "remotion-render.log")
        val exitCode = ProcessBuilder(argv)
            .directory(projectDir)
            .redirectErrorStream(true)
            .redirectOutput(logFile)
            .start()
            .waitFor()

        if (exitCode != 0) {
            val tail = logFile.takeIf { it.exists() }
                ?.readLines()
                ?.takeLast(20)
                ?.joinToString(System.lineSeparator())
                .orEmpty()
            throw CapturingException("Remotion render failed (exit $exitCode): $tail")
        }
        if (!plan.finalWebm.exists() || plan.finalWebm.length() == 0L) {
            throw CapturingException("Remotion render reported success but produced no video")
        }
        logFile.delete()
        logger.lifecycle("  Remotion: {} → {}", plan.size, plan.finalWebm.name)
    }

    override fun close() {
        availabilityProbe = null
    }

    /**
     * Copies the Manim animation of each slide into the Remotion public
     * directory and returns, per slide index, the file name to reference.
     *
     * Slides without a `data-manim` attribute simply get no animation.
     */
    private fun stageManimAssets(plan: RemotionPlan, projectDir: File): Map<Int, String> {
        val publicDir = File(projectDir, "public").apply { mkdirs() }
        val staged = mutableMapOf<Int, String>()
        plan.slides.forEach { slide ->
            val name = MANIM_ATTR.find(slide.html)?.groupValues?.get(1) ?: return@forEach
            val source = manimSources.map { File(it, name) }.firstOrNull { it.isFile }
            if (source == null) {
                logger.warn("  Remotion: animation '{}' introuvable pour la diapo {}", name, slide.index + 1)
                return@forEach
            }
            val target = File(publicDir, name)
            if (!target.exists() || target.length() != source.length()) {
                source.copyTo(target, overwrite = true)
            }
            staged[slide.index] = name
        }
        return staged
    }

    companion object {
        /** Video codec handed to the render script. */
        internal const val CODEC: String = "h264"

        /** Frames rendered in parallel when slides carry a video. */
        internal const val VIDEO_MAX_CONCURRENCY: Int = 2

        private val MANIM_ATTR = Regex("""data-manim="([^"]+)"""")

        private val SLIDES_REGEX = Regex("""(?s)<div class="slides">(.*)</div>""")
        private val HEAD_REGEX = Regex("""(?s)<head>.*?</head>""")

        /**
         * Extracts the markup holding the slides.
         *
         * Greedy on purpose: the non-greedy form stops at the first nested
         * `</div>`, which silently truncates any deck whose slides contain a
         * `<div>` — the trailing `</div>` of the container is the last one.
         */
        internal fun slidesMarkup(deckHtml: String): String =
            SLIDES_REGEX.find(deckHtml)?.groupValues?.get(1) ?: deckHtml

        /** Extracts the deck `<head>` so slides keep their own stylesheet. */
        internal fun headMarkup(deckHtml: String): String =
            HEAD_REGEX.find(deckHtml)?.value.orEmpty()
    }
}
