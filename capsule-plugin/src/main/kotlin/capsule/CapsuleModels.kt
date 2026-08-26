package capsule

import capsule.audio.AudioPostConfig
import capsule.transcript.TranscriptStrategy
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import javax.inject.Inject

open class CapsuleExtension @Inject constructor(objects: ObjectFactory) {
    /** Path to the capsule-context.yml configuration file. Defaults to "capsule-context.yml" in the project root. */
    val configPath: Property<String> = objects.property(String::class.java)
        .convention("capsule-context.yml")

    /** Active UI language for task descriptions and log messages. Defaults to "en". */
    val language: Property<String> = objects.property(String::class.java)
        .convention("en")

    /** CAP-CR3-2 — strict mode: fail build instead of NoOp fallback when a tool is missing. Defaults to false. */
    val strictMode: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /** CAP-DOCCONTEXT-1 — glob patterns for documentary corpus feeding the Docs channel. Defaults to empty. */
    val docsGlobs: ListProperty<String> = objects.listProperty(String::class.java)
        .convention(emptyList())

    /** CAP-SPD-3 — path to a pedagogical scenario directory (metadata.json + .adoc) or direct .adoc. Defaults to empty. */
    val scenarioFile: Property<String> = objects.property(String::class.java)
        .convention("")

    /** CAP-GLOSSARY-2 — path to a glossary AsciiDoc file (`== Glossary` section + `- term: definition` bullets). Defaults to empty. */
    val glossaryFile: Property<String> = objects.property(String::class.java)
        .convention("")

    val ttsEngine: Property<String> = objects.property(String::class.java)
        .convention("piper")

    val ttsVoice: Property<String> = objects.property(String::class.java)
        .convention("fr_FR-siwis-medium")

    val piperExecutablePath: Property<String> = objects.property(String::class.java)
        .convention("piper")

    val ttsFallbackEnabled: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(true)

    val outputDir: Property<String> = objects.property(String::class.java)
        .convention("capsule")

    val sliderScriptDir: Property<String> = objects.property(String::class.java)
        .convention("capsule")

    val viewportWidth: Property<Int> = objects.property(Int::class.java)
        .convention(1408)

    val viewportHeight: Property<Int> = objects.property(Int::class.java)
        .convention(792)

    val playwrightTimeout: Property<Double> = objects.property(Double::class.java)
        .convention(120_000.0)

    val chromiumExecutablePath: Property<String> = objects.property(String::class.java)
        .convention("")

    val deckSourceDir: Property<String> = objects.property(String::class.java)
        .convention("docs/asciidocRevealJs")

    /** CAP-CR3-4 — path to slider's build output directory (fallback for script/deck resolution). */
    val sliderBuildDir: Property<String> = objects.property(String::class.java)
        .convention("")

    val ffmpegExecutablePath: Property<String> = objects.property(String::class.java)
        .convention("ffmpeg")

    val distribOutputWidth: Property<Int> = objects.property(Int::class.java)
        .convention(1080)

    val distribOutputHeight: Property<Int> = objects.property(Int::class.java)
        .convention(1920)

    val compositeContextOutputFile: Property<String> = objects.property(String::class.java)
        .convention("capsule/capsule-context.json")

    val slideDurationSeconds: Property<Double> = objects.property(Double::class.java)
        .convention(5.0)

    val espeakVoice: Property<String> = objects.property(String::class.java)
        .convention("fr")

    val espeakSpeed: Property<Int> = objects.property(Int::class.java)
        .convention(150)

    val ttsLanguage: Property<String> = objects.property(String::class.java)
        .convention("fr")

    val manimExecutablePath: Property<String> = objects.property(String::class.java)
        .convention("manim")

    val manimQuality: Property<String> = objects.property(String::class.java)
        .convention("l")

    val manimScriptsDir: Property<String> = objects.property(String::class.java)
        .convention("src/manim")

    val manimOutputDir: Property<String> = objects.property(String::class.java)
        .convention("build/capsule/manim")

    val manimParallelRender: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    val manimParallelRenderThreads: Property<Int> = objects.property(Int::class.java)
        .convention(4)

    val parallelCaptureEnabled: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    val parallelCaptureThreads: Property<Int> = objects.property(Int::class.java)
        .convention(4)

    val captureTimeoutMinutes: Property<Int> = objects.property(Int::class.java)
        .convention(5)

    val subtitleEnabled: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    val subtitleFormat: Property<String> = objects.property(String::class.java)
        .convention("srt")

    val subtitleBurnIn: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    val subtitleBurnInFontSize: Property<Int> = objects.property(Int::class.java)
        .convention(24)

    val subtitleBurnInFontColor: Property<String> = objects.property(String::class.java)
        .convention("&H00FFFFFF")

    val subtitleBurnInOutlineColor: Property<String> = objects.property(String::class.java)
        .convention("&H00000000")

