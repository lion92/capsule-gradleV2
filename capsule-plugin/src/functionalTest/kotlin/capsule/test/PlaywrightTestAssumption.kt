package capsule.test

import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * JUnit5 helper for tests that spin a real Playwright/Chromium browser.
 *
 * Call [assumePlaywrightAvailable] in a `@BeforeAll` or at the top of a test
 * method. When the guard says "skip" (default — no `-PrunPlaywrightTests` and
 * no `CI=true`), the test is marked as skipped via `assumeTrue` (not failed).
 *
 * Usage in functional tests:
 * ```
 * @Test
 * fun `my test that launches Chromium`() {
 *     assumePlaywrightAvailable()
 *     // ... test body ...
 * }
 * ```
 *
 * Usage with system property override (for ad-hoc local runs):
 * ```
 * ./gradlew functionalTest -PrunPlaywrightTests
 * ```
 */
object PlaywrightTestAssumption {

    fun assumePlaywrightAvailable() {
        val hasProperty = System.getProperty("runPlaywrightTests") != null
        val isCi = System.getenv("CI") == "true"
        val shouldRun = hasProperty || isCi
        assumeTrue(shouldRun) {
            "Playwright test skipped (pass -PrunPlaywrightTests or set CI=true to enable)"
        }
    }
}