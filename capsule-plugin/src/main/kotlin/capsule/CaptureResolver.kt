package capsule

/**
 * Resolves the capture engine based on [CaptureStrategy] (CAP-CR3-3 US-2).
 *
 * Pure object — no I/O, no Gradle dependencies, fully testable.
 *
 * Dispatches on [CaptureStrategy] to build the appropriate
 * [PlaywrightCapture] implementation. When the selected engine is not
 * available and strict mode is disabled, falls back to
 * [NoOpPlaywrightCapture] (backward compatibility). When strict mode is
 * enabled and the engine is unavailable, [StrictModeGuard] throws an
 * [IllegalStateException].
 *
 * The actual engine construction is delegated to caller-supplied
 * factories ([PlaywrightFactory]/[ScreenshotFactory]) so that tests can
 * inject fakes without touching Playwright/FFmpeg.
 */
object CaptureResolver {

    /** Factory for [PlaywrightCaptureImpl] (real Playwright recording). */
    fun interface PlaywrightFactory {
        fun create(): PlaywrightCapture
    }

    /** Factory for [ScreenshotCaptureImpl] (PNG + FFmpeg per slide). */
    fun interface ScreenshotFactory {
        fun create(): PlaywrightCapture
    }

    /** Factory for [RemotionCaptureImpl] (frame-by-frame animated render). */
    fun interface RemotionFactory {
        fun create(): PlaywrightCapture
    }

    /**
     * Resolves the capture engine for the given strategy.
     *
     * @param strategy         the capture strategy (PLAYWRIGHT or SCREENSHOT)
     * @param strict           whether strict mode is enabled
     * @param playwrightFactory builds a PlaywrightCaptureImpl when strategy=PLAYWRIGHT
     * @param screenshotFactory builds a ScreenshotCaptureImpl when strategy=SCREENSHOT
     * @param remotionFactory  builds a RemotionCaptureImpl when strategy=REMOTION
     * @param noOpCapture      the NoOp fallback (built once, reused)
     * @param enginePath       the resolved executable/path for the strict-mode
     *        error message (blank if N/A)
     * @return the resolved [PlaywrightCapture] (real impl or NoOp fallback)
     * @throws IllegalStateException if strict mode is enabled and the
     *         selected engine is not available
     */
    fun resolve(
        strategy: CaptureStrategy,
        strict: Boolean,
        playwrightFactory: PlaywrightFactory,
        screenshotFactory: ScreenshotFactory,
        noOpCapture: PlaywrightCapture,
        enginePath: String = "",
        remotionFactory: RemotionFactory = RemotionFactory { noOpCapture },
    ): PlaywrightCapture {
        val engineName = when (strategy) {
            CaptureStrategy.PLAYWRIGHT -> "playwright"
            CaptureStrategy.SCREENSHOT -> "screenshot"
            CaptureStrategy.REMOTION -> "remotion"
        }
        val impl = when (strategy) {
            CaptureStrategy.PLAYWRIGHT -> playwrightFactory.create()
            CaptureStrategy.SCREENSHOT -> screenshotFactory.create()
            CaptureStrategy.REMOTION -> remotionFactory.create()
        }
        return if (impl.isAvailable()) {
            impl
        } else {
            StrictModeGuard.requireAvailable(
                strict = strict,
                engineName = engineName,
                isAvailable = false,
                path = enginePath
            )
            noOpCapture
        }
    }
}