    val subtitleBurnInPosition: Property<String> = objects.property(String::class.java)
        .convention("bottom")

    /** CAP-CR3-3 — capture strategy: PLAYWRIGHT (real-time recording) or SCREENSHOT (PNG+FFmpeg per slide). Defaults to PLAYWRIGHT. */
    val captureStrategy: Property<CaptureStrategy> = objects.property(CaptureStrategy::class.java)
        .convention(CaptureStrategy.PLAYWRIGHT)

    /** Groovy DSL helper: accepts a case-insensitive string ("playwright" / "screenshot" / "remotion"). */
    fun captureStrategy(value: String) {
        captureStrategy.set(CaptureStrategy.fromString(value))
    }

    /** CAP-ANIM — where the bundled Remotion composition is materialised, under the build dir. */
    val remotionProjectDir: Property<String> = objects.property(String::class.java)
        .convention("capsule/remotion")

    /** CAP-ANIM — the `node` binary driving the Remotion render. */
    val remotionNodeExecutablePath: Property<String> = objects.property(String::class.java)
        .convention("node")

    /** CAP-ANIM — how many frames Remotion renders in parallel (its multi-core knob). */
    val remotionConcurrency: Property<Int> = objects.property(Int::class.java)
        .convention(4)

    /** CAP-ANIM — frame rate of the animated render. */
    val remotionFps: Property<Int> = objects.property(Int::class.java)
        .convention(30)

    /** CAP-MP4 — output format: WEBM (default), MP4 (H.264 transcode), or BOTH. Defaults to WEBM. */
    val outputFormat: Property<OutputFormat> = objects.property(OutputFormat::class.java)
        .convention(OutputFormat.WEBM)

    /** Groovy DSL helper: accepts a case-insensitive string ("webm" / "mp4" / "both"). */
    fun outputFormat(value: String) {
        outputFormat.set(OutputFormat.fromString(value))
    }

    /** CAP-CR3-1 — duration validation: probe video duration and compare to audio sum. Defaults to false. */
    val durationValidationEnabled: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /** CAP-CR3-1 — tolerance in seconds for duration validation. Defaults to 2.0. */
    val durationValidationToleranceSecs: Property<Double> = objects.property(Double::class.java)
        .convention(2.0)

    /** CAP-AUDIO — `true` to mix background music under the voice track. Defaults to false (opt-in). */
    val audioPostBgmEnabled: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /** CAP-AUDIO — path to the BGM audio file. Defaults to empty (no BGM). */
    val audioPostBgmFile: Property<String> = objects.property(String::class.java)
        .convention("")

    /** CAP-AUDIO — BGM level in dB. Defaults to -18.0 (music bed under voice). */
    val audioPostBgmLevel: Property<Double> = objects.property(Double::class.java)
        .convention(-18.0)

    /** CAP-AUDIO — target loudness in LUFS (EBU R128). Defaults to -16.0 (streaming web). */
    val audioPostLoudnessTarget: Property<Double> = objects.property(Double::class.java)
        .convention(-16.0)

    /** CAP-AUDIO — `true` to lower BGM when voice speaks (sidechain compression). Defaults to false. */
    val audioPostDuckingEnabled: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /** CAP-TRANSCRIPT — `true` to generate an AsciiDoc transcript article from the capsule. Defaults to false (opt-in). */
    val transcriptEnabled: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /** CAP-TRANSCRIPT — generation strategy: TEMPLATE (default, deterministic, no LLM) or LLM (enrichment via LlmBuildService). */
    val transcriptStrategy: Property<TranscriptStrategy> = objects.property(TranscriptStrategy::class.java)
        .convention(TranscriptStrategy.TEMPLATE)

    /** Groovy DSL helper: accepts a case-insensitive string ("template" / "llm"). */
    fun transcriptStrategy(value: String) {
        transcriptStrategy.set(TranscriptStrategy.fromString(value))
    }

    /** CAP-PODCAST — `true` to concatenate per-slide MP3s into a podcast MP3. Defaults to false (opt-in). */
    val podcastEnabled: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /** CAP-PODCAST — output path for the podcast MP3. Defaults to empty (task resolves a default under build/capsule/). */
    val podcastOutputFile: Property<String> = objects.property(String::class.java)
        .convention("")

    /** CAP-PREVIEW — dry-run mode: capture PNG screenshots only, skip FFmpeg encode/concat/video/audio. Defaults to false. */
    val previewOnly: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /** CAP-CHAPITRE — `true` to generate Matroska chapter metadata and optional intro/outro cards. Defaults to false (opt-in). */
    val chaptersEnabled: Property<Boolean> = objects.property(Boolean::class.java)
        .convention(false)

    /** CAP-CHAPITRE — custom text for the intro card title (default `""` — uses deck name). */
    val chaptersIntroText: Property<String> = objects.property(String::class.java)
        .convention("")

