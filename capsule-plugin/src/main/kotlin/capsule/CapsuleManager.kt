package capsule

import capsule.ai.CapsuleLlmService.registerLlmBuildService
import capsule.chapters.CardRenderer
import capsule.chapters.CardRendererImpl
import capsule.chapters.ChapterMarker
import capsule.chapters.ChapterMarkerImpl
import capsule.chapters.NoOpCardRenderer
import capsule.chapters.NoOpChapterMarker
import capsule.i18n.CapsuleMessages
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import java.io.File

class CapsuleManager(private val project: Project) {

    fun registerTasks() {
        project.registerExtractSpeakerNotesTask()
        project.registerGenerateCapsuleScriptTask()
        project.registerGenerateCapsuleTask()
        project.registerGenerateCapsuleVideoTask()
        project.registerGenerateCapsuleVideoAllLanguagesTask()
        project.registerDeployCapsuleTask()
        project.registerCollectCapsuleContextTask()
        project.registerTransformCapsuleContextTask()
        project.registerScaffoldCapsuleContextTask()
        project.registerAiSmokeTestTask()
        project.registerCollectAugmentedContextTask()
        project.registerGenerateCapsuleContentTask()
        project.registerGenerateTranscriptTask()
        project.registerGeneratePodcastTask()
        project.registerDistributeCapsuleVideoTask()
        project.registerValidateCapsuleVideoDurationTask()
        project.registerGenerateCapsuleChaptersTask()
    }

    private fun Project.registerExtractSpeakerNotesTask() {
        capsule.feed.CapsuleFeedTaskRegistrar.register(this)
        capsule.feed.CapsuleFeedTaskRegistrar.registerTranslateAndExtractSpeakerNotes(this)
        capsule.feed.CapsuleFeedTaskRegistrar.registerTranslateAndGenerateCapsuleVideos(this)
        capsule.feed.CapsuleFeedTaskRegistrar.registerGenerateCapsuleContentAndVideos(this)
    }

