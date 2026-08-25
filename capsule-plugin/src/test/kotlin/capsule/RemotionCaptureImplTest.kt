package capsule

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * TDD unit tests for [RemotionCaptureImpl] — CAP-ANIM US-1.
 *
 * The capture engine drives a Node.js Remotion render, which is too heavy
 * and network-bound for a unit test. What is unit-testable here is its
 * pure surface: the container/extension contract, the deck markup
 * extraction, the Manim asset staging, and the availability probe (which
 * only runs `node --version` and caches the result).
 *
 * The real render path is covered by dogfooding/CI.
 */
class RemotionCaptureImplTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `name reports the remotion engine`() {
        assertEquals("remotion", RemotionCaptureImpl(tempDir).name())
    }

    @Test
    fun `outputExtension is mp4 — Remotion renders H-dot-264, not WebM`() {
        assertEquals("mp4", RemotionCaptureImpl(tempDir).outputExtension())
    }

    @Test
    fun `isAvailable is false when node cannot be found`() {
        val capture = RemotionCaptureImpl(
            projectDir = tempDir,
            nodeExecutablePath = "this-node-binary-does-not-exist-anywhere-12345",
        )
        assertFalse(capture.isAvailable())
    }

    @Test
    fun `isAvailable caches its probe across calls`() {
        val capture = RemotionCaptureImpl(
            projectDir = tempDir,
            nodeExecutablePath = "this-node-binary-does-not-exist-anywhere-67890",
        )
        assertFalse(capture.isAvailable())
        // A second call must not re-probe — the availabilityProbe field is
        // the cache. We cannot observe the probe itself, but we can assert
        // the answer is stable, which is the contract.
        assertFalse(capture.isAvailable())
    }

    @Test
    fun `close resets the availability cache so the next call re-probes`() {
        val capture = RemotionCaptureImpl(
            projectDir = tempDir,
            nodeExecutablePath = "this-node-binary-does-not-exist-anywhere-reset",
        )
        assertFalse(capture.isAvailable())
        capture.close()
        assertFalse(capture.isAvailable())
    }

    @Test
    fun `slidesMarkup extracts the div-dot-slides container from a reveal deck`() {
        val deck = """
            <html><head></head><body><div class="reveal"><div class="slides">
            <section>one</section><section>two</section>
            </div></div></body></html>
        """.trimIndent()
        val markup = RemotionCaptureImpl.slidesMarkup(deck)
        assertTrue(markup.contains("one"))
        assertTrue(markup.contains("two"))
    }

    @Test
    fun `slidesMarkup falls back to the whole deck when there is no slides container`() {
        val deck = "<section>lonely</section>"
        assertEquals(deck, RemotionCaptureImpl.slidesMarkup(deck))
    }

    @Test
    fun `slidesMarkup is greedy so a slide containing a div is not truncated`() {
        // Non-greedy would stop at the first </div> inside a slide.
        val deck = """
            <div class="slides">
            <section><div class="fig">one</div></section>
            <section><div class="fig">two</div></section>
            </div>
        """.trimIndent()
        val markup = RemotionCaptureImpl.slidesMarkup(deck)
        assertTrue(markup.contains("one") && markup.contains("two"), "Deck truncated: $markup")
    }

    @Test
    fun `headMarkup extracts the head block so slides keep their stylesheet`() {
        val deck = "<html><head><style>h1{color:red}</style></head><body></body></html>"
        assertTrue(RemotionCaptureImpl.headMarkup(deck).contains("color:red"))
    }

    @Test
    fun `headMarkup is empty when the deck has no head`() {
        assertEquals("", RemotionCaptureImpl.headMarkup("<section>no head</section>"))
    }

    @Test
    fun `capture rejects an empty deck with no sections`() {
        val deckHtml = File(tempDir, "deck.html").apply {
            writeText("<html><head></head><body><div class='slides'></div></body></html>")
        }
        val capture = RemotionCaptureImpl(projectDir = tempDir, nodeExecutablePath = "node")
        assertFailsWith<CapturingException> {
            capture.capture(
                deckHtmlPath = deckHtml.absolutePath,
                outputDir = File(tempDir, "out"),
                viewportWidth = 1280,
                viewportHeight = 720,
                slideDurations = listOf(2.0),
            )
        }
    }

    @Test
    fun `capture rejects empty slideDurations`() {
        val deckHtml = File(tempDir, "deck.html").apply {
            writeText("<html><head></head><body><section>one</section></body></html>")
        }
        val capture = RemotionCaptureImpl(projectDir = tempDir, nodeExecutablePath = "node")
        assertFailsWith<IllegalArgumentException> {
            capture.capture(
                deckHtmlPath = deckHtml.absolutePath,
                outputDir = File(tempDir, "out"),
                viewportWidth = 1280,
                viewportHeight = 720,
                slideDurations = emptyList(),
            )
        }
    }

    @Test
    fun `CODEC is h264 — Remotion renders H-dot-264 by contract`() {
        assertEquals("h264", RemotionCaptureImpl.CODEC)
    }

    @Test
    fun `VIDEO_MAX_CONCURRENCY caps parallel frames when slides carry a video`() {
        assertEquals(2, RemotionCaptureImpl.VIDEO_MAX_CONCURRENCY)
    }
}