    /** CAP-CHAPITRE — custom text for the outro card title (default `""` — uses "Thank you"). */
    val chaptersOutroText: Property<String> = objects.property(String::class.java)
        .convention("")

    internal val conventions: CapsuleConventions = CapsuleConventions(
        outputDir = "capsule",
        sliderScriptDir = "capsule",
        deckSourceDir = "docs/asciidocRevealJs",
        sliderBuildDir = "",
        chromiumExecutablePath = "",
        ttsEngine = "piper",
        ttsVoice = "fr_FR-siwis-medium",
        piperExecutablePath = "piper",
        ttsFallbackEnabled = true,
        espeakVoice = "fr",
        espeakSpeed = 150,
        ttsLanguage = "fr",
        viewportWidth = 1408,
        viewportHeight = 792,
        playwrightTimeout = 120_000.0,
        slideDurationSeconds = 5.0,
        parallelCaptureEnabled = false,
        parallelCaptureThreads = 4,
        captureTimeoutMinutes = 5,
        subtitleEnabled = false,
        subtitleFormat = "srt",
        subtitleBurnIn = false,
        subtitleBurnInFontSize = 24,
        subtitleBurnInFontColor = "&H00FFFFFF",
        subtitleBurnInOutlineColor = "&H00000000",
        subtitleBurnInPosition = "bottom",
        captureStrategy = CaptureStrategy.PLAYWRIGHT,
        outputFormat = OutputFormat.WEBM,
        ffmpegExecutablePath = "ffmpeg",
        distribOutputWidth = 1080,
        distribOutputHeight = 1920,
        manimExecutablePath = "manim",
        manimQuality = "l",
        manimScriptsDir = "src/manim",
        manimOutputDir = "build/capsule/manim",
        manimParallelRender = false,
        manimParallelRenderThreads = 4,
        durationValidationEnabled = false,
        durationValidationToleranceSecs = 2.0,
        audioPostBgmEnabled = false,
        audioPostBgmFile = "",
        audioPostBgmLevel = -18.0,
        audioPostLoudnessTarget = -16.0,
        audioPostDuckingEnabled = false,
        transcriptEnabled = false,
        transcriptStrategy = TranscriptStrategy.TEMPLATE,
        remotionProjectDir = "capsule/remotion",
        remotionNodeExecutablePath = "node",
        remotionConcurrency = 4,
        remotionFps = 30,
        podcastEnabled = false,
        podcastOutputFile = "",
        previewOnly = false,
        chaptersEnabled = false,
        chaptersIntroText = "",
        chaptersOutroText = ""
    )
}

data class CapsuleConventions(
    val outputDir: String,
    val sliderScriptDir: String,
    val deckSourceDir: String,
    val sliderBuildDir: String,
    val chromiumExecutablePath: String,
    val ttsEngine: String,
    val ttsVoice: String,
    val piperExecutablePath: String,
    val ttsFallbackEnabled: Boolean,
    val espeakVoice: String,
    val espeakSpeed: Int,
    val ttsLanguage: String,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val playwrightTimeout: Double,
    val slideDurationSeconds: Double,
    val parallelCaptureEnabled: Boolean,
    val parallelCaptureThreads: Int,
    val captureTimeoutMinutes: Int,
    val subtitleEnabled: Boolean,
    val subtitleFormat: String,
    val subtitleBurnIn: Boolean,
    val subtitleBurnInFontSize: Int,
    val subtitleBurnInFontColor: String,
    val subtitleBurnInOutlineColor: String,
    val subtitleBurnInPosition: String,
    val captureStrategy: CaptureStrategy,
    val outputFormat: OutputFormat,
    val ffmpegExecutablePath: String,
    val distribOutputWidth: Int,
    val distribOutputHeight: Int,
    val manimExecutablePath: String,
    val manimQuality: String,
    val manimScriptsDir: String,
    val manimOutputDir: String,
    val manimParallelRender: Boolean,
    val manimParallelRenderThreads: Int,
    val durationValidationEnabled: Boolean,
    val durationValidationToleranceSecs: Double,
    val audioPostBgmEnabled: Boolean,
    val audioPostBgmFile: String,
    val audioPostBgmLevel: Double,
    val audioPostLoudnessTarget: Double,
    val audioPostDuckingEnabled: Boolean,
    val transcriptEnabled: Boolean,
    val transcriptStrategy: TranscriptStrategy,
    val remotionProjectDir: String,
    val remotionNodeExecutablePath: String,
    val remotionConcurrency: Int,
    val remotionFps: Int,
    val podcastEnabled: Boolean,
    val podcastOutputFile: String,
    val previewOnly: Boolean,
    val chaptersEnabled: Boolean,
    val chaptersIntroText: String,
    val chaptersOutroText: String
)
