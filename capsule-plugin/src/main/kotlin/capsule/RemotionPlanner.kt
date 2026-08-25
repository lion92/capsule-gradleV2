package capsule

import java.io.File

/**
 * Plans a Remotion render of a deck (CAP-ANIM).
 *
 * Pure object — no I/O, no Node, no browser. It turns a deck plus its per-slide
 * durations into the two things the renderer needs: a props document describing
 * what to animate, and the argv that drives it. [RemotionCaptureImpl] executes
 * the plan; everything here is unit-testable.
 *
 * Pattern mirrors [ScreenshotPlanner] (plan + argv builders) and
 * [capsule.audio.AudioPostCommand] (pure argv builder).
 */
object RemotionPlanner {

    /** Composition id declared by the bundled template. */
    const val COMPOSITION_ID: String = "Capsule"

    /** Entry point of the bundled template, relative to the Remotion project. */
    const val RENDER_SCRIPT: String = "render.mjs"

    /** File the plan is serialised to, read back by the render script. */
    const val PROPS_FILE: String = "capsule-props.json"

    /**
     * Builds the plan for a deck.
     *
     * @param sections   the deck's top-level `<section>` markup, in order.
     * @param headHtml   the deck's `<head>` block, replayed so slides keep their
     *                   own stylesheet and identity.
     * @param slideDurations one duration in seconds per slide.
     * @param outputDir  where the rendered WebM is written.
     * @param fps        frame rate of the produced video.
     */
    fun plan(
        sections: List<String>,
        headHtml: String,
        slideDurations: List<Double>,
        outputDir: File,
        viewportWidth: Int,
        viewportHeight: Int,
        fps: Int,
    ): RemotionPlan {
        require(slideDurations.isNotEmpty()) { "slideDurations must not be empty" }
        require(sections.isNotEmpty()) { "sections must not be empty" }
        require(fps in 1..120) { "fps must be between 1 and 120, got $fps" }

        // A deck can carry sections a script has no narration for, and vice
        // versa: pair them up and let the shorter list win, so the render never
        // desynchronises from the audio it will be muxed with.
        val paired = sections.size.coerceAtMost(slideDurations.size)
        val slides = (0 until paired).map { i ->
            RemotionSlide(
                index = i,
                html = sections[i],
                durationInFrames = framesFor(slideDurations[i], fps),
            )
        }
        return RemotionPlan(
            slides = slides,
            headHtml = headHtml,
            width = viewportWidth,
            height = viewportHeight,
            fps = fps,
            finalWebm = outputDir.resolve(ScreenshotPlanner.FINAL_WEBM_NAME),
            propsFile = outputDir.resolve(PROPS_FILE),
        )
    }

    /**
     * Converts a duration in seconds into whole frames, never returning zero:
     * a slide with no measurable audio still has to be on screen.
     */
    fun framesFor(durationSecs: Double, fps: Int): Int =
        Math.round(durationSecs * fps).toInt().coerceAtLeast(1)

    /**
     * Serialises the plan into the props document the composition reads.
     *
     * Kept here rather than in the adapter because it is a pure transformation:
     * a plan in, a string out, verifiable without Node or a browser.
     */
    fun toPropsJson(plan: RemotionPlan): String {
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        val root = mapper.createObjectNode()
        root.put("width", plan.width)
        root.put("height", plan.height)
        root.put("fps", plan.fps)
        root.put("totalFrames", plan.totalFrames)
        root.put("headHtml", plan.headHtml)
        val slides = root.putArray("slides")
        plan.slides.forEach { slide ->
            val node = slides.addObject()
            node.put("index", slide.index)
            node.put("durationInFrames", slide.durationInFrames)
            node.put("html", slide.html)
        }
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root)
    }

    /**
     * Builds the argv running the bundled render script.
     *
     * Concurrency is Remotion's own multi-core knob: it renders that many frames
     * in parallel, each in its own browser tab.
     */
    fun renderArgs(
        plan: RemotionPlan,
        projectDir: File,
        nodeExecutablePath: String,
        concurrency: Int,
    ): List<String> = listOf(
        nodeExecutablePath,
        File(projectDir, RENDER_SCRIPT).absolutePath,
        "--props", plan.propsFile.absolutePath,
        "--out", plan.finalWebm.absolutePath,
        "--concurrency", concurrency.coerceAtLeast(1).toString(),
    )
}

/**
 * Immutable description of a Remotion render.
 */
data class RemotionPlan(
    val slides: List<RemotionSlide>,
    val headHtml: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val finalWebm: File,
    val propsFile: File,
) {
    val size: Int get() = slides.size

    /** Total length of the composition, in frames. */
    val totalFrames: Int get() = slides.sumOf { it.durationInFrames }
}

/** One slide handed to the composition. */
data class RemotionSlide(
    val index: Int,
    val html: String,
    val durationInFrames: Int,
) {
    init {
        require(durationInFrames >= 1) { "durationInFrames must be >= 1, got $durationInFrames" }
    }
}
