package capsule

import capsule.audio.AudioPostConfig
import capsule.podcast.PodcastConfig
import capsule.transcript.TranscriptConfig
import capsule.transcript.TranscriptStrategy
import java.io.File

/**
 * Merges configuration from four sources with priority:
 * ENV vars < gradle.properties < YAML file < CLI -P params
 *
 * Pattern aligné sur plantuml-gradle ConfigMerger.
 * Each higher-priority source overrides the same key from lower-priority sources.
 */
object CapsuleConfigMerger {

    /**
     * Merges configuration from all four sources.
     *
     * @param projectDir The project directory (where gradle.properties lives)
     * @param yamlConfig The configuration loaded from the YAML file
     * @param cliParams  CLI -P params as a flat map (e.g. "tts.engine" -> "piper")
     * @param yamlLoaded Whether the YAML file was actually found and loaded.
     *                   When false, YAML values are ignored and props/ENV take precedence.
     * @return The merged CapsuleConfig with all sources resolved
     */
    fun merge(projectDir: File, yamlConfig: CapsuleConfig, cliParams: Map<String, Any?>, yamlLoaded: Boolean = true): CapsuleConfig {
        val propertiesConfig = loadFromGradleProperties(projectDir)
        val envConfig = loadFromEnvironment()

        val yaml: CapsuleConfig? = if (yamlLoaded) yamlConfig else null

        return CapsuleConfig(
            input = mergeInputConfig(envConfig.input, propertiesConfig.input, yaml?.input, cliParams),
            tts = mergeTtsConfig(envConfig.tts, propertiesConfig.tts, yaml?.tts, cliParams),
            capture = mergeCaptureConfig(envConfig.capture, propertiesConfig.capture, yaml?.capture, cliParams),
            distrib = mergeDistribConfig(envConfig.distrib, propertiesConfig.distrib, yaml?.distrib, cliParams),
            manim = mergeManimConfig(envConfig.manim, propertiesConfig.manim, yaml?.manim, cliParams),
            output = mergeOutputConfig(envConfig.output, propertiesConfig.output, yaml?.output, cliParams),
            strictMode = mergeStrictModeConfig(envConfig.strictMode, propertiesConfig.strictMode, yaml?.strictMode, cliParams),
            context = mergeContextConfig(envConfig.context, propertiesConfig.context, yaml?.context, cliParams),
            validation = mergeValidationConfig(envConfig.validation, propertiesConfig.validation, yaml?.validation, cliParams),
            audioPost = mergeAudioPostConfig(envConfig.audioPost, propertiesConfig.audioPost, yaml?.audioPost, cliParams),
            transcript = mergeTranscriptConfig(envConfig.transcript, propertiesConfig.transcript, yaml?.transcript, cliParams),
            remotion = mergeRemotionConfig(envConfig.remotion, propertiesConfig.remotion, yaml?.remotion, cliParams),
            podcast = mergePodcastConfig(envConfig.podcast, propertiesConfig.podcast, yaml?.podcast, cliParams)
        )
    }