    private fun Project.registerGenerateCapsuleScriptTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        tasks.register("generateCapsuleScript", CapsuleScriptTask::class.java) { task ->
            task.group = CapsuleMessages.get("task.group.generate", lang)
            task.description = CapsuleMessages.get("task.generateCapsuleScript.description", lang)
            task.dependsOn(capsule.feed.CapsuleFeedTaskNames.EXTRACT_SPEAKER_NOTES)
        }
    }

    private fun Project.registerGenerateCapsuleTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        tasks.register("generateCapsule", CapsuleBuildTask::class.java) { task ->
            task.group = CapsuleMessages.get("task.group.generate", lang)
            task.description = CapsuleMessages.get("task.generateCapsule.description", lang)
            task.dependsOn("generateCapsuleScript")
        }
    }

    private fun Project.registerGenerateCapsuleVideoTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        tasks.register("generateCapsuleVideo", CapsuleVideoTask::class.java) { task ->
            task.group = CapsuleMessages.get("task.group.generate", lang)
            task.description = CapsuleMessages.get("task.generateCapsuleVideo.description", lang)
            task.dependsOn("generateCapsule")
        }
    }

    private fun Project.registerGenerateCapsuleVideoAllLanguagesTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        tasks.register(
            "generateCapsuleVideoAllLanguages",
            capsule.multilang.GenerateCapsuleVideoAllLanguagesTask::class.java,
        ) { task ->
            task.group = CapsuleMessages.get("task.group.generate", lang)
            task.description = CapsuleMessages.get("task.generateCapsuleVideoAllLanguages.description", lang)
        }
    }

    private fun Project.registerDeployCapsuleTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        tasks.register("deployCapsule", CapsuleDistribTask::class.java) { task ->
            task.group = CapsuleMessages.get("task.group.deploy", lang)
            task.description = CapsuleMessages.get("task.deployCapsule.description", lang)
            task.dependsOn("generateCapsuleVideo")
        }
    }

    private fun Project.registerDistributeCapsuleVideoTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        tasks.register("distributeCapsuleVideo", DistributeCapsuleVideoTask::class.java) { task ->
            task.group = CapsuleMessages.get("task.group.distribute", lang)
            task.description = CapsuleMessages.get("task.distributeCapsuleVideo.description", lang)
            val config = CapsuleConfigLoader.load(File(project.projectDir, "capsule-context.yml"))
            val merged = CapsuleConfigMerger.merge(project.projectDir, config, emptyMap())
            task.videoDestinationDir.set(merged.output.videoDestinationDir)
            task.versioning.set(merged.output.versioning.name)
            task.versionPrefix.set(merged.output.versionPrefix)
            task.format.set(merged.output.format.name)
        }
    }

    private fun Project.registerValidateCapsuleVideoDurationTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        val capsuleExt = project.extensions.findByType(CapsuleExtension::class.java)
        tasks.register(
            "validateCapsuleVideoDuration",
            capsule.validation.ValidateCapsuleVideoDurationTask::class.java,
        ) { task ->
            task.group = CapsuleMessages.get("task.group.verification", lang)
            task.description = CapsuleMessages.get("task.validateCapsuleVideoDuration.description", lang)
            task.durationEnabled.set(project.provider {
                capsuleExt?.durationValidationEnabled?.get()
                    ?: project.findProperty("capsule.validation.durationEnabled")?.toString()?.toBoolean()
                    ?: false
            })
            task.toleranceSecs.set(project.provider {
                capsuleExt?.durationValidationToleranceSecs?.get()
                    ?: project.findProperty("capsule.validation.toleranceSecs")?.toString()?.toDoubleOrNull()
                    ?: 2.0
            })
            task.videoFile.convention(
                project.layout.buildDirectory.file(
                    project.provider {
                        val capDir = project.layout.buildDirectory.dir(
                            capsuleExt?.outputDir?.get() ?: "capsule"
                        ).get().asFile
                        capDir.listFiles { f -> f.name.endsWith(".webm") || f.name.endsWith(".mp4") }
                            ?.firstOrNull()
                            ?.let { it.absolutePath }
                            ?: (capsuleExt?.outputDir?.get() ?: "capsule") + "/video-not-found"
                    }
                )
            )
            task.audioFiles.from(project.provider {
                val capDir = project.layout.buildDirectory.dir(
                    capsuleExt?.outputDir?.get() ?: "capsule"
                ).get().asFile
                val audioDirs = capDir.listFiles { f -> f.isDirectory }?.toList() ?: emptyList()
                audioDirs.flatMap { dir ->
                    dir.listFiles { f -> f.name.endsWith(".mp3") }?.toList() ?: emptyList()
                }
            })
        }
    }

    private fun Project.registerGenerateCapsuleChaptersTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        val capsuleExt = project.extensions.findByType(CapsuleExtension::class.java)
        tasks.register(
            "generateCapsuleChapters",
            capsule.chapters.GenerateCapsuleChaptersTask::class.java,
        ) { task ->
            task.group = CapsuleMessages.get("task.group.generate", lang)
            task.description = CapsuleMessages.get("task.generateCapsuleChapters.description", lang)
            task.enabled.convention(project.provider {
                capsuleExt?.chaptersEnabled?.get()
                    ?: project.findProperty("capsule.chapters.enabled")?.toString()?.toBoolean()
                    ?: false
            })
            task.introText.convention(project.provider {
                capsuleExt?.chaptersIntroText?.orNull
                    ?: project.findProperty("capsule.chapters.introText")?.toString()
                    ?: ""
            })
            task.outroText.convention(project.provider {
                capsuleExt?.chaptersOutroText?.orNull
                    ?: project.findProperty("capsule.chapters.outroText")?.toString()
                    ?: ""
            })
            task.slideSegmentsJson.set("[]")
            task.outputDir.convention(project.layout.buildDirectory.dir("capsule/chapters"))
        }
    }

    private fun Project.registerCollectCapsuleContextTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        tasks.register("collectCapsuleContext", CapsuleCompositeContextTask::class.java) { task ->
            task.group = CapsuleMessages.get("task.group.collect", lang)
            task.description = CapsuleMessages.get("task.collectCapsuleContext.description", lang)
            task.dependsOn("deployCapsule")
        }
    }

    private fun Project.registerTransformCapsuleContextTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        tasks.register("transformCapsuleContext", CapsuleParseContextTask::class.java) { task ->
            task.group = CapsuleMessages.get("task.group.transform", lang)
            task.description = CapsuleMessages.get("task.transformCapsuleContext.description", lang)
            task.contextFile.convention(
                project.layout.buildDirectory.file("capsule/capsule-context.json")
            )
            task.outputFile.convention(
                project.layout.buildDirectory.file("capsule/capsule-parse-results.json")
            )
        }

        tasks.register("collectCapsuleRetrieve", CapsuleParseContextTask::class.java) { task ->
            task.group = CapsuleMessages.get("task.group.collect", lang)
            task.description = CapsuleMessages.get("task.collectCapsuleRetrieve.description", lang)
            val outputFile = project.findProperty("outputFile") as? String
            if (outputFile != null) {
                task.outputFile.set(File(outputFile))
            }
            task.contextFile.convention(
                project.layout.buildDirectory.file("capsule/capsule-context.json")
            )
        }
    }

    private fun Project.registerScaffoldCapsuleContextTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        tasks.register("scaffoldCapsuleContext", CapsuleScaffoldTask::class.java) { task ->
            task.group = CapsuleMessages.get("task.group.generate", lang)
            task.description = CapsuleMessages.get("task.scaffoldCapsuleContext.description", lang)
        }
    }

    private fun Project.registerAiSmokeTestTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        val llmServiceProvider = registerLlmBuildService()
        tasks.register("capsuleAiSmokeTest", capsule.ai.CapsuleAiSmokeTestTask::class.java) { task ->
            task.group = CapsuleMessages.get("task.group.generate", lang)
            task.description = CapsuleMessages.get("task.capsuleAiSmokeTest.description", lang)
            task.llmService.set(llmServiceProvider)
            task.usesService(llmServiceProvider)
        }
    }

    private fun Project.registerCollectAugmentedContextTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        val capsuleExt = project.extensions.findByType(CapsuleExtension::class.java)
        tasks.register(
            "collectCapsuleAugmentedContext",
            capsule.context.CollectCapsuleAugmentedContextTask::class.java,
        ) { task ->
            task.group = CapsuleMessages.get("task.group.collect", lang)
            task.description = CapsuleMessages.get("task.collectCapsuleAugmentedContext.description", lang)
            task.eagerFiles.from(
                project.layout.projectDirectory.file(".agents/INDEX.adoc"),
                project.layout.projectDirectory.file("PROMPT_REPRISE.adoc"),
                project.layout.projectDirectory.file("AGENT.adoc"),
            )

            // CAP-DOCCONTEXT-3 — resolve docsGlobs lazily (extension afterEvaluate > CLI).
            // The extension is populated by pushConfigIntoExtension in afterEvaluate,
            // so we use a provider to defer resolution until task execution.
            task.docsGlobs.set(project.provider {
                val extGlobs = capsuleExt?.docsGlobs?.orNull ?: emptyList()
                if (extGlobs.isNotEmpty()) extGlobs
                else project.findProperty("capsule.context.docsGlobs")?.toString()
                    ?.let { CapsuleConfigMerger.splitCommaList(it) }
                    ?: emptyList()
            })
            task.docsFiles.from(project.provider {
                val globs = task.docsGlobs.get()
                if (globs.isEmpty()) emptyList<Any>() else listOf(resolveGlobFiles(globs))
            })

            task.ragContent.set(project.findProperty("context.ragContent")?.toString().orEmpty())
            task.graphifyContent.set(project.findProperty("context.graphifyContent")?.toString().orEmpty())
            task.docsContent.set(project.findProperty("context.docsContent")?.toString().orEmpty())

            // CAP-SPD-3 — resolve scenarioFile lazily (extension afterEvaluate > CLI).
            task.scenarioFile.from(project.provider {
                val extPath = capsuleExt?.scenarioFile?.orNull
                val path = when {
                    !extPath.isNullOrBlank() -> extPath
                    else -> project.findProperty("capsule.context.scenarioFile")?.toString().orEmpty()
                }
                if (path.isBlank()) emptyList<Any>()
                else listOf(project.file(path))
            })

            // CAP-GLOSSARY-2 — resolve glossaryFile lazily (extension afterEvaluate > CLI).
            task.glossaryFile.from(project.provider {
                val extPath = capsuleExt?.glossaryFile?.orNull
                val path = when {
                    !extPath.isNullOrBlank() -> extPath
                    else -> project.findProperty("capsule.context.glossaryFile")?.toString().orEmpty()
                }
                if (path.isBlank()) emptyList<Any>()
                else listOf(project.file(path))
            })

            task.tokenBudget.set(
                project.findProperty("context.tokenBudget")?.toString()?.toIntOrNull()
                    ?: contracts.context.ContextChannel.DEFAULT_TOKEN_BUDGET
            )
            task.outputFile.set(project.layout.buildDirectory.file("capsule/augmented-context.txt"))
            task.provenanceFile.set(project.layout.buildDirectory.file("capsule/context-provenance.json"))
        }
    }

    /**
     * Resolves Ant-style globs into a file collection relative to the project root
     * (CAP-DOCCONTEXT-3).
     */
    private fun Project.resolveGlobFiles(globs: List<String>): ConfigurableFileCollection {
        val collection = files()
        for (glob in globs) {
            val tree = fileTree(project.projectDir).matching { it.include(glob) }
            collection.from(tree)
        }
        return collection
    }

    private fun Project.registerGenerateCapsuleContentTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        val llmServiceProvider = registerLlmBuildService()
        tasks.register(
            "generateCapsuleContent",
            capsule.pipeline.GenerateCapsuleContentTask::class.java,
        ) { task ->
            task.group = CapsuleMessages.get("task.group.generate", lang)
            task.description = CapsuleMessages.get("task.generateCapsuleContent.description", lang)
            val deckProp = findProperty("deck.file") as? String
            if (deckProp != null) {
                task.deckFile.convention(
                    project.layout.file(project.provider { project.file(deckProp) })
                )
            } else {
                task.deckFile.convention(
                    project.layout.file(
                        project.provider {
                            capsule.feed.CapsuleAdocDir(project.projectDir).adocFiles()
                                .firstOrNull()
                                ?.let { project.file(it) }
                                ?: error("No deck found: set -Pdeck.file=<path> or add a .adoc in slides/misc")
                        }
                    )
                )
            }
            task.language.convention(findProperty("deck.language")?.toString() ?: "fr")
            task.augmentedContextFile.convention(
                project.layout.buildDirectory.file(
                    project.provider {
                        val f = project.layout.buildDirectory.file("capsule/augmented-context.txt").get().asFile
                        if (f.exists()) "capsule/augmented-context.txt" else null
                    }
                )
            )
            task.speakerNotesOutput.convention(
                project.layout.buildDirectory.file(
                    task.deckFile.map { deck ->
                        "capsule/${deck.asFile.nameWithoutExtension}-speaker-notes.adoc"
                    }
                )
            )
            task.ttsScriptOutput.convention(
                project.layout.buildDirectory.file(
                    task.deckFile.map { deck ->
                        "capsule/${deck.asFile.nameWithoutExtension}-script.txt"
                    }
                )
            )
            task.llmService.set(llmServiceProvider)
            task.usesService(llmServiceProvider)
        }
    }

    private fun Project.registerGenerateTranscriptTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        val capsuleExt = project.extensions.findByType(CapsuleExtension::class.java)
        val llmServiceProvider = registerLlmBuildService()
        tasks.register(
            "generateCapsuleTranscript",
            capsule.transcript.GenerateCapsuleTranscriptTask::class.java,
        ) { task ->
            task.group = CapsuleMessages.get("task.group.generate", lang)
            task.description = CapsuleMessages.get("task.generateCapsuleTranscript.description", lang)
            task.dependsOn("generateCapsuleContent")
            task.language.convention(findProperty("deck.language")?.toString() ?: "fr")
            task.strategy.convention(project.provider {
                capsuleExt?.transcriptStrategy?.orNull
                    ?: capsule.transcript.TranscriptStrategy.fromString(
                        findProperty("capsule.transcript.strategy")?.toString()
                    )
            })
            task.speakerNotesFile.convention(
                project.layout.buildDirectory.file(
                    project.provider {
                        val capDir = project.layout.buildDirectory.dir("capsule").get().asFile
                        capDir.listFiles { f -> f.name.endsWith("-speaker-notes.adoc") }
                            ?.firstOrNull()
                            ?.let { it.absolutePath }
                            ?: "capsule/no-speaker-notes.adoc"
                    }
                )
            )
            task.transcriptOutput.convention(
                project.layout.buildDirectory.file(
                    task.speakerNotesFile.map { notes ->
                        val deckName = notes.asFile.nameWithoutExtension
                            .removeSuffix("-speaker-notes")
                        "capsule/$deckName-transcript.adoc"
                    }
                )
            )
            task.llmService.set(llmServiceProvider)
            task.usesService(llmServiceProvider)
        }
    }

    private fun Project.registerGeneratePodcastTask() {
        val lang = CapsuleMessages.resolveLanguage(this)
        val capsuleExt = project.extensions.findByType(CapsuleExtension::class.java)
        tasks.register(
            "generateCapsulePodcast",
            capsule.podcast.GenerateCapsulePodcastTask::class.java,
        ) { task ->
            task.group = CapsuleMessages.get("task.group.generate", lang)
            task.description = CapsuleMessages.get("task.generateCapsulePodcast.description", lang)
            task.dependsOn("generateCapsuleVideo")
            task.podcastEnabled.set(project.provider {
                capsuleExt?.podcastEnabled?.get()
                    ?: project.findProperty("capsule.podcast.enabled")?.toString()?.toBoolean()
                    ?: false
            })
            task.deckName.set(project.provider {
                project.findProperty("deck.name")?.toString()
                    ?: project.layout.buildDirectory.dir("capsule").get().asFile
                        .listFiles { f -> f.isDirectory }?.firstOrNull()?.name
                    ?: "demo"
            })
            task.podcastOutput.convention(
                project.layout.buildDirectory.file(
                    project.provider {
                        val configured = capsuleExt?.podcastOutputFile?.get().orEmpty()
                        if (configured.isNotBlank()) configured
                        else "capsule/${task.deckName.get()}-podcast.mp3"
                    }
                )
            )
            task.audioFiles.from(project.provider {
                val capDir = project.layout.buildDirectory.dir("capsule").get().asFile
                val deckDir = capDir.listFiles { f -> f.isDirectory }?.firstOrNull()
                deckDir?.listFiles { f -> f.name.endsWith(".mp3") && f.name.startsWith("slide-") }
                    ?.toList() ?: emptyList()
            })
        }
    }

    companion object {
        /**
         * Resolves the appropriate ManimVideoMixer based on ffmpeg availability.
         * - If ffmpeg is not available, returns NoOpManimVideoMixer
         * - Otherwise, returns ManimVideoMixerImpl
         */
        @JvmStatic
        fun resolveManimVideoMixer(ffmpegPath: String = "ffmpeg", strict: Boolean = false): ManimVideoMixer {
            if (ffmpegPath == "noop") return NoOpManimVideoMixer()
            val mixer = ManimVideoMixerImpl(ffmpegPath)
            return if (mixer.isAvailable()) {
                mixer
            } else {
                StrictModeGuard.requireAvailable(strict, "ffmpeg (manim video mixer)", false, ffmpegPath)
                NoOpManimVideoMixer()
            }
        }

        /**
         * Resolves the appropriate ManimSlideReplacer.
         * Always returns ManimSlideReplacerImpl (always available, pure HTML manipulation).
         */
        @JvmStatic
        fun resolveManimSlideReplacer(): ManimSlideReplacer {
            return ManimSlideReplacerImpl()
        }

        /**
         * Resolves the appropriate ManimEngine based on configuration.
         * - If executablePath is "noop", returns NoOpManimEngine
         * - Otherwise, creates ManimEngineImpl(config) and falls back to NoOpManimEngine if unavailable
         */
        @JvmStatic
        fun resolveManimEngine(config: ManimConfig, strict: Boolean = false): ManimEngine {
            if (config.executablePath == "noop") {
                return NoOpManimEngine()
            }
            val engine = ManimEngineImpl(config)
            return if (engine.isAvailable()) {
                engine
            } else {
                StrictModeGuard.requireAvailable(strict, "manim", false, config.executablePath)
                NoOpManimEngine()
            }
        }

        /**
         * Resolves the appropriate ManimParallelRenderer based on parallelism.
         * - parallelism = 1: NoOpManimParallelRenderer (sequential fallback)
         * - parallelism > 1: ManimParallelRendererImpl with thread pool
         */
        @JvmStatic
        fun resolveManimParallelRenderer(parallelism: Int = 4): ManimParallelRenderer {
            return if (parallelism <= 1) {
                NoOpManimParallelRenderer()
            } else {
                ManimParallelRendererImpl(parallelism)
            }
        }

        /**
         * Resolves the appropriate SubtitleBurnInService based on ffmpeg availability.
         * - If ffmpegPath is "noop", returns NoOpSubtitleBurnInService
         * - Otherwise, returns SubtitleBurnInServiceImpl if ffmpeg is available
         */
        @JvmStatic
        fun resolveSubtitleBurnInService(ffmpegPath: String = "ffmpeg", style: SubtitleBurnInStyle = SubtitleBurnInStyle(), strict: Boolean = false): SubtitleBurnInService {
            if (ffmpegPath == "noop") return NoOpSubtitleBurnInService()
            val service = SubtitleBurnInServiceImpl(ffmpegPath, style)
            return if (service.isAvailable()) {
                service
            } else {
                StrictModeGuard.requireAvailable(strict, "ffmpeg (subtitle burn-in)", false, ffmpegPath)
                NoOpSubtitleBurnInService()
            }
        }

        /**
         * Resolves the appropriate [VideoFormatConverter] based on ffmpeg
         * availability (CAP-MP4 US-2). Pattern mirrors
         * [resolveSubtitleBurnInService]:
         * - If ffmpegPath is "noop", returns [NoOpVideoFormatConverter]
         * - Otherwise, returns [VideoFormatConverterImpl] if ffmpeg is available
         * - If unavailable and strict, [StrictModeGuard] throws
         * - If unavailable and non-strict, returns [NoOpVideoFormatConverter]
         *   (degraded mode — WebM kept, backward compat)
         */
        @JvmStatic
        fun resolveFormatConverter(ffmpegPath: String = "ffmpeg", strict: Boolean = false): VideoFormatConverter {
            if (ffmpegPath == "noop") return NoOpVideoFormatConverter()
            val converter = VideoFormatConverterImpl(ffmpegPath)
            return if (converter.isAvailable()) {
                converter
            } else {
                StrictModeGuard.requireAvailable(strict, "ffmpeg (format conversion)", false, ffmpegPath)
                NoOpVideoFormatConverter()
            }
        }

        /**
         * Resolves the appropriate [capsule.audio.AudioPostProcessor]
         * based on ffmpeg availability (CAP-AUDIO US-2). 5ème factory
         * `resolve*`, pattern mirrors [resolveFormatConverter]:
         * - If ffmpegPath is "noop", returns [capsule.audio.NoOpAudioPostProcessor]
         * - Otherwise, returns [capsule.audio.AudioPostProcessorImpl] if ffmpeg is available
         * - If unavailable and strict, [StrictModeGuard] throws
         * - If unavailable and non-strict, returns [capsule.audio.NoOpAudioPostProcessor]
         *   (degraded mode — original video kept, backward compat)
         */
        @JvmStatic
        fun resolveAudioPostProcessor(ffmpegPath: String = "ffmpeg", strict: Boolean = false): capsule.audio.AudioPostProcessor {
            if (ffmpegPath == "noop") return capsule.audio.NoOpAudioPostProcessor()
            val processor = capsule.audio.AudioPostProcessorImpl(ffmpegPath)
            return if (processor.isAvailable()) {
                processor
            } else {
                StrictModeGuard.requireAvailable(strict, "ffmpeg (audio post)", false, ffmpegPath)
                capsule.audio.NoOpAudioPostProcessor()
            }
        }

        /**
         * Resolves the appropriate [capsule.podcast.PodcastConcatenator]
         * based on ffmpeg availability (CAP-PODCAST US-1). 8ème factory
         * `resolve*`, pattern mirrors [resolveAudioPostProcessor]:
         * - If ffmpegPath is "noop", returns [capsule.podcast.NoOpPodcastConcatenator]
         * - Otherwise, returns [capsule.podcast.PodcastConcatenatorImpl] if ffmpeg is available
         * - If unavailable and strict, [StrictModeGuard] throws
         * - If unavailable and non-strict, returns [capsule.podcast.NoOpPodcastConcatenator]
         *   (degraded mode — no podcast produced, backward compat)
         */
        @JvmStatic
        fun resolvePodcastConcatenator(ffmpegPath: String = "ffmpeg", strict: Boolean = false): capsule.podcast.PodcastConcatenator {
            if (ffmpegPath == "noop") return capsule.podcast.NoOpPodcastConcatenator()
            val concat = capsule.podcast.PodcastConcatenatorImpl(ffmpegPath)
            return if (concat.isAvailable()) {
                concat
            } else {
                StrictModeGuard.requireAvailable(strict, "ffmpeg (podcast concat)", false, ffmpegPath)
                capsule.podcast.NoOpPodcastConcatenator()
            }
        }

        /**
         * 9th factory — chapter marker metadata file writer.
         *
         * - noop → [NoOpChapterMarker] (writes empty JSON)
         * - otherwise → [ChapterMarkerImpl] (always available — disk write only, no ffmpeg)
         * - strict has no effect here (disk write always succeeds)
         */
        @JvmStatic
        fun resolveChapterMarker(ffmpegPath: String = "ffmpeg", strict: Boolean = false): ChapterMarker {
            if (ffmpegPath == "noop") return NoOpChapterMarker()
            return ChapterMarkerImpl(ffmpegPath)
        }

        /**
         * 10th factory — card renderer for intro/outro chapter cards.
         *
         * - noop → [NoOpCardRenderer] (writes no-op HTML placeholder)
         * - otherwise → [CardRendererImpl] (always available — disk write + HTML generation, no external deps)
         */
        @JvmStatic
        fun resolveCardRenderer(ffmpegPath: String = "ffmpeg", strict: Boolean = false): CardRenderer {
            if (ffmpegPath == "noop") return NoOpCardRenderer()
            return CardRendererImpl()
        }

        fun readScriptFiles(dir: File): List<File> {
            return dir.listFiles { f ->
                f.name.endsWith("-script.txt") &&
                !f.name.startsWith("example-") &&
                !f.name.contains("-context-")
            }
                ?.toList() ?: emptyList()
        }

        fun resolveScriptDir(project: Project, capsuleExt: CapsuleExtension): File {
            val configured = capsuleExt.sliderScriptDir.get()
            val candidate = project.layout.buildDirectory.dir(configured).get().asFile
            if (candidate.exists() && candidate.listFiles()
                    ?.any { it.name.endsWith("-script.txt") } == true
            ) {
                return candidate
            }
            // CAP-CR3-4 — configurable fallback to slider's build output.
            val sliderBuildDir = capsuleExt.sliderBuildDir.orNull
            if (!sliderBuildDir.isNullOrBlank()) {
                val sliderOutput = project.file(sliderBuildDir)
                    .resolve("slider").resolve("build").resolve("capsule")
                if (sliderOutput.exists()) return sliderOutput
            }
            return candidate
        }

        fun resolveDeckDir(project: Project, capsuleExt: CapsuleExtension): File {
            val configured = capsuleExt.deckSourceDir.get()
            val candidate = project.layout.buildDirectory.dir(configured).get().asFile
            if (candidate.exists()) return candidate
            // CAP-CR3-4 — configurable fallback to slider's build output.
            val sliderBuildDir = capsuleExt.sliderBuildDir.orNull
            if (!sliderBuildDir.isNullOrBlank()) {
                val sliderOutput = project.file(sliderBuildDir)
                    .resolve("slider").resolve("build").resolve("docs").resolve("asciidocRevealJs")
                if (sliderOutput.exists()) return sliderOutput
            }
            return candidate
        }
    }
}
