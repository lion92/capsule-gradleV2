package capsule.ci

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [PlaywrightTestGuard] — pure decision function, no I/O.
 *
 * Pattern mirror [CucumberTestGuardTest] (CR-10).
 */
class PlaywrightTestGuardTest {

    @Test
    fun `shouldRun returns true when runPlaywrightTests property is set`() {
        val guard = PlaywrightTestGuard(hasRunPlaywrightTestsProperty = true, isCi = false)
        assertTrue(guard.shouldRun())
    }

    @Test
    fun `shouldRun returns true when CI env var is active`() {
        val guard = PlaywrightTestGuard(hasRunPlaywrightTestsProperty = false, isCi = true)
        assertTrue(guard.shouldRun())
    }

    @Test
    fun `shouldRun returns true when both property and CI are active`() {
        val guard = PlaywrightTestGuard(hasRunPlaywrightTestsProperty = true, isCi = true)
        assertTrue(guard.shouldRun())
    }

    @Test
    fun `shouldRun returns false when neither property nor CI is active`() {
        val guard = PlaywrightTestGuard(hasRunPlaywrightTestsProperty = false, isCi = false)
        assertFalse(guard.shouldRun())
    }

    @Test
    fun `shouldSkip is negation of shouldRun`() {
        assertFalse(PlaywrightTestGuard(true, false).shouldSkip())
        assertTrue(PlaywrightTestGuard(false, false).shouldSkip())
    }

    @Test
    fun `skipReason is blank when shouldRun`() {
        val guard = PlaywrightTestGuard(hasRunPlaywrightTestsProperty = true, isCi = false)
        assertEquals("", guard.skipReason())
    }

    @Test
    fun `skipReason mentions runPlaywrightTests when skipped`() {
        val guard = PlaywrightTestGuard(hasRunPlaywrightTestsProperty = false, isCi = false)
        val reason = guard.skipReason()
        assertTrue(reason.contains("runPlaywrightTests"))
        assertTrue(reason.contains("CI=true"))
    }
}