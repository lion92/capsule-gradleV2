package capsule.chapters

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import java.io.File
import org.junit.jupiter.api.Test

class CardRendererTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `CardRendererCommand buildCardHtml for intro contains title text`() {
        val html = CardRendererCommand.buildCardHtml("Welcome", "intro")
        assertTrue(html.contains("Welcome"), "HTML should contain the title text")
        assertTrue(html.contains("card--intro"), "HTML should have intro CSS class")
        assertTrue(html.startsWith("<!DOCTYPE html>"), "HTML should start with DOCTYPE")
    }

    @Test
    fun `CardRendererCommand buildCardHtml for outro contains title text`() {
        val html = CardRendererCommand.buildCardHtml("Goodbye", "outro")
        assertTrue(html.contains("Goodbye"), "HTML should contain the title text")
        assertTrue(html.contains("card--outro"), "HTML should have outro CSS class")
    }

    @Test
    fun `CardRendererCommand buildCardHtml escapes HTML entities`() {
        val html = CardRendererCommand.buildCardHtml("A <b>bold</b> & \"quoted\" title", "intro")
        assertTrue(html.contains("A &lt;b&gt;bold&lt;/b&gt; &amp; &quot;quoted&quot; title"),
            "HTML entities should be escaped")
        assertFalse(html.contains("<b>bold</b>"), "Raw HTML tags should not appear")
    }

    @Test
    fun `CardRendererCommand buildCardHtml has 1920x1080 viewport`() {
        val html = CardRendererCommand.buildCardHtml("Test", "intro")
        assertTrue(html.contains("width=1920"), "Viewport width should be 1920")
        assertTrue(html.contains("height=1080"), "Viewport height should be 1080")
    }

    @Test
    fun `CardRendererCommand buildCardHtml has inline CSS`() {
        val html = CardRendererCommand.buildCardHtml("Test", "intro")
        assertTrue(html.contains("<style>"), "HTML should contain style tag")
        assertTrue(html.contains("font-family"), "HTML should contain font-family CSS")
    }

    @Test
    fun `CardRendererImpl renderIntroCard writes HTML file`() {
        val impl = CardRendererImpl()
        val outputFile = File(tempDir, "intro.html")

        val result = impl.renderIntroCard("Welcome to the Course", outputFile)

        assertTrue(result, "renderIntroCard should return true")
        assertTrue(outputFile.exists(), "Output file should be created")
        val content = outputFile.readText()
        assertTrue(content.contains("Welcome to the Course"), "File should contain title")
        assertTrue(content.contains("card--intro"), "File should have intro class")
    }

    @Test
    fun `CardRendererImpl renderOutroCard writes HTML file`() {
        val impl = CardRendererImpl()
        val outputFile = File(tempDir, "outro.html")

        val result = impl.renderOutroCard("Thank you for watching", outputFile)

        assertTrue(result, "renderOutroCard should return true")
        assertTrue(outputFile.exists(), "Output file should be created")
        val content = outputFile.readText()
        assertTrue(content.contains("Thank you for watching"), "File should contain title")
        assertTrue(content.contains("card--outro"), "File should have outro class")
    }

    @Test
    fun `CardRendererImpl creates parent directories`() {
        val impl = CardRendererImpl()
        val outputFile = File(tempDir, "subdir/deep/intro.html")

        impl.renderIntroCard("Test", outputFile)

        assertTrue(outputFile.parentFile?.exists() == true, "Parent dirs should be created")
        assertTrue(outputFile.exists(), "File should be created")
    }

    @Test
    fun `CardRendererImpl renderIntroCard returns false for blank text`() {
        val impl = CardRendererImpl()
        val outputFile = File(tempDir, "blank-intro.html")

        assertFalse(impl.renderIntroCard("", outputFile), "Blank intro should return false")
        assertFalse(impl.renderIntroCard("   ", outputFile), "Whitespace-only intro should return false")
    }

    @Test
    fun `CardRendererImpl renderOutroCard returns false for blank text`() {
        val impl = CardRendererImpl()
        val outputFile = File(tempDir, "blank-outro.html")

        assertFalse(impl.renderOutroCard("", outputFile), "Blank outro should return false")
        assertFalse(impl.renderOutroCard("   ", outputFile), "Whitespace-only outro should return false")
    }

    @Test
    fun `CardRendererImpl isAvailable returns true`() {
        assertTrue(CardRendererImpl().isAvailable(), "Impl should always be available")
    }

    @Test
    fun `CardRendererImpl name is CardRendererImpl`() {
        assertEquals("CardRendererImpl", CardRendererImpl().name())
    }

    @Test
    fun `NoOpCardRenderer renderIntroCard returns false`() {
        val noop = NoOpCardRenderer()
        val outputFile = File(tempDir, "noop-intro.html")

        assertFalse(noop.renderIntroCard("Test", outputFile), "NoOp should return false")
        assertTrue(outputFile.exists(), "File should still be created (no-op placeholder)")
        assertEquals("<!-- no-op -->", outputFile.readText().trim(), "File should contain no-op placeholder")
    }

    @Test
    fun `NoOpCardRenderer renderOutroCard returns false`() {
        val noop = NoOpCardRenderer()
        val outputFile = File(tempDir, "noop-outro.html")

        assertFalse(noop.renderOutroCard("Test", outputFile), "NoOp should return false")
        assertEquals("<!-- no-op -->", outputFile.readText().trim(), "File should contain no-op placeholder")
    }

    @Test
    fun `NoOpCardRenderer isAvailable returns false`() {
        assertFalse(NoOpCardRenderer().isAvailable(), "NoOp should report unavailable")
    }

    @Test
    fun `NoOpCardRenderer name is NoOpCardRenderer`() {
        assertEquals("NoOpCardRenderer", NoOpCardRenderer().name())
    }

    @Test
    fun `CardRendererCommand buildCardHtml with empty text produces valid HTML`() {
        val html = CardRendererCommand.buildCardHtml("", "intro")
        assertTrue(html.startsWith("<!DOCTYPE html>"), "Empty text should still produce valid HTML")
        assertTrue(html.contains("card--intro"), "Empty text should still have intro class")
    }

    @Test
    fun `CardRendererCommand buildCardHtml preserves special characters`() {
        val html = CardRendererCommand.buildCardHtml("Session 1/2: Introduction & Overview", "intro")
        assertTrue(html.contains("Session 1/2: Introduction &amp; Overview"),
            "Ampersand should be escaped in HTML")
    }
}
