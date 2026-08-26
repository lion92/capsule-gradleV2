package capsule.chapters

import java.io.File

/**
 * Renders intro/outro card HTML files for chapter markers.
 *
 * The cards are standalone HTML documents with inline CSS,
 * designed to be captured as PNG images by Playwright (US-3).
 *
 * Factory pattern (10th):
 * - noop → [NoOpCardRenderer] (writes empty HTML)
 * - available → [CardRendererImpl]
 */
interface CardRenderer {
    fun renderIntroCard(introText: String, outputFile: File): Boolean
    fun renderOutroCard(outroText: String, outputFile: File): Boolean
    fun isAvailable(): Boolean
    fun name(): String
}

/**
 * Pure command object — builds HTML content for intro/outro cards.
 * No I/O, no Gradle dependencies, fully unit-testable.
 */
object CardRendererCommand {

    fun buildCardHtml(text: String, type: String): String {
        val escapedText = escapeHtml(text)
        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"en\">")
            appendLine("<head>")
            appendLine("  <meta charset=\"UTF-8\">")
            appendLine("  <meta name=\"viewport\" content=\"width=1920, height=1080\">")
            appendLine("  <title>$escapedText</title>")
            appendLine("  <style>")
            appendLine(CARD_CSS)
            appendLine("  </style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("  <div class=\"card card--$type\">")
            appendLine("    <div class=\"card__label\">$type</div>")
            appendLine("    <h1 class=\"card__title\">$escapedText</h1>")
            appendLine("  </div>")
            appendLine("</body>")
            appendLine("</html>")
        }.trimIndent()
    }

    private fun escapeHtml(s: String): String {
        return s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private const val CARD_CSS = """
      * { margin: 0; padding: 0; box-sizing: border-box; }
      body {
        width: 1920px; height: 1080px;
        display: flex; align-items: center; justify-content: center;
        background: #0d1117; color: #f0f6fc;
        font-family: 'Segoe UI', system-ui, -apple-system, sans-serif;
      }
      .card {
        text-align: center; padding: 80px 120px;
        border: 2px solid #30363d; border-radius: 16px;
        background: #161b22; max-width: 1400px;
      }
      .card__label {
        font-size: 18px; text-transform: uppercase; letter-spacing: 4px;
        color: #8b949e; margin-bottom: 32px;
      }
      .card--intro .card__label { color: #58a6ff; }
      .card--outro .card__label { color: #f78166; }
      .card__title {
        font-size: 64px; font-weight: 600; line-height: 1.3;
        color: #f0f6fc;
      }
"""
}

/**
 * Real implementation — writes intro/outro card HTML to disk.
 */
class CardRendererImpl : CardRenderer {
    override fun renderIntroCard(introText: String, outputFile: File): Boolean {
        if (introText.isBlank()) return false
        val html = CardRendererCommand.buildCardHtml(introText, "intro")
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(html)
        return true
    }

    override fun renderOutroCard(outroText: String, outputFile: File): Boolean {
        if (outroText.isBlank()) return false
        val html = CardRendererCommand.buildCardHtml(outroText, "outro")
        outputFile.parentFile?.mkdirs()
        outputFile.writeText(html)
        return true
    }

    override fun isAvailable(): Boolean = true

    override fun name(): String = "CardRendererImpl"
}

/**
 * No-op implementation — returns false (degraded, no card rendered).
 * Used when chapters are disabled or intro/outro text is blank.
 */
class NoOpCardRenderer : CardRenderer {
    override fun renderIntroCard(introText: String, outputFile: File): Boolean {
        outputFile.parentFile?.mkdirs()
        outputFile.writeText("<!-- no-op -->")
        return false
    }

    override fun renderOutroCard(outroText: String, outputFile: File): Boolean {
        outputFile.parentFile?.mkdirs()
        outputFile.writeText("<!-- no-op -->")
        return false
    }

    override fun isAvailable(): Boolean = false

    override fun name(): String = "NoOpCardRenderer"
}
