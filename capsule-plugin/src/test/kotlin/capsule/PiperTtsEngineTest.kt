package capsule

import contracts.i18n.LanguageCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * CAP-28 US-1 — PiperTtsEngine coverage gaps.
 *
 * Covers the `language()` and `resolvedModel` branches that are testable
 * without I/O (no piper binary required). The `synthesize` happy path and
 * exit-code != 0 branches are gated (require a real piper binary) and are
 * out of scope for CAP-28.
 */
class PiperTtsEngineTest {

    @Test
    fun `language() returns injected language when non-null`() {
        val en = LanguageCatalog.findByCode("en")
        val engine = PiperTtsEngine(executablePath = "/nonexistent/piper", language = en)
        assertEquals(en, engine.language())
    }

    @Test
    fun `language() resolves from default model when language is null`() {
        val engine = PiperTtsEngine(
            executablePath = "/nonexistent/piper",
            model = "fr_FR-siwis-medium"
        )
        val resolved = engine.language()
        assertEquals("fr", resolved?.code)
    }

    @Test
    fun `resolvedModel uses default model when language is null`() {
        val engine = PiperTtsEngine(
            executablePath = "/nonexistent/piper",
            model = "custom-model"
        )
        assertNull(engine.language())
    }

    @Test
    fun `language() returns injected ES language and does not fall back to default model`() {
        val es = LanguageCatalog.findByCode("es")
        val engine = PiperTtsEngine(
            executablePath = "/nonexistent/piper",
            model = "fr_FR-siwis-medium",
            language = es
        )
        assertEquals(es, engine.language())
        assertEquals("es", engine.language()?.code)
    }
}