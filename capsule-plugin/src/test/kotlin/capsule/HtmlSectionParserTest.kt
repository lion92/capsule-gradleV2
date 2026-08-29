package capsule

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD Baby Step — HtmlSectionParser unit tests.
 *
 * Extracts top-level <section> elements from reveal.js slides HTML,
 * handling nested sections (vertical stacks) by tracking depth.
 */
class HtmlSectionParserTest {

    // ── findTopLevelSectionOpenTags (CR-9: robust HTML parsing) ──

    @Test
    fun `extractTopLevelSections extracts simple flat sections`() {
        val html = """
            <section><h2>Slide 1</h2></section>
            <section><h2>Slide 2</h2></section>
            <section><h2>Slide 3</h2></section>
        """.trimIndent()

        val sections = HtmlSectionParser.extractTopLevelSections(html)
        assertEquals(3, sections.size)
        assertTrue(sections[0].contains("Slide 1"))
        assertTrue(sections[1].contains("Slide 2"))
        assertTrue(sections[2].contains("Slide 3"))
    }

    @Test
    fun `extractTopLevelSections handles nested sections`() {
        val html = """
            <section>
                <section><h2>Sub A</h2></section>
                <section><h2>Sub B</h2></section>
            </section>
            <section><h2>Slide 2</h2></section>
        """.trimIndent()

        val sections = HtmlSectionParser.extractTopLevelSections(html)
        assertEquals(2, sections.size)
        assertTrue(sections[0].contains("Sub A"))
        assertTrue(sections[0].contains("Sub B"))
        assertTrue(sections[1].contains("Slide 2"))
    }

    @Test
    fun `extractTopLevelSections returns empty list for no sections`() {
        val html = "<div><h2>No sections here</h2></div>"
        val sections = HtmlSectionParser.extractTopLevelSections(html)
        assertEquals(0, sections.size)
    }

    @Test
    fun `extractTopLevelSections handles single section`() {
        val html = "<section><h2>Only</h2></section>"
        val sections = HtmlSectionParser.extractTopLevelSections(html)
        assertEquals(1, sections.size)
        assertTrue(sections[0].contains("Only"))
    }

    @Test
    fun `extractTopLevelSections skips self-closing tags`() {
        val html = """
            <section />
            <section><h2>Real</h2></section>
        """.trimIndent()

        val sections = HtmlSectionParser.extractTopLevelSections(html)
        assertEquals(1, sections.size)
        assertTrue(sections[0].contains("Real"))
    }

    @Test
    fun `extractTopLevelSections handles sections with attributes`() {
        val html = """
            <section data-capsule-slide="1" class="intro"><h2>One</h2></section>
            <section id="two" data-background="red"><h2>Two</h2></section>
        """.trimIndent()

        val sections = HtmlSectionParser.extractTopLevelSections(html)
        assertEquals(2, sections.size)
        assertTrue(sections[0].contains("data-capsule-slide=\"1\""))
        assertTrue(sections[1].contains("id=\"two\""))
    }

    // ── findTopLevelSectionOpenTags (CR-9: robust HTML parsing) ──

    @Test
    fun `findTopLevelSectionOpenTags returns flat section open tags`() {
        val html = """
            <section><h2>Slide 1</h2></section>
            <section><h2>Slide 2</h2></section>
        """.trimIndent()

        val tags = HtmlSectionParser.findTopLevelSectionOpenTags(html)
        assertEquals(2, tags.size)
        assertEquals("<section>", tags[0].value)
        assertEquals("<section>", tags[1].value)
    }

    @Test
    fun `findTopLevelSectionOpenTags skips nested section open tags`() {
        val html = """
            <section>
                <section><h2>Sub A</h2></section>
                <section><h2>Sub B</h2></section>
            </section>
            <section><h2>Slide 2</h2></section>
        """.trimIndent()

        val tags = HtmlSectionParser.findTopLevelSectionOpenTags(html)
        assertEquals(2, tags.size)
        assertTrue(tags[0].value.startsWith("<section"))
        assertTrue(tags[1].value.startsWith("<section"))
    }

    @Test
    fun `findTopLevelSectionOpenTags skips self-closing tags`() {
        val html = """
            <section />
            <section><h2>Real</h2></section>
        """.trimIndent()

        val tags = HtmlSectionParser.findTopLevelSectionOpenTags(html)
        assertEquals(1, tags.size)
        assertTrue(tags[0].value.startsWith("<section>"))
    }

    @Test
    fun `findTopLevelSectionOpenTags preserves attributes in open tag`() {
        val html = """
            <section data-capsule-slide="1" class="intro"><h2>One</h2></section>
            <section id="two" data-background="red"><h2>Two</h2></section>
        """.trimIndent()

        val tags = HtmlSectionParser.findTopLevelSectionOpenTags(html)
        assertEquals(2, tags.size)
        assertTrue(tags[0].value.contains("data-capsule-slide=\"1\""))
        assertTrue(tags[0].value.contains("class=\"intro\""))
        assertTrue(tags[1].value.contains("id=\"two\""))
    }

