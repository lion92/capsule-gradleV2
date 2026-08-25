package capsule.podcast

import capsule.MediaProbeUtil
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Gradle task: `generateCapsulePodcast` (CAP-PODCAST US-2).
 *
 * Concatenates the per-slide TTS MP3 files (produced by
 * `synthesizeTtsForScript` in `generateCapsuleVideo`) into a single
 * podcast MP3 via the FFmpeg concat demuxer
 * (`-f concat -safe 0 -i <listfile> -c copy <output>`). No re-encoding,
 * lossless, fast — the podcast doubles the MVP0 deliverable: capsule
 * video + audio-only mobile file.
 *
 * Inputs:
 *   - [audioFiles]   the per-slide `slide-XX.mp3` files produced by
 *                   `generateCapsuleVideo` (dependsOn)
 *   - [deckName]    the capsule deck name (resolves output convention
 *                   `build/capsule/<deckName>-podcast.mp3`)
 *   - [podcastOutput] the output podcast MP3 path
 *   - [podcastEnabled] master switch (default false — opt-in)
 *
 * Economy of ink (AGENT.adoc):
 * - Skipped when [podcastEnabled] is false (default, backward compat).
 * - Skipped when [podcastOutput] already exists and has a valid
 *   duration (`MediaProbeUtil.probeDuration > 0.0`) — a valid podcast
 *   is never overwritten by a re-computation of the same inputs.
 * - Skipped when [audioFiles] is empty — warns (no per-slide MP3 yet).
 *
 * Degraded mode (pattern `AudioPostProcessor.process`):
 * - NoOp ffmpeg path → [NoOpPodcastConcatenator] returns `false` →
 *   warns and keeps no podcast (no output produced, backward compat).
 *
 * Usage:
 *   ./gradlew generateCapsulePodcast -Pcapsule.podcast.enabled=true
 *   ./gradlew generateCapsulePodcast -Pcapsule.podcast.enabled=true \
 *     -Pcapsule.podcast.outputFile=/out/demo.mp3
 */
@DisableCachingByDefault(because = "Concatenates external MP3 files via ffmpeg, not a pure function of inputs")
abstract class GenerateCapsulePodcastTask : DefaultTask() {

    /** Per-slide TTS MP3 files (`slide-XX.mp3`) produced by `generateCapsuleVideo`. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val audioFiles: ConfigurableFileCollection

    /** The capsule deck name (resolves default output path convention). */
    @get:Input
    abstract val deckName: Property<String>

    /** Output podcast MP3 file. */
    @get:OutputFile
    abstract val podcastOutput: RegularFileProperty

    /** Master switch — when false (default), the task is a no-op skip. */
    @get:Input
    abstract val podcastEnabled: Property<Boolean>

    @TaskAction
    fun run() {
        if (!podcastEnabled.get()) {
            logger.lifecycle("CAPSULE PODCAST → skipped (podcastEnabled=false)")
            return
        }

        val output = podcastOutput.get().asFile

        // Economy of ink — skip if the podcast already exists and is valid.
        if (output.exists() && MediaProbeUtil.probeDuration(output) > 0.0) {
            logger.lifecycle("CAPSULE PODCAST → ${output.name} already exists and is valid, skipping (economy of ink)")
            return
        }

        val mp3s = audioFiles.files.toList().sortedBy { it.name }
        if (mp3s.isEmpty()) {
            logger.warn("CAPSULE PODCAST → no per-slide MP3 files found for deck '{}'. Run generateCapsuleVideo first.", deckName.get())
            return
        }

        val ffmpegPath = project.extensions
            .findByType(capsule.CapsuleExtension::class.java)
            ?.ffmpegExecutablePath?.get()
            ?: "ffmpeg"
        val strict = project.extensions
            .findByType(capsule.CapsuleExtension::class.java)
            ?.strictMode?.get()
            ?: false
        val concatenator = capsule.CapsuleManager.resolvePodcastConcatenator(ffmpegPath, strict)

        if (!concatenator.isAvailable()) {
            logger.warn("CAPSULE PODCAST → ffmpeg unavailable (path={}), podcast skipped (degraded)", ffmpegPath)
            return
        }

        val produced = concatenator.concatenate(mp3s, output)
        if (produced) {
            logger.lifecycle("CAPSULE PODCAST → {} ({} MP3s concatenated via {})", output.name, mp3s.size, concatenator.name())
        } else {
            logger.warn("CAPSULE PODCAST → concatenation degraded (no podcast produced, {} returned false)", concatenator.name())
        }
    }
}