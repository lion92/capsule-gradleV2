package capsule.ci

/**
 * Guard deciding whether tests that spin a real Playwright/Chromium browser
 * should run.
 *
 * Playwright tests are expensive (Chromium headless launch + video capture,
 * 15-30 min for the full suite) and require Chromium to be installed. By
 * default these tests are skipped. They run only when:
 * - the user explicitly opts in via `-PrunPlaywrightTests`, or
 * - the build runs on a CI server (`CI=true`).
 *
 * This object is a pure decision function with no Gradle coupling so it can
 * be unit-tested in isolation. Pattern mirror [CucumberTestGuard] (CR-10).
 *
 * Tests that use NoOp engines (NoOpPlaywrightCapture, noop TTS) do NOT need
 * this guard — only tests that launch a real Chromium browser.
 */
data class PlaywrightTestGuard(
    val hasRunPlaywrightTestsProperty: Boolean,
    val isCi: Boolean
) {
    fun shouldRun(): Boolean = hasRunPlaywrightTestsProperty || isCi

    fun shouldSkip(): Boolean = !shouldRun()

    fun skipReason(): String =
        if (shouldRun()) ""
        else "Playwright test skipped (pass -PrunPlaywrightTests or set CI=true to enable)"
}