    @Test
    fun `findTopLevelSectionOpenTags returns empty list for no sections`() {
        val html = "<div><h2>No sections here</h2></div>"
        val tags = HtmlSectionParser.findTopLevelSectionOpenTags(html)
        assertEquals(0, tags.size)
    }

    @Test
    fun `findTopLevelSectionOpenTags handles malformed HTML with unclosed section`() {
        val html = """
            <section><h2>Slide 1</h2></section>
            <section><h2>Slide 2</h2>
        """.trimIndent()

        val tags = HtmlSectionParser.findTopLevelSectionOpenTags(html)
        assertEquals(2, tags.size)
    }

    @Test
    fun `findTopLevelSectionOpenTags positions allow substring extraction`() {
        val html = "<section><h2>Slide 1</h2></section><section><h2>Slide 2</h2></section>"

        val tags = HtmlSectionParser.findTopLevelSectionOpenTags(html)
        assertEquals(2, tags.size)

        val beforeFirst = html.substring(0, tags[0].range.first)
        assertEquals("", beforeFirst)

        val betweenTags = html.substring(tags[0].range.last + 1, tags[1].range.first)
        assertTrue(betweenTags.contains("</section>"))
    }

    @Test
    fun `findTopLevelSectionOpenTags handles multiline section open tags`() {
        val html = """
            <section
              data-capsule-slide="1"
              class="intro">
              <h2>One</h2>
            </section>
            <section><h2>Two</h2></section>
        """.trimIndent()

        val tags = HtmlSectionParser.findTopLevelSectionOpenTags(html)
        assertEquals(2, tags.size)
        assertTrue(tags[0].value.contains("data-capsule-slide=\"1\""))
        assertTrue(tags[0].value.contains("class=\"intro\""))
    }

    // ─── slidesMarkup — régression « div imbriqué » ──────────────

    /**
     * Le deck réel : chaque diapo porte un `<div>` (colonnes, encadré).
     * La forme paresseuse `<div class="slides">(.*?)</div>` s'arrêtait au
     * premier `</div>` de la première diapo — une seule section survivait au
     * comptage, et l'extraction d'une diapo par son index rendait le deck
     * entier au lieu de la diapo demandée.
     */
    @Test
    fun `slidesMarkup keeps every slide when slides contain nested divs`() {
        val deck = """
            <html><head><title>T</title></head><body>
            <div class="reveal">
            <div class="slides">
            <section><div class="colonne"><h2>Un</h2></div></section>
            <section><div class="colonne"><h2>Deux</h2></div></section>
            <section><div class="colonne"><h2>Trois</h2></div></section>
            </div>
            </div>
            </body></html>
        """.trimIndent()

        val markup = HtmlSectionParser.slidesMarkup(deck)

        assertNotNull(markup)
        assertEquals(3, HtmlSectionParser.extractTopLevelSections(markup).size)
    }

    @Test
    fun `slidesMarkup returns null when the deck has no slides container`() {
        assertNull(HtmlSectionParser.slidesMarkup("<html><body><p>rien</p></body></html>"))
    }

    @Test
    fun `slidesMarkup extracts the container content of a flat deck`() {
        val deck = """<div class="slides"><section><h2>Une</h2></section></div>"""
        assertEquals("<section><h2>Une</h2></section>", HtmlSectionParser.slidesMarkup(deck))
    }

    // ─── headMarkup — brique partagée par trois appelants ───────

    /**
     * `headMarkup` est appelé par RemotionCaptureImpl ET par
     * CapsuleVideoTask.createSingleSlideHtml : c'est ce qui garde à chaque diapo
     * extraite sa feuille de style. Les trois appelants en avaient chacun leur
     * copie, dont deux recompilaient l'expression à chaque diapo.
     */
    @Test
    fun `headMarkup returns the whole head block`() {
        val deck = "<html><head><title>T</title><style>body{color:red}</style></head><body>x</body></html>"
        val head = HtmlSectionParser.headMarkup(deck)
        assertTrue(head.startsWith("<head>"))
        assertTrue(head.endsWith("</head>"))
        assertTrue(head.contains("color:red"))
        assertTrue(!head.contains("<body>"), "le corps ne doit pas suivre : $head")
    }

    @Test
    fun `headMarkup is empty when the deck has no head`() {
        assertEquals("", HtmlSectionParser.headMarkup("<section>sans tête</section>"))
    }

    @Test
    fun `headMarkup stops at the first head, not at a later one`() {
        val deck = "<head><style>a</style></head><body><head>b</head></body>"
        assertTrue(HtmlSectionParser.headMarkup(deck).contains("<style>a</style>"))
        assertTrue(!HtmlSectionParser.headMarkup(deck).contains("b"))
    }
}
