package capsule

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import org.gradle.api.logging.Logging
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors

interface PlaywrightCapture {
    fun capture(deckHtmlPath: String, outputDir: File, viewportWidth: Int, viewportHeight: Int, slideDurations: List<Double>)
    fun isAvailable(): Boolean
    fun name(): String
    fun close()

    /**
     * Container the engine writes, without the dot.
     *
     * The browser-driven engines produce WebM; Remotion produces MP4, because an
     * H.264 stream cannot go into a WebM container and H.264 encodes far faster
     * than VP8 (measured: 13.1 against 9.1 frames per second on the same deck).
     * The downstream steps derive their file names and audio codec from this
     * rather than assuming WebM.
     */
    fun outputExtension(): String = "webm"
}

/**
 * Chromium launch options shared by every capture engine.
 *
 * `--no-sandbox` is not optional here: Gradle builds routinely run as root in
 * Docker images and CI runners, and a root Chromium cannot initialise its
 * sandbox — it hangs on the DevTools handshake instead of failing, which
 * surfaces much later as a capture timeout. `--disable-dev-shm-usage` covers
 * containers whose /dev/shm is too small for Chromium's default shared memory,
 * and `--disable-gpu` avoids waiting on a GPU stack that headless rendering
 * does not need.
 */
internal fun headlessChromiumOptions(): BrowserType.LaunchOptions =
    BrowserType.LaunchOptions()
        .setHeadless(true)
        .setArgs(listOf("--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu"))

class PlaywrightCaptureImpl(
    private val timeout: Double = 120_000.0,
    private val transitionPause: Double = 500.0,
    private val endMargin: Double = 2000.0,
    private val defaultSlideDuration: Double = 5.0
) : PlaywrightCapture {

    private val logger = Logging.getLogger(PlaywrightCaptureImpl::class.java)

    private var playwright: Playwright? = null
    private var browser: Browser? = null
    private var context: BrowserContext? = null
    private var page: Page? = null

    // Probing costs a full browser launch: do it once per instance, not once per
    // slide. Parallel capture used to re-resolve an engine for every slide, i.e.
    // two launches per slide instead of one per worker thread.
    private var availabilityProbe: Boolean? = null

    override fun isAvailable(): Boolean = availabilityProbe ?: (
        try {
            Playwright.create().use { pw ->
                pw.chromium().launch(headlessChromiumOptions()).use { it.close() }
            }
            true
        } catch (e: Exception) {
            false
        }
        ).also { availabilityProbe = it }

    override fun name(): String = "playwright-java"

    override fun capture(
        deckHtmlPath: String,
        outputDir: File,
        viewportWidth: Int,
        viewportHeight: Int,
        slideDurations: List<Double>
    ) {
        val slideCount = slideDurations.size
        playwright = Playwright.create()
        browser = playwright!!.chromium().launch(headlessChromiumOptions())
        context = browser!!.newContext(
            Browser.NewContextOptions()
                .setViewportSize(viewportWidth, viewportHeight)
                .setRecordVideoDir(Paths.get(outputDir.absolutePath))
                .setRecordVideoSize(viewportWidth, viewportHeight)
        )
        page = context!!.newPage()
        page!!.setDefaultNavigationTimeout(timeout)
        page!!.setDefaultTimeout(timeout)

        val absolutePath = File(deckHtmlPath).absolutePath
        page!!.navigate("file://$absolutePath")

        page!!.waitForSelector(".reveal",
            Page.WaitForSelectorOptions().setTimeout(timeout))

        page!!.waitForTimeout(2000.0)

        val audioIds = page!!.evaluate("Array.from(document.querySelectorAll('audio')).map(a => a.id)") as? List<*> ?: listOf<Any>()
        val hasAudioElements = audioIds.isNotEmpty()

        logger.lifecycle("  Playwright: hasAudio={} audioCount={} slides={}", hasAudioElements, audioIds.size, slideCount)

        for (i in 0 until slideCount) {
            val slideMs = (slideDurations[i] * 1000).toLong()
            // Always use waitForTimeout: audio doesn't play in headless Chromium.
            // FFmpeg will mix the audio tracks onto the captured video later.
            page!!.waitForTimeout(slideMs.toDouble())
            page!!.waitForTimeout(transitionPause)
            if (i < slideCount - 1) {
                page!!.evaluate("typeof Reveal !== 'undefined' && Reveal.next()")
            }
        }

        page!!.waitForTimeout(endMargin)
    }

    override fun close() {
        context?.close()
        browser?.close()
        playwright?.close()
        context = null
        browser = null
        playwright = null
        page = null
    }
}

