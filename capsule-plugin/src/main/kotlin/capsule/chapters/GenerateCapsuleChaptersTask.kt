package capsule.chapters

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Gradle task: `generateCapsuleChapters`
 *
 * Generates chapter metadata (JSON timestamps + titles) and renders
 * intro/outro card HTML files for Matroska chapter markers.
 *
 * The chapter metadata is injected between audio mix and subtitle burn-in
 * by [capsule.CapsuleVideoTask] when `chapters.enabled = true`.
 *
 * Inputs:
 *   - [enabled]       chapters feature toggle (default false)
 *   - [introText]     text for the intro card
 *   - [outroText]     text for the outro card
 *   - [slideSegments] JSON string with slide segment data (title, duration)
 *
 * Outputs:
 *   - [outputDir]     directory containing `chapters.json`, `intro.html`, `outro.html`
 *
 * Economy of ink: the task is skipped when [outputDir] already contains a valid
 * `chapters.json` with non-empty content.
 *
 * Usage:
 *   ./gradlew generateCapsuleChapters \
 *     -Pcapsule.chapters.enabled=true \
 *     -Pcapsule.chapters.introText="Welcome" \
 *     -Pcapsule.chapters.outroText="Thank you"
 */
@DisableCachingByDefault(because = "Chapter generation is a simple file write, not worth caching")
open class GenerateCapsuleChaptersTask : DefaultTask() {

    /** Whether chapter generation is enabled. */
    @get:Input
    val enabled: Property<Boolean> = project.objects.property(Boolean::class.java).convention(false)

    /** Text for the intro card (rendered as HTML). */
    @get:Input
    val introText: Property<String> = project.objects.property(String::class.java).convention("")

    /** Text for the outro card (rendered as HTML). */
    @get:Input
    val outroText: Property<String> = project.objects.property(String::class.java).convention("")

    /** JSON string describing slide segments [{title, start_ms, end_ms}]. */
    @get:Input
    val slideSegmentsJson: Property<String> = project.objects.property(String::class.java).convention("[]")

    /** Output directory for chapters.json, intro.html, outro.html. */
    @get:OutputDirectory
    val outputDir: DirectoryProperty = project.objects.directoryProperty()

    @TaskAction
    fun run() {
        if (!enabled.getOrElse(false)) {
            logger.lifecycle("CAPSULE CHAPTERS → disabled, skipping")
            return
        }

        val outDir = outputDir.get().asFile

        // Economy of ink — skip if the chapters.json already exists and is non-empty.
        val chaptersFile = java.io.File(outDir, "chapters.json")
        if (chaptersFile.exists() && chaptersFile.readText().trim().isNotBlank()
            && chaptersFile.readText().trim() != "[]"
        ) {
            logger.lifecycle("CAPSULE CHAPTERS → ${chaptersFile.name} already exists, skipping (economy of ink)")
            return
        }

        outDir.mkdirs()

        // Parse slide segments from JSON input.
        val segments = ChapterEntryParser.parseSlideSegments(slideSegmentsJson.getOrElse("[]"))

        // Build chapter entries: intro (0-5000ms) + segments + outro (last segment end - last+5000ms).
        val chapters = ChapterEntryParser.buildChapterEntries(
            segments,
            introText.getOrElse(""),
            outroText.getOrElse("")
        )

        // Write chapters metadata JSON.
        val marker: ChapterMarker = resolveChapterMarker()
        marker.format(chapters, chaptersFile)
        logger.lifecycle("CAPSULE CHAPTERS → ${chaptersFile.name} (${chapters.size} chapters)")

        // Render intro card HTML.
        val introFile = java.io.File(outDir, "intro.html")
        val cardRenderer: CardRenderer = resolveCardRenderer()
        if (introText.getOrElse("").isNotBlank()) {
            cardRenderer.renderIntroCard(introText.get(), introFile)
            logger.lifecycle("CAPSULE CHAPTERS → ${introFile.name} (intro card)")
        }

        // Render outro card HTML.
        val outroFile = java.io.File(outDir, "outro.html")
        if (outroText.getOrElse("").isNotBlank()) {
            cardRenderer.renderOutroCard(outroText.get(), outroFile)
            logger.lifecycle("CAPSULE CHAPTERS → ${outroFile.name} (outro card)")
        }
    }

    private fun resolveChapterMarker(): ChapterMarker {
        val ffmpegPath = project.findProperty("capsule.ffmpeg.path")?.toString() ?: "ffmpeg"
        val strict = project.findProperty("capsule.strictMode.enabled")?.toString()?.toBoolean() ?: false
        return if (ffmpegPath == "noop") NoOpChapterMarker() else ChapterMarkerImpl(ffmpegPath)
    }

    private fun resolveCardRenderer(): CardRenderer {
        val ffmpegPath = project.findProperty("capsule.ffmpeg.path")?.toString() ?: "ffmpeg"
        return if (ffmpegPath == "noop") NoOpCardRenderer() else CardRendererImpl()
    }
}