    /**
     * Loads configuration from gradle.properties in the project directory.
     * Only reads properties prefixed with "capsule.".
     */
    internal fun loadFromGradleProperties(projectDir: File): CapsuleConfig {
        val props = mutableMapOf<String, String>()

        val propertiesFile = File(projectDir, "gradle.properties")
        if (propertiesFile.exists()) {
            propertiesFile.reader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.startsWith("capsule.") && !trimmed.startsWith("#")) {
                        val parts = trimmed.split("=", limit = 2)
                        if (parts.size == 2) {
                            props[parts[0].trim()] = parts[1].trim()
                        }
                    }
                }
            }
        }

        return buildConfigFromProperties(props)
    }

    /**
     * Loads configuration from environment variables prefixed with CAPSULE_.
     *
     * Convention: CAPSULE_TTS_ENGINE → tts.engine, CAPSULE_CAPTURE_VIEWPORT_WIDTH → capture.viewportWidth
     */
    internal fun loadFromEnvironment(): CapsuleConfig {
        val env = System.getenv()

        return CapsuleConfig(
            input = InputConfig(
                outputDir = env["CAPSULE_INPUT_OUTPUT_DIR"] ?: "capsule",
                sliderScriptDir = env["CAPSULE_INPUT_SLIDER_SCRIPT_DIR"] ?: "capsule",
                deckSourceDir = env["CAPSULE_INPUT_DECK_SOURCE_DIR"] ?: "docs/asciidocRevealJs",
                chromiumExecutablePath = env["CAPSULE_INPUT_CHROMIUM_EXECUTABLE_PATH"] ?: ""
            ),
            tts = TtsConfig(
                engine = env["CAPSULE_TTS_ENGINE"] ?: "piper",
                voice = env["CAPSULE_TTS_VOICE"] ?: "fr_FR-siwis-medium",
                piperExecutablePath = env["CAPSULE_TTS_PIPER_EXECUTABLE_PATH"] ?: "piper",
                fallbackEnabled = env["CAPSULE_TTS_FALLBACK_ENABLED"]?.toBoolean() ?: true,
                espeakVoice = env["CAPSULE_TTS_ESPEAK_VOICE"] ?: "fr",
                espeakSpeed = env["CAPSULE_TTS_ESPEAK_SPEED"]?.toIntOrNull() ?: 150,
                language = env["CAPSULE_TTS_LANGUAGE"] ?: "fr"
            ),
            capture = CaptureConfig(
                viewportWidth = env["CAPSULE_CAPTURE_VIEWPORT_WIDTH"]?.toIntOrNull() ?: 1408,
                viewportHeight = env["CAPSULE_CAPTURE_VIEWPORT_HEIGHT"]?.toIntOrNull() ?: 792,
                playwrightTimeout = env["CAPSULE_CAPTURE_PLAYWRIGHT_TIMEOUT"]?.toDoubleOrNull() ?: 120_000.0,
                slideDurationSeconds = env["CAPSULE_CAPTURE_SLIDE_DURATION_SECONDS"]?.toDoubleOrNull() ?: 5.0,
                parallelCaptureEnabled = env["CAPSULE_CAPTURE_PARALLEL_CAPTURE_ENABLED"]?.toBoolean() ?: false,
                parallelCaptureThreads = env["CAPSULE_CAPTURE_PARALLEL_CAPTURE_THREADS"]?.toIntOrNull() ?: 4,
                captureTimeoutMinutes = env["CAPSULE_CAPTURE_TIMEOUT_MINUTES"]?.toIntOrNull() ?: 5,
                subtitleEnabled = env["CAPSULE_CAPTURE_SUBTITLE_ENABLED"]?.toBoolean() ?: false,
                subtitleFormat = env["CAPSULE_CAPTURE_SUBTITLE_FORMAT"] ?: "srt",
                subtitleBurnIn = env["CAPSULE_CAPTURE_SUBTITLE_BURN_IN"]?.toBoolean() ?: false,
                subtitleBurnInFontSize = env["CAPSULE_CAPTURE_SUBTITLE_BURN_IN_FONT_SIZE"]?.toIntOrNull() ?: 24,
                subtitleBurnInFontColor = env["CAPSULE_CAPTURE_SUBTITLE_BURN_IN_FONT_COLOR"] ?: "&H00FFFFFF",
                subtitleBurnInOutlineColor = env["CAPSULE_CAPTURE_SUBTITLE_BURN_IN_OUTLINE_COLOR"] ?: "&H00000000",
                subtitleBurnInPosition = env["CAPSULE_CAPTURE_SUBTITLE_BURN_IN_POSITION"] ?: "bottom",
                strategy = CaptureStrategy.fromString(env["CAPSULE_CAPTURE_STRATEGY"])
            ),
            distrib = DistribConfig(
                ffmpegExecutablePath = env["CAPSULE_DISTRIB_FFMPEG_EXECUTABLE_PATH"] ?: "ffmpeg",
                outputWidth = env["CAPSULE_DISTRIB_OUTPUT_WIDTH"]?.toIntOrNull() ?: 1080,
                outputHeight = env["CAPSULE_DISTRIB_OUTPUT_HEIGHT"]?.toIntOrNull() ?: 1920
            ),
            manim = ManimConfig(
                executablePath = env["CAPSULE_MANIM_EXECUTABLE_PATH"] ?: "manim",
                quality = env["CAPSULE_MANIM_QUALITY"] ?: "l",
                scriptsDir = env["CAPSULE_MANIM_SCRIPTS_DIR"] ?: "src/manim",
                outputDir = env["CAPSULE_MANIM_OUTPUT_DIR"] ?: "build/capsule/manim",
                parallelRender = env["CAPSULE_MANIM_PARALLEL_RENDER"]?.toBoolean() ?: false,
                parallelRenderThreads = env["CAPSULE_MANIM_PARALLEL_RENDER_THREADS"]?.toIntOrNull() ?: 4
            ),
            output = OutputConfig(
                videoDestinationDir = env["CAPSULE_OUTPUT_VIDEO_DESTINATION_DIR"] ?: "office/videos",
                versioning = VersioningStrategy.fromString(env["CAPSULE_OUTPUT_VERSIONING"]),
                versionPrefix = env["CAPSULE_OUTPUT_VERSION_PREFIX"] ?: "v",
                format = OutputFormat.fromString(env["CAPSULE_OUTPUT_FORMAT"])
            ),
            strictMode = StrictModeConfig(
                enabled = env["CAPSULE_STRICT_MODE_ENABLED"]?.toBoolean() ?: false
            ),
            context = ContextConfig(
                docsGlobs = env["CAPSULE_CONTEXT_DOCS_GLOBS"]?.let { splitCommaList(it) } ?: emptyList(),
                scenarioFile = env["CAPSULE_CONTEXT_SCENARIO_FILE"]?.takeIf { it.isNotBlank() },
                glossaryFile = env["CAPSULE_CONTEXT_GLOSSARY_FILE"]?.takeIf { it.isNotBlank() }
            ),
            validation = ValidationConfig(
                durationEnabled = env["CAPSULE_VALIDATION_DURATION_ENABLED"]?.toBoolean() ?: false,
                toleranceSecs = env["CAPSULE_VALIDATION_TOLERANCE_SECS"]?.toDoubleOrNull() ?: 2.0
            ),
            audioPost = AudioPostConfig(
                bgmEnabled = env["CAPSULE_AUDIO_POST_BGM_ENABLED"]?.toBoolean() ?: false,
                bgmFile = env["CAPSULE_AUDIO_POST_BGM_FILE"] ?: "",
                bgmLevel = env["CAPSULE_AUDIO_POST_BGM_LEVEL"]?.toDoubleOrNull() ?: -18.0,
                loudnessTarget = env["CAPSULE_AUDIO_POST_LOUDNESS_TARGET"]?.toDoubleOrNull() ?: -16.0,
                duckingEnabled = env["CAPSULE_AUDIO_POST_DUCKING_ENABLED"]?.toBoolean() ?: false
            ),
            transcript = TranscriptConfig(
                enabled = env["CAPSULE_TRANSCRIPT_ENABLED"]?.toBoolean() ?: false,
                strategy = TranscriptStrategy.fromString(env["CAPSULE_TRANSCRIPT_STRATEGY"])
            ),
            remotion = RemotionConfig(
                projectDir = env["CAPSULE_REMOTION_PROJECT_DIR"] ?: "capsule/remotion",
                nodeExecutablePath = env["CAPSULE_REMOTION_NODE_EXECUTABLE_PATH"] ?: "node",
                concurrency = env["CAPSULE_REMOTION_CONCURRENCY"]?.toIntOrNull() ?: 4,
                fps = env["CAPSULE_REMOTION_FPS"]?.toIntOrNull() ?: 30
            ),
            podcast = PodcastConfig(
                enabled = env["CAPSULE_PODCAST_ENABLED"]?.toBoolean() ?: false,
                outputFile = env["CAPSULE_PODCAST_OUTPUT_FILE"] ?: ""
            )
        )
    }

    private fun buildConfigFromProperties(props: Map<String, String>): CapsuleConfig {
        return CapsuleConfig(
            input = InputConfig(
                outputDir = props["capsule.input.outputDir"] ?: "capsule",
                sliderScriptDir = props["capsule.input.sliderScriptDir"] ?: "capsule",
                deckSourceDir = props["capsule.input.deckSourceDir"] ?: "docs/asciidocRevealJs",
                chromiumExecutablePath = props["capsule.input.chromiumExecutablePath"] ?: ""
            ),
            tts = TtsConfig(
                engine = props["capsule.tts.engine"] ?: "piper",
                voice = props["capsule.tts.voice"] ?: "fr_FR-siwis-medium",
                piperExecutablePath = props["capsule.tts.piperExecutablePath"] ?: "piper",
                fallbackEnabled = props["capsule.tts.fallbackEnabled"]?.toBoolean() ?: true,
                espeakVoice = props["capsule.tts.espeakVoice"] ?: "fr",
                espeakSpeed = props["capsule.tts.espeakSpeed"]?.toIntOrNull() ?: 150,
                language = props["capsule.tts.language"] ?: "fr"
            ),
            capture = CaptureConfig(
                viewportWidth = props["capsule.capture.viewportWidth"]?.toIntOrNull() ?: 1408,
                viewportHeight = props["capsule.capture.viewportHeight"]?.toIntOrNull() ?: 792,
                playwrightTimeout = props["capsule.capture.playwrightTimeout"]?.toDoubleOrNull() ?: 120_000.0,
                slideDurationSeconds = props["capsule.capture.slideDurationSeconds"]?.toDoubleOrNull() ?: 5.0,
                parallelCaptureEnabled = props["capsule.capture.parallelCaptureEnabled"]?.toBoolean() ?: false,
                parallelCaptureThreads = props["capsule.capture.parallelCaptureThreads"]?.toIntOrNull() ?: 4,
                captureTimeoutMinutes = props["capsule.capture.captureTimeoutMinutes"]?.toIntOrNull() ?: 5,
                subtitleEnabled = props["capsule.capture.subtitleEnabled"]?.toBoolean() ?: false,
                subtitleFormat = props["capsule.capture.subtitleFormat"] ?: "srt",
                subtitleBurnIn = props["capsule.capture.subtitleBurnIn"]?.toBoolean() ?: false,
                subtitleBurnInFontSize = props["capsule.capture.subtitleBurnInFontSize"]?.toIntOrNull() ?: 24,
                subtitleBurnInFontColor = props["capsule.capture.subtitleBurnInFontColor"] ?: "&H00FFFFFF",
                subtitleBurnInOutlineColor = props["capsule.capture.subtitleBurnInOutlineColor"] ?: "&H00000000",
                subtitleBurnInPosition = props["capsule.capture.subtitleBurnInPosition"] ?: "bottom",
                strategy = CaptureStrategy.fromString(props["capsule.capture.strategy"])
            ),
            distrib = DistribConfig(
                ffmpegExecutablePath = props["capsule.distrib.ffmpegExecutablePath"] ?: "ffmpeg",
                outputWidth = props["capsule.distrib.outputWidth"]?.toIntOrNull() ?: 1080,
                outputHeight = props["capsule.distrib.outputHeight"]?.toIntOrNull() ?: 1920
            ),
            manim = ManimConfig(
                executablePath = props["capsule.manim.executablePath"] ?: "manim",
                quality = props["capsule.manim.quality"] ?: "l",
                scriptsDir = props["capsule.manim.scriptsDir"] ?: "src/manim",
                outputDir = props["capsule.manim.outputDir"] ?: "build/capsule/manim",
                parallelRender = props["capsule.manim.parallelRender"]?.toBoolean() ?: false,
                parallelRenderThreads = props["capsule.manim.parallelRenderThreads"]?.toIntOrNull() ?: 4
            ),
            output = OutputConfig(
                videoDestinationDir = props["capsule.output.videoDestinationDir"] ?: "office/videos",
                versioning = VersioningStrategy.fromString(props["capsule.output.versioning"]),
                versionPrefix = props["capsule.output.versionPrefix"] ?: "v",
                format = OutputFormat.fromString(props["capsule.output.format"])
            ),
            strictMode = StrictModeConfig(
                enabled = props["capsule.strictMode.enabled"]?.toBoolean() ?: false
            ),
            context = ContextConfig(
                docsGlobs = props["capsule.context.docsGlobs"]?.let { splitCommaList(it) } ?: emptyList(),
                scenarioFile = props["capsule.context.scenarioFile"]?.takeIf { it.isNotBlank() },
                glossaryFile = props["capsule.context.glossaryFile"]?.takeIf { it.isNotBlank() }
            ),
            validation = ValidationConfig(
                durationEnabled = props["capsule.validation.durationEnabled"]?.toBoolean() ?: false,
                toleranceSecs = props["capsule.validation.toleranceSecs"]?.toDoubleOrNull() ?: 2.0
            ),
            audioPost = AudioPostConfig(
                bgmEnabled = props["capsule.audioPost.bgmEnabled"]?.toBoolean() ?: false,
                bgmFile = props["capsule.audioPost.bgmFile"] ?: "",
                bgmLevel = props["capsule.audioPost.bgmLevel"]?.toDoubleOrNull() ?: -18.0,
                loudnessTarget = props["capsule.audioPost.loudnessTarget"]?.toDoubleOrNull() ?: -16.0,
                duckingEnabled = props["capsule.audioPost.duckingEnabled"]?.toBoolean() ?: false
            ),
            transcript = TranscriptConfig(
                enabled = props["capsule.transcript.enabled"]?.toBoolean() ?: false,
                strategy = TranscriptStrategy.fromString(props["capsule.transcript.strategy"])
            ),
            remotion = RemotionConfig(
                projectDir = props["capsule.remotion.projectDir"] ?: "capsule/remotion",
                nodeExecutablePath = props["capsule.remotion.nodeExecutablePath"] ?: "node",
                concurrency = props["capsule.remotion.concurrency"]?.toIntOrNull() ?: 4,
                fps = props["capsule.remotion.fps"]?.toIntOrNull() ?: 30
            ),
            podcast = PodcastConfig(
                enabled = props["capsule.podcast.enabled"]?.toBoolean() ?: false,
                outputFile = props["capsule.podcast.outputFile"] ?: ""
            )
        )
    }

    // ─── Section merge methods ──────────────────────────────────
    //
    // Merge logic: CLI > YAML > Props > ENV
    // YAML always wins over props. Props always wins over ENV.
    // Empty-string fields use isNotBlank() as "explicitly set" heuristic.
    // Boolean/Int/Double fields: YAML/YAML-provided value always preferred over props.
    // When yaml == null (no YAML file found), props > ENV fallback applies.
    //

    private fun mergeInputConfig(env: InputConfig, props: InputConfig, yaml: InputConfig?, cli: Map<String, Any?>): InputConfig {
        return InputConfig(
            outputDir = mergeStr(cli, "input.outputDir", yaml?.outputDir, props.outputDir, env.outputDir),
            sliderScriptDir = mergeStr(cli, "input.sliderScriptDir", yaml?.sliderScriptDir, props.sliderScriptDir, env.sliderScriptDir),
            deckSourceDir = mergeStr(cli, "input.deckSourceDir", yaml?.deckSourceDir, props.deckSourceDir, env.deckSourceDir),
            chromiumExecutablePath = mergeStr(cli, "input.chromiumExecutablePath", yaml?.chromiumExecutablePath, props.chromiumExecutablePath, env.chromiumExecutablePath)
        )
    }

    private fun mergeTtsConfig(env: TtsConfig, props: TtsConfig, yaml: TtsConfig?, cli: Map<String, Any?>): TtsConfig {
        return TtsConfig(
            engine = mergeStr(cli, "tts.engine", yaml?.engine, props.engine, env.engine),
            voice = mergeStr(cli, "tts.voice", yaml?.voice, props.voice, env.voice),
            piperExecutablePath = mergeStr(cli, "tts.piperExecutablePath", yaml?.piperExecutablePath, props.piperExecutablePath, env.piperExecutablePath),
            fallbackEnabled = mergeBoolean(cli, "tts.fallbackEnabled", yaml?.fallbackEnabled, props.fallbackEnabled),
            espeakVoice = mergeStr(cli, "tts.espeakVoice", yaml?.espeakVoice, props.espeakVoice, env.espeakVoice),
            espeakSpeed = mergeInt(cli, "tts.espeakSpeed", yaml?.espeakSpeed, props.espeakSpeed),
            language = mergeStr(cli, "tts.language", yaml?.language, props.language, env.language)
        )
    }

    private fun mergeCaptureConfig(env: CaptureConfig, props: CaptureConfig, yaml: CaptureConfig?, cli: Map<String, Any?>): CaptureConfig {
        return CaptureConfig(
            viewportWidth = mergeInt(cli, "capture.viewportWidth", yaml?.viewportWidth, props.viewportWidth),
            viewportHeight = mergeInt(cli, "capture.viewportHeight", yaml?.viewportHeight, props.viewportHeight),
            playwrightTimeout = mergeDouble(cli, "capture.playwrightTimeout", yaml?.playwrightTimeout, props.playwrightTimeout),
            slideDurationSeconds = mergeDouble(cli, "capture.slideDurationSeconds", yaml?.slideDurationSeconds, props.slideDurationSeconds),
            parallelCaptureEnabled = mergeBoolean(cli, "capture.parallelCaptureEnabled", yaml?.parallelCaptureEnabled, props.parallelCaptureEnabled),
            parallelCaptureThreads = mergeInt(cli, "capture.parallelCaptureThreads", yaml?.parallelCaptureThreads, props.parallelCaptureThreads),
            captureTimeoutMinutes = mergeInt(cli, "capture.captureTimeoutMinutes", yaml?.captureTimeoutMinutes, props.captureTimeoutMinutes),
            subtitleEnabled = mergeBoolean(cli, "capture.subtitleEnabled", yaml?.subtitleEnabled, props.subtitleEnabled),
            subtitleFormat = mergeStr(cli, "capture.subtitleFormat", yaml?.subtitleFormat, props.subtitleFormat, env.subtitleFormat),
            subtitleBurnIn = mergeBoolean(cli, "capture.subtitleBurnIn", yaml?.subtitleBurnIn, props.subtitleBurnIn),
            subtitleBurnInFontSize = mergeInt(cli, "capture.subtitleBurnInFontSize", yaml?.subtitleBurnInFontSize, props.subtitleBurnInFontSize),
            subtitleBurnInFontColor = mergeStr(cli, "capture.subtitleBurnInFontColor", yaml?.subtitleBurnInFontColor, props.subtitleBurnInFontColor, env.subtitleBurnInFontColor),
            subtitleBurnInOutlineColor = mergeStr(cli, "capture.subtitleBurnInOutlineColor", yaml?.subtitleBurnInOutlineColor, props.subtitleBurnInOutlineColor, env.subtitleBurnInOutlineColor),
            subtitleBurnInPosition = mergeStr(cli, "capture.subtitleBurnInPosition", yaml?.subtitleBurnInPosition, props.subtitleBurnInPosition, env.subtitleBurnInPosition),
            strategy = mergeCaptureStrategy(cli, "capture.strategy", yaml?.strategy, props.strategy)
        )
    }

    private fun mergeDistribConfig(env: DistribConfig, props: DistribConfig, yaml: DistribConfig?, cli: Map<String, Any?>): DistribConfig {
        return DistribConfig(
            ffmpegExecutablePath = mergeStr(cli, "distrib.ffmpegExecutablePath", yaml?.ffmpegExecutablePath, props.ffmpegExecutablePath, env.ffmpegExecutablePath),
            outputWidth = mergeInt(cli, "distrib.outputWidth", yaml?.outputWidth, props.outputWidth),
            outputHeight = mergeInt(cli, "distrib.outputHeight", yaml?.outputHeight, props.outputHeight)
        )
    }

    private fun mergeManimConfig(env: ManimConfig, props: ManimConfig, yaml: ManimConfig?, cli: Map<String, Any?>): ManimConfig {
        return ManimConfig(
            executablePath = mergeStr(cli, "manim.executablePath", yaml?.executablePath, props.executablePath, env.executablePath),
            quality = mergeStr(cli, "manim.quality", yaml?.quality, props.quality, env.quality),
            scriptsDir = mergeStr(cli, "manim.scriptsDir", yaml?.scriptsDir, props.scriptsDir, env.scriptsDir),
            outputDir = mergeStr(cli, "manim.outputDir", yaml?.outputDir, props.outputDir, env.outputDir),
            parallelRender = mergeBoolean(cli, "manim.parallelRender", yaml?.parallelRender, props.parallelRender),
            parallelRenderThreads = mergeInt(cli, "manim.parallelRenderThreads", yaml?.parallelRenderThreads, props.parallelRenderThreads)
        )
    }

    private fun mergeOutputConfig(env: OutputConfig, props: OutputConfig, yaml: OutputConfig?, cli: Map<String, Any?>): OutputConfig {
        return OutputConfig(
            videoDestinationDir = mergeStr(cli, "output.videoDestinationDir", yaml?.videoDestinationDir, props.videoDestinationDir, env.videoDestinationDir),
            versioning = mergeVersioning(cli, "output.versioning", yaml?.versioning, props.versioning),
            versionPrefix = mergeStr(cli, "output.versionPrefix", yaml?.versionPrefix, props.versionPrefix, env.versionPrefix),
            format = mergeOutputFormat(cli, "output.format", yaml?.format, props.format)
        )
    }

    private fun mergeStrictModeConfig(env: StrictModeConfig, props: StrictModeConfig, yaml: StrictModeConfig?, cli: Map<String, Any?>): StrictModeConfig {
        return StrictModeConfig(
            enabled = mergeBoolean(cli, "strictMode.enabled", yaml?.enabled, props.enabled)
        )
    }

    private fun mergeValidationConfig(env: ValidationConfig, props: ValidationConfig, yaml: ValidationConfig?, cli: Map<String, Any?>): ValidationConfig {
        return ValidationConfig(
            durationEnabled = mergeBoolean(cli, "validation.durationEnabled", yaml?.durationEnabled, props.durationEnabled),
            toleranceSecs = mergeDouble(cli, "validation.toleranceSecs", yaml?.toleranceSecs, props.toleranceSecs)
        )
    }

    private fun mergeAudioPostConfig(env: AudioPostConfig, props: AudioPostConfig, yaml: AudioPostConfig?, cli: Map<String, Any?>): AudioPostConfig {
        return AudioPostConfig(
            bgmEnabled = mergeBoolean(cli, "audioPost.bgmEnabled", yaml?.bgmEnabled, props.bgmEnabled),
            bgmFile = mergeStr(cli, "audioPost.bgmFile", yaml?.bgmFile, props.bgmFile, env.bgmFile),
            bgmLevel = mergeDouble(cli, "audioPost.bgmLevel", yaml?.bgmLevel, props.bgmLevel),
            loudnessTarget = mergeDouble(cli, "audioPost.loudnessTarget", yaml?.loudnessTarget, props.loudnessTarget),
            duckingEnabled = mergeBoolean(cli, "audioPost.duckingEnabled", yaml?.duckingEnabled, props.duckingEnabled)
        )
    }

    private fun mergeTranscriptConfig(env: TranscriptConfig, props: TranscriptConfig, yaml: TranscriptConfig?, cli: Map<String, Any?>): TranscriptConfig {
        return TranscriptConfig(
            enabled = mergeBoolean(cli, "transcript.enabled", yaml?.enabled, props.enabled),
            strategy = mergeTranscriptStrategy(cli, "transcript.strategy", yaml?.strategy, props.strategy)
        )
    }

    private fun mergeRemotionConfig(env: RemotionConfig, props: RemotionConfig, yaml: RemotionConfig?, cli: Map<String, Any?>): RemotionConfig {
        return RemotionConfig(
            projectDir = mergeStr(cli, "remotion.projectDir", yaml?.projectDir, props.projectDir, env.projectDir),
            nodeExecutablePath = mergeStr(cli, "remotion.nodeExecutablePath", yaml?.nodeExecutablePath, props.nodeExecutablePath, env.nodeExecutablePath),
            concurrency = mergeInt(cli, "remotion.concurrency", yaml?.concurrency, props.concurrency),
            fps = mergeInt(cli, "remotion.fps", yaml?.fps, props.fps)
        )
    }

    private fun mergeContextConfig(env: ContextConfig, props: ContextConfig, yaml: ContextConfig?, cli: Map<String, Any?>): ContextConfig {
        return ContextConfig(
            docsGlobs = mergeStrList(cli, "context.docsGlobs", yaml?.docsGlobs, props.docsGlobs, env.docsGlobs),
            scenarioFile = mergeStr(cli, "context.scenarioFile", yaml?.scenarioFile, props.scenarioFile ?: "", env.scenarioFile ?: "").takeIf { it.isNotBlank() },
            glossaryFile = mergeStr(cli, "context.glossaryFile", yaml?.glossaryFile, props.glossaryFile ?: "", env.glossaryFile ?: "").takeIf { it.isNotBlank() }
        )
    }

    private fun mergePodcastConfig(env: PodcastConfig, props: PodcastConfig, yaml: PodcastConfig?, cli: Map<String, Any?>): PodcastConfig {
        return PodcastConfig(
            enabled = mergeBoolean(cli, "podcast.enabled", yaml?.enabled, props.enabled),
            outputFile = mergeStr(cli, "podcast.outputFile", yaml?.outputFile, props.outputFile, env.outputFile)
        )
    }

    private fun mergeVersioning(
        cli: Map<String, Any?>,
        key: String,
        yaml: VersioningStrategy?,
        props: VersioningStrategy
    ): VersioningStrategy {
        val cliValue = cli[key]?.toString()
        if (!cliValue.isNullOrBlank()) return VersioningStrategy.fromString(cliValue)
        yaml?.let { return it }
        return props
    }

    private fun mergeCaptureStrategy(
        cli: Map<String, Any?>,
        key: String,
        yaml: CaptureStrategy?,
        props: CaptureStrategy
    ): CaptureStrategy {
        val cliValue = cli[key]?.toString()
        if (!cliValue.isNullOrBlank()) return CaptureStrategy.fromString(cliValue)
        yaml?.let { return it }
        return props
    }

    private fun mergeOutputFormat(
        cli: Map<String, Any?>,
        key: String,
        yaml: OutputFormat?,
        props: OutputFormat
    ): OutputFormat {
        val cliValue = cli[key]?.toString()
        if (!cliValue.isNullOrBlank()) return OutputFormat.fromString(cliValue)
        yaml?.let { return it }
        return props
    }

    private fun mergeTranscriptStrategy(
        cli: Map<String, Any?>,
        key: String,
        yaml: TranscriptStrategy?,
        props: TranscriptStrategy
    ): TranscriptStrategy {
        val cliValue = cli[key]?.toString()
        if (!cliValue.isNullOrBlank()) return TranscriptStrategy.fromString(cliValue)
        yaml?.let { return it }
        return props
    }

    // ─── Generic merge helpers (CLI > YAML > Props > ENV) ────────
    //
    // String fields: isNotBlank() is the "explicitly set" heuristic — an
    // explicit blank never overrides a non-blank lower-priority source.
    // Boolean/Int/Double fields: the YAML/props value is always preferred
    // over ENV (no "blank" concept for non-strings).

    private fun mergeStr(
        cli: Map<String, Any?>,
        key: String,
        yaml: String?,
        props: String,
        env: String
    ): String {
        val cliValue = cli[key]?.toString()
        if (!cliValue.isNullOrBlank()) return cliValue
        if (!yaml.isNullOrBlank()) return yaml
        if (props.isNotBlank()) return props
        return env
    }

    private fun mergeInt(
        cli: Map<String, Any?>,
        key: String,
        yaml: Int?,
        props: Int
    ): Int {
        cli.cliInt(key)?.let { return it }
        yaml?.let { return it }
        return props
    }

    private fun mergeDouble(
        cli: Map<String, Any?>,
        key: String,
        yaml: Double?,
        props: Double
    ): Double {
        cli.cliDouble(key)?.let { return it }
        yaml?.let { return it }
        return props
    }

    private fun mergeBoolean(
        cli: Map<String, Any?>,
        key: String,
        yaml: Boolean?,
        props: Boolean
    ): Boolean {
        cli.cliBoolean(key)?.let { return it }
        yaml?.let { return it }
        return props
    }

    /**
     * Merges a `List<String>` field from the 4 sources (CLI > YAML > props > ENV).
     *
     * CLI and props/ENV are comma-separated strings; YAML provides a native list.
     * An empty/blank CLI string does NOT override (falls back to YAML/props/ENV).
     * A non-empty YAML list always wins over props/ENV (consistent with the
     * scalar `mergeStr` heuristic).
     */
    private fun mergeStrList(
        cli: Map<String, Any?>,
        key: String,
        yaml: List<String>?,
        props: List<String>,
        env: List<String>
    ): List<String> {
        val cliValue = cli[key]?.toString()
        if (!cliValue.isNullOrBlank()) return splitCommaList(cliValue)
        if (!yaml.isNullOrEmpty()) return yaml
        if (props.isNotEmpty()) return props
        return env
    }

    private fun Map<String, Any?>.cliInt(key: String): Int? =
        this[key]?.let { (it as? Int) ?: it.toString().toIntOrNull() }

    private fun Map<String, Any?>.cliDouble(key: String): Double? =
        this[key]?.let { (it as? Double) ?: it.toString().toDoubleOrNull() }

    private fun Map<String, Any?>.cliBoolean(key: String): Boolean? =
        this[key]?.let { (it as? Boolean) ?: it.toString().toBoolean() }

    /** Splits a comma-separated string into a trimmed list of non-blank entries. */
    internal fun splitCommaList(value: String): List<String> =
        value.split(",").map { it.trim() }.filter { it.isNotBlank() }
}