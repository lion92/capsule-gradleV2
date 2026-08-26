package capsule

import capsule.transcript.TranscriptStrategy
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.UnknownPluginException
import java.io.File

class CapsulePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        try {
            project.plugins.apply("education.cccp.slider")
        } catch (e: UnknownPluginException) {
            project.logger.info(
                "slider-gradle not on classpath, skipping auto-apply. " +
                    "Add education.cccp.slider to buildscript dependencies if you need reveal.js deck generation."
            )
        }

        val capsuleExt = project.extensions.create("capsule", CapsuleExtension::class.java)
        CapsuleManager(project).registerTasks()

        // After DSL is configured, merge 4 sources and push resolved values
        project.afterEvaluate {
            val configPath = capsuleExt.configPath.get()
            val configFile = File(project.projectDir, configPath)
            val yamlLoaded = configFile.exists() && configFile.length() > 0

            // Load YAML config (defaults if file doesn't exist)
            val yamlConfig = CapsuleConfigLoader.load(configFile)

            // Collect CLI -P params prefixed with "capsule."
            val cliParams = collectCliParams(project)

            // Merge: CLI > YAML > gradle.properties > ENV
            val mergedConfig = CapsuleConfigMerger.merge(project.projectDir, yamlConfig, cliParams, yamlLoaded)

            // Push resolved values into DSL Extension properties
            pushConfigIntoExtension(mergedConfig, capsuleExt)

            // CR-8 — Structured config logging (4 section lines instead of 1 monolithic).
            // Logged from the extension, not from mergedConfig: an explicit DSL value
            // (`capsule { parallelCaptureEnabled.set(true) }`) never reaches the merged
            // config, so logging the latter would announce a configuration the build
            // does not actually run with.
            CapsuleConfigLogger.formatConfigLog(effectiveConfig(mergedConfig, capsuleExt)).forEach { line ->
                project.logger.lifecycle(line)
            }
        }
    }

    /**
     * Rebuilds the configuration actually in force after [pushConfigIntoExtension],
     * i.e. the merged 4-source config with the explicit DSL values layered on top.
     * Only the sections rendered by [CapsuleConfigLogger] are refreshed.
     */
    internal fun effectiveConfig(merged: CapsuleConfig, ext: CapsuleExtension): CapsuleConfig =
        merged.copy(
            tts = merged.tts.copy(
                engine = ext.ttsEngine.get(),
                voice = ext.ttsVoice.get(),
                language = ext.ttsLanguage.get(),
            ),
            capture = merged.capture.copy(
                viewportWidth = ext.viewportWidth.get(),
                viewportHeight = ext.viewportHeight.get(),
                parallelCaptureEnabled = ext.parallelCaptureEnabled.get(),
                subtitleEnabled = ext.subtitleEnabled.get(),
                subtitleFormat = ext.subtitleFormat.get(),
                subtitleBurnIn = ext.subtitleBurnIn.get(),
            ),
            manim = merged.manim.copy(
                executablePath = ext.manimExecutablePath.get(),
                quality = ext.manimQuality.get(),
                scriptsDir = ext.manimScriptsDir.get(),
                outputDir = ext.manimOutputDir.get(),
            ),
        )

    /**
     * Collects CLI -P params that start with "capsule." and converts them
     * to the flat key format used by ConfigMerger (e.g. "capsule.tts.engine" → "tts.engine").
     */
    internal fun collectCliParams(project: Project): Map<String, Any?> {
        val cliParams = mutableMapOf<String, Any?>()
        val prefix = "capsule."
        project.properties.forEach { (key, value) ->
            if (key.startsWith(prefix)) {
                val flatKey = key.removePrefix(prefix)
                cliParams[flatKey] = value
            }
        }
        return cliParams
    }

    /**
     * Pushes the merged configuration values into the Gradle Extension properties.
     * Only sets properties that still hold their original convention value,
     * preserving explicit DSL configuration: DSL > CLI > YAML > props > ENV.
     */
    internal fun pushConfigIntoExtension(config: CapsuleConfig, ext: CapsuleExtension) {
        val conventions = ext.conventions

        // Input section — only fill if still at convention (DSL didn't override)
        if (ext.outputDir.get() == conventions.outputDir) ext.outputDir.set(config.input.outputDir)
        if (ext.sliderScriptDir.get() == conventions.sliderScriptDir) ext.sliderScriptDir.set(config.input.sliderScriptDir)
        if (ext.deckSourceDir.get() == conventions.deckSourceDir) ext.deckSourceDir.set(config.input.deckSourceDir)
        if (ext.sliderBuildDir.get() == conventions.sliderBuildDir) ext.sliderBuildDir.set(config.input.sliderBuildDir)
        if (ext.chromiumExecutablePath.get() == conventions.chromiumExecutablePath) ext.chromiumExecutablePath.set(config.input.chromiumExecutablePath)

        // TTS section
        if (ext.ttsEngine.get() == conventions.ttsEngine) ext.ttsEngine.set(config.tts.engine)
        if (ext.ttsVoice.get() == conventions.ttsVoice) ext.ttsVoice.set(config.tts.voice)
        if (ext.piperExecutablePath.get() == conventions.piperExecutablePath) ext.piperExecutablePath.set(config.tts.piperExecutablePath)
        if (ext.ttsFallbackEnabled.get() == conventions.ttsFallbackEnabled) ext.ttsFallbackEnabled.set(config.tts.fallbackEnabled)
        if (ext.espeakVoice.get() == conventions.espeakVoice) ext.espeakVoice.set(config.tts.espeakVoice)
        if (ext.espeakSpeed.get() == conventions.espeakSpeed) ext.espeakSpeed.set(config.tts.espeakSpeed)
        if (ext.ttsLanguage.get() == conventions.ttsLanguage) ext.ttsLanguage.set(config.tts.language)

        // Capture section
        if (ext.viewportWidth.get() == conventions.viewportWidth) ext.viewportWidth.set(config.capture.viewportWidth)
        if (ext.viewportHeight.get() == conventions.viewportHeight) ext.viewportHeight.set(config.capture.viewportHeight)
        if (ext.playwrightTimeout.get() == conventions.playwrightTimeout) ext.playwrightTimeout.set(config.capture.playwrightTimeout)
        if (ext.slideDurationSeconds.get() == conventions.slideDurationSeconds) ext.slideDurationSeconds.set(config.capture.slideDurationSeconds)
        if (ext.parallelCaptureEnabled.get() == conventions.parallelCaptureEnabled) ext.parallelCaptureEnabled.set(config.capture.parallelCaptureEnabled)
        if (ext.parallelCaptureThreads.get() == conventions.parallelCaptureThreads) ext.parallelCaptureThreads.set(config.capture.parallelCaptureThreads)
        if (ext.captureTimeoutMinutes.get() == conventions.captureTimeoutMinutes) ext.captureTimeoutMinutes.set(config.capture.captureTimeoutMinutes)
        if (ext.subtitleEnabled.get() == conventions.subtitleEnabled) ext.subtitleEnabled.set(config.capture.subtitleEnabled)
        if (ext.subtitleFormat.get() == conventions.subtitleFormat) ext.subtitleFormat.set(config.capture.subtitleFormat)
        if (ext.subtitleBurnIn.get() == conventions.subtitleBurnIn) ext.subtitleBurnIn.set(config.capture.subtitleBurnIn)
        if (ext.subtitleBurnInFontSize.get() == conventions.subtitleBurnInFontSize) ext.subtitleBurnInFontSize.set(config.capture.subtitleBurnInFontSize)
        if (ext.subtitleBurnInFontColor.get() == conventions.subtitleBurnInFontColor) ext.subtitleBurnInFontColor.set(config.capture.subtitleBurnInFontColor)
        if (ext.subtitleBurnInOutlineColor.get() == conventions.subtitleBurnInOutlineColor) ext.subtitleBurnInOutlineColor.set(config.capture.subtitleBurnInOutlineColor)
        if (ext.subtitleBurnInPosition.get() == conventions.subtitleBurnInPosition) ext.subtitleBurnInPosition.set(config.capture.subtitleBurnInPosition)
        if (!ext.captureStrategy.isPresent || ext.captureStrategy.get() == conventions.captureStrategy) ext.captureStrategy.set(config.capture.strategy)

        // Output format (CAP-MP4)
        if (!ext.outputFormat.isPresent || ext.outputFormat.get() == conventions.outputFormat) ext.outputFormat.set(config.output.format)

        // Distrib section
        if (ext.ffmpegExecutablePath.get() == conventions.ffmpegExecutablePath) ext.ffmpegExecutablePath.set(config.distrib.ffmpegExecutablePath)
        if (ext.distribOutputWidth.get() == conventions.distribOutputWidth) ext.distribOutputWidth.set(config.distrib.outputWidth)
        if (ext.distribOutputHeight.get() == conventions.distribOutputHeight) ext.distribOutputHeight.set(config.distrib.outputHeight)

        // Manim section
        if (ext.manimExecutablePath.get() == conventions.manimExecutablePath) ext.manimExecutablePath.set(config.manim.executablePath)
        if (ext.manimQuality.get() == conventions.manimQuality) ext.manimQuality.set(config.manim.quality)
        if (ext.manimScriptsDir.get() == conventions.manimScriptsDir) ext.manimScriptsDir.set(config.manim.scriptsDir)
        if (ext.manimOutputDir.get() == conventions.manimOutputDir) ext.manimOutputDir.set(config.manim.outputDir)
        if (ext.manimParallelRender.get() == conventions.manimParallelRender) ext.manimParallelRender.set(config.manim.parallelRender)
        if (ext.manimParallelRenderThreads.get() == conventions.manimParallelRenderThreads) ext.manimParallelRenderThreads.set(config.manim.parallelRenderThreads)

        // StrictMode section (CAP-CR3-2)
        if (!ext.strictMode.isPresent || ext.strictMode.get() == false) ext.strictMode.set(config.strictMode.enabled)

        // Context section (CAP-DOCCONTEXT-1 + CAP-SPD-3 + CAP-GLOSSARY-2)
        if (ext.docsGlobs.get().isEmpty()) ext.docsGlobs.set(config.context.docsGlobs)
        if (!ext.scenarioFile.isPresent || ext.scenarioFile.get().isBlank()) {
            config.context.scenarioFile?.let { ext.scenarioFile.set(it) }
        }
        if (!ext.glossaryFile.isPresent || ext.glossaryFile.get().isBlank()) {
            config.context.glossaryFile?.let { ext.glossaryFile.set(it) }
        }

        // Validation section (CAP-CR3-1)
        if (!ext.durationValidationEnabled.isPresent || ext.durationValidationEnabled.get() == conventions.durationValidationEnabled) {
            ext.durationValidationEnabled.set(config.validation.durationEnabled)
        }
        if (!ext.durationValidationToleranceSecs.isPresent || ext.durationValidationToleranceSecs.get() == conventions.durationValidationToleranceSecs) {
            ext.durationValidationToleranceSecs.set(config.validation.toleranceSecs)
        }

        // AudioPost section (CAP-AUDIO)
        if (!ext.audioPostBgmEnabled.isPresent || ext.audioPostBgmEnabled.get() == conventions.audioPostBgmEnabled) {
            ext.audioPostBgmEnabled.set(config.audioPost.bgmEnabled)
        }
        if (!ext.audioPostBgmFile.isPresent || ext.audioPostBgmFile.get() == conventions.audioPostBgmFile) {
            ext.audioPostBgmFile.set(config.audioPost.bgmFile)
        }
        if (!ext.audioPostBgmLevel.isPresent || ext.audioPostBgmLevel.get() == conventions.audioPostBgmLevel) {
            ext.audioPostBgmLevel.set(config.audioPost.bgmLevel)
        }
        if (!ext.audioPostLoudnessTarget.isPresent || ext.audioPostLoudnessTarget.get() == conventions.audioPostLoudnessTarget) {
            ext.audioPostLoudnessTarget.set(config.audioPost.loudnessTarget)
        }
        if (!ext.audioPostDuckingEnabled.isPresent || ext.audioPostDuckingEnabled.get() == conventions.audioPostDuckingEnabled) {
            ext.audioPostDuckingEnabled.set(config.audioPost.duckingEnabled)
        }

        // Remotion section (CAP-ANIM)
        if (ext.remotionProjectDir.get() == conventions.remotionProjectDir) {
            ext.remotionProjectDir.set(config.remotion.projectDir)
        }
        if (ext.remotionNodeExecutablePath.get() == conventions.remotionNodeExecutablePath) {
            ext.remotionNodeExecutablePath.set(config.remotion.nodeExecutablePath)
        }
        if (ext.remotionConcurrency.get() == conventions.remotionConcurrency) {
            ext.remotionConcurrency.set(config.remotion.concurrency)
        }
        if (ext.remotionFps.get() == conventions.remotionFps) {
            ext.remotionFps.set(config.remotion.fps)
        }

        // Transcript section (CAP-TRANSCRIPT)
        if (!ext.transcriptEnabled.isPresent || ext.transcriptEnabled.get() == conventions.transcriptEnabled) {
            ext.transcriptEnabled.set(config.transcript.enabled)
        }
        if (!ext.transcriptStrategy.isPresent || ext.transcriptStrategy.get() == conventions.transcriptStrategy) {
            ext.transcriptStrategy.set(config.transcript.strategy)
        }

        // Podcast section (CAP-PODCAST)
        if (!ext.podcastEnabled.isPresent || ext.podcastEnabled.get() == conventions.podcastEnabled) {
            ext.podcastEnabled.set(config.podcast.enabled)
        }
        if (!ext.podcastOutputFile.isPresent || ext.podcastOutputFile.get() == conventions.podcastOutputFile) {
            ext.podcastOutputFile.set(config.podcast.outputFile)
        }

        // Preview section (CAP-PREVIEW)
        if (!ext.previewOnly.isPresent || ext.previewOnly.get() == conventions.previewOnly) {
            ext.previewOnly.set(config.preview.enabled)
        }

        // Chapters section (CAP-CHAPITRE)
        if (!ext.chaptersEnabled.isPresent || ext.chaptersEnabled.get() == conventions.chaptersEnabled) {
            ext.chaptersEnabled.set(config.chapters.enabled)
        }
        if (!ext.chaptersIntroText.isPresent || ext.chaptersIntroText.get() == conventions.chaptersIntroText) {
            ext.chaptersIntroText.set(config.chapters.introText)
        }
        if (!ext.chaptersOutroText.isPresent || ext.chaptersOutroText.get() == conventions.chaptersOutroText) {
            ext.chaptersOutroText.set(config.chapters.outroText)
        }
    }
}