class NoOpPlaywrightCapture : PlaywrightCapture {
    override fun isAvailable(): Boolean = true
    override fun name(): String = "noop-playwright"

    override fun capture(
        deckHtmlPath: String,
        outputDir: File,
        viewportWidth: Int,
        viewportHeight: Int,
        slideDurations: List<Double>
    ) {
        outputDir.mkdirs()
        outputDir.resolve("capsule.webm").writeBytes(MINIMAL_WEBM)
    }

    override fun close() {}

    companion object {
        private val MINIMAL_WEBM: ByteArray = "1a45dfa301000000000000001f4286810142f7810142f2810442f381084282847765626d42878104428581021853806701000000000000001e1549a96601000000000000000d2ad7b1830f4240448984000000000000000000"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

class CapturingException(message: String) : RuntimeException(message)

/**
 * Screenshot-based capture: takes a PNG screenshot of each slide then uses FFmpeg
 * to produce a WebM of the exact audio duration per slide, and concatenates the
 * per-slide WebMs into a single `capsule.webm`. No real-time recording — orders
 * of magnitude faster and more reliable than Playwright video recording.
 *
 * Multi-slide support (CAP-CR3-3 US-3): iterates over [slideDurations], capturing
 * `slide-N.png` per slide, navigating with `Reveal.next()` between slides, then
 * converts each PNG to a `slide-N.webm` of the exact slide duration, and finally
 * concatenates all per-slide WebMs via the FFmpeg concat demuxer.
 */
class ScreenshotCaptureImpl(
    private val timeout: Double = 120_000.0,
    private val ffmpegPath: String = "ffmpeg",
    private val encodeParallelism: Int = 4,
    private val previewOnly: Boolean = false,
) : PlaywrightCapture {

    private val logger = Logging.getLogger(ScreenshotCaptureImpl::class.java)

    private var playwright: Playwright? = null
    private var browser: Browser? = null
    private var availabilityProbe: Boolean? = null

    /**
     * Launches the browser on first use and keeps it for the lifetime of the
     * instance. Each `capture()` call used to spin up its own Playwright driver
     * and Chromium, and overwrite the previous ones without closing them — with
     * parallel capture that meant one launch per slide, plus one more for the
     * availability probe.
     */
    private fun ensureBrowser(): Browser {
        val current = browser
        if (current != null && current.isConnected) return current
        val pw = playwright ?: Playwright.create().also { playwright = it }
        return pw.chromium()
            .launch(headlessChromiumOptions())
            .also { browser = it }
    }

    override fun isAvailable(): Boolean = availabilityProbe ?: (
        try {
            ensureBrowser()
            if (!isFfmpegAvailable()) {
                logger.info("Screenshot capture unavailable: ffmpeg '{}' not executable", ffmpegPath)
                false
            } else {
                true
            }
        } catch (e: Exception) {
            logger.info("Screenshot capture unavailable: {}", e.message)
            false
        }
        ).also { availabilityProbe = it }

    private fun isFfmpegAvailable(): Boolean = ProcessRunner.probe(ffmpegPath, "-version")

    override fun name(): String = "screenshot+ffmpeg"

    override fun capture(
        deckHtmlPath: String,
        outputDir: File,
        viewportWidth: Int,
        viewportHeight: Int,
        slideDurations: List<Double>
    ) {
        require(slideDurations.isNotEmpty()) { "slideDurations must not be empty" }
        val plan = ScreenshotPlanner.plan(outputDir, slideDurations)
        outputDir.mkdirs()

        shootSlides(plan, File(deckHtmlPath).absolutePath, viewportWidth, viewportHeight)

        if (previewOnly) {
            logger.lifecycle("  ScreenshotCapture PREVIEW: {} PNGs captured -> {}", plan.size, outputDir.absolutePath)
            return
        }

        encodeSlides(plan, viewportWidth, viewportHeight)
        concatSlides(plan)

        logger.lifecycle("  ScreenshotCapture: {} slides -> {}", plan.size, plan.finalWebm.name)
    }

    /** Navigates the deck once and takes one PNG per slide. Inherently sequential. */
    private fun shootSlides(
        plan: ScreenshotCapturePlan,
        absoluteDeckPath: String,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        val page = ensureBrowser().newPage(
            Browser.NewPageOptions().setViewportSize(viewportWidth, viewportHeight)
        )
        try {
            page.setDefaultNavigationTimeout(timeout)
            page.setDefaultTimeout(timeout)
            page.navigate("file://$absoluteDeckPath")
            page.waitForSelector(
                ".reveal, section, body",
                Page.WaitForSelectorOptions().setTimeout(timeout)
            )
            page.waitForTimeout(800.0)

            for (entry in plan.slides) {
                page.screenshot(Page.ScreenshotOptions().setPath(entry.pngFile.toPath()))
                if (entry.index < plan.slides.lastIndex) {
                    page.evaluate("typeof Reveal !== 'undefined' && Reveal.next()")
                    page.waitForTimeout(300.0)
                }
            }
        } finally {
            page.close()
        }
    }

    /**
     * PNG → WebM, one FFmpeg process per slide. Pure CPU work with no shared
     * state, so it runs on a bounded pool: this is the step that dominates the
     * capture, and unlike parallel browsers it cannot destabilise the driver.
     */
    private fun encodeSlides(plan: ScreenshotCapturePlan, viewportWidth: Int, viewportHeight: Int) {
        val parallelism = encodeParallelism.coerceIn(1, plan.slides.size)
        if (parallelism == 1) {
            plan.slides.forEach { encodeSlide(it, viewportWidth, viewportHeight) }
            return
        }
        val pool = Executors.newFixedThreadPool(parallelism)
        try {
            val futures = plan.slides.map { entry ->
                pool.submit { encodeSlide(entry, viewportWidth, viewportHeight) }
            }
            futures.forEach { future ->
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    throw e.cause as? CapturingException
                        ?: CapturingException("FFmpeg PNG to WebM failed: ${e.cause?.message}")
                }
            }
        } finally {
            pool.shutdownNow()
        }
    }

    private fun encodeSlide(entry: ScreenshotSlideEntry, viewportWidth: Int, viewportHeight: Int) {
        runFfmpeg(
            ScreenshotPlanner.ffmpegPngToWebmArgs(entry, viewportWidth, viewportHeight, ffmpegPath),
            File(entry.webmFile.parentFile, "${entry.webmFile.nameWithoutExtension}-ffmpeg.log"),
        ) { log -> "FFmpeg PNG to WebM failed (slide ${entry.index}): $log" }
    }

    private fun concatSlides(plan: ScreenshotCapturePlan) {
        plan.concatListFile.writeText(ScreenshotPlanner.renderConcatList(plan))
        runFfmpeg(
            ScreenshotPlanner.ffmpegConcatArgs(plan, ffmpegPath),
            File(plan.finalWebm.parentFile, "concat-ffmpeg.log"),
        ) { log -> "FFmpeg concat failed: $log" }
    }

    /**
     * Runs FFmpeg with its output redirected to [logFile] rather than to a pipe.
     * FFmpeg is chatty and a pipe nobody drains fills up at 64 KB, which
     * deadlocks the process instead of failing it.
     */
    private fun runFfmpeg(argv: List<String>, logFile: File, message: (String) -> String) {
        logFile.parentFile?.mkdirs()
        val result = ProcessRunner.run(argv, logFile = logFile)
        if (!result.isSuccess) {
            throw CapturingException(message(result.tail(12)))
        }
        logFile.delete()
    }

    override fun close() {
        browser?.close()
        playwright?.close()
        browser = null
        playwright = null
        availabilityProbe = null
    }
}
