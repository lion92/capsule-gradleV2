package capsule

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for the pure Remotion planner (CAP-ANIM).
 *
 * No Node, no browser: the planner turns a deck plus durations into a props
 * document and an argv, and that is all this verifies.
 */
class RemotionPlannerTest {

    @TempDir
    lateinit var tempDir: File

    private val sections = listOf(
        """<section data-capsule-slide="1"><h1>Un</h1></section>""",
        """<section data-capsule-slide="2"><h1>Deux</h1></section>""",
    )

    private fun plan(
        durations: List<Double> = listOf(4.0, 6.0),
        secs: List<String> = sections,
        fps: Int = 30,
    ) = RemotionPlanner.plan(
        sections = secs,
        headHtml = "<head><style>body{background:#000}</style></head>",
        slideDurations = durations,
        outputDir = tempDir,
        viewportWidth = 1408,
        viewportHeight = 792,
        fps = fps,
    )

    @Test
    fun `converts each slide duration into whole frames`() {
        val p = plan()
        assertEquals(listOf(120, 180), p.slides.map { it.durationInFrames })
        assertEquals(300, p.totalFrames)
    }

    @Test
    fun `a slide shorter than a frame still gets one frame`() {
        assertEquals(1, RemotionPlanner.framesFor(0.0, 30))
        assertEquals(1, RemotionPlanner.framesFor(0.001, 30))
    }

    @Test
    fun `rounds to the nearest frame rather than truncating`() {
        // 4.99 s at 30 fps is 149.7 frames: truncating would drop a third of a
        // second of narration on every slide.
        assertEquals(150, RemotionPlanner.framesFor(4.99, 30))
    }

    @Test
    fun `pairs sections with durations and stops at the shorter list`() {
        // A deck can carry more sections than the script has narration for.
        val p = plan(durations = listOf(4.0), secs = sections)
        assertEquals(1, p.size, "The render must not outlive the audio it is muxed with")
    }

    @Test
    fun `output lands on the file name the rest of the pipeline expects`() {
        assertEquals(ScreenshotPlanner.FINAL_WEBM_NAME, plan().finalWebm.name)
    }

    @Test
    fun `rejects an empty deck`() {
        assertFailsWith<IllegalArgumentException> { plan(secs = emptyList()) }
    }

    @Test
    fun `rejects an out-of-range frame rate`() {
        assertFailsWith<IllegalArgumentException> { plan(fps = 0) }
        assertFailsWith<IllegalArgumentException> { plan(fps = 500) }
    }

    @Test
    fun `props carry the slides, their length and the deck head`() {
        val json = ObjectMapper().readTree(RemotionPlanner.toPropsJson(plan()))
        assertEquals(1408, json["width"].asInt())
        assertEquals(792, json["height"].asInt())
        assertEquals(30, json["fps"].asInt())
        assertEquals(300, json["totalFrames"].asInt())
        assertTrue(json["headHtml"].asText().contains("background:#000"))
        assertEquals(2, json["slides"].size())
        assertEquals(120, json["slides"][0]["durationInFrames"].asInt())
        assertTrue(json["slides"][1]["html"].asText().contains("Deux"))
    }

    /**
     * La composition ajuste la vitesse du schéma pour qu'il finisse avec la
     * diapo. Sans la durée du clip dans les props, elle ne peut pas savoir
     * qu'il est plus court ou plus long que la voix : sur « Terre ronde », les
     * neuf clips l'étaient tous, jusqu'à 6,4 s d'écart sur une diapo de 14,4 s.
     */
    @Test
    fun `props carry the measured length of each manim clip`() {
        val json = ObjectMapper().readTree(
            RemotionPlanner.toPropsJson(
                plan(),
                mapOf(0 to ManimAsset("S01.mp4", durationSecs = 3.5)),
            )
        )

        assertEquals("S01.mp4", json["slides"][0]["manim"].asText())
        assertEquals(105, json["slides"][0]["manimDurationInFrames"].asInt())
    }

    @Test
    fun `props name a clip whose length could not be measured, without a duration`() {
        val json = ObjectMapper().readTree(
            RemotionPlanner.toPropsJson(
                plan(),
                mapOf(0 to ManimAsset("S01.mp4")),
            )
        )

        assertEquals("S01.mp4", json["slides"][0]["manim"].asText())
        assertTrue(
            json["slides"][0]["manimDurationInFrames"] == null,
            "sans mesure, la composition doit jouer le clip à sa vitesse propre"
        )
    }

    @Test
    fun `props leave a slide without animation untouched`() {
        val json = ObjectMapper().readTree(RemotionPlanner.toPropsJson(plan()))
        assertTrue(json["slides"][0]["manim"] == null)
        assertTrue(json["slides"][0]["manimDurationInFrames"] == null)
    }

    @Test
    fun `argv points node at the bundled script with the props and output`() {
        val p = plan()
        val projectDir = File(tempDir, "remotion")
        val argv = RemotionPlanner.renderArgs(p, projectDir, "node", 6)

        assertEquals("node", argv.first())
        assertEquals(File(projectDir, RemotionPlanner.RENDER_SCRIPT).absolutePath, argv[1])
        assertTrue(argv.containsAll(listOf("--props", p.propsFile.absolutePath)))
        assertTrue(argv.containsAll(listOf("--out", p.finalWebm.absolutePath)))
        assertEquals("6", argv[argv.indexOf("--concurrency") + 1])
    }

    @Test
    fun `concurrency below one is clamped rather than passed through`() {
        val argv = RemotionPlanner.renderArgs(plan(), tempDir, "node", 0)
        assertEquals("1", argv[argv.indexOf("--concurrency") + 1])
    }

    @Test
    fun `slides markup survives a deck whose slides contain divs`() {
        // The non-greedy form stops at the first nested </div> and silently
        // truncates the deck.
        val deck = """
            <html><head></head><body><div class="reveal"><div class="slides">
            <section><div class="fig">one</div></section>
            <section><div class="fig">two</div></section>
            </div></div></body></html>
        """.trimIndent()
        val markup = RemotionCaptureImpl.slidesMarkup(deck)
        assertTrue(markup.contains("one") && markup.contains("two"), "Deck truncated: $markup")
    }

    @Test
    fun `head markup is extracted so slides keep their stylesheet`() {
        val deck = "<html><head><style>h1{color:red}</style></head><body></body></html>"
        assertTrue(RemotionCaptureImpl.headMarkup(deck).contains("color:red"))
    }
}
