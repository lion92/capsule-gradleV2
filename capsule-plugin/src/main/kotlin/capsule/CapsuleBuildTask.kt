package capsule

import capsule.feed.CapsuleScriptReader
import capsule.multilang.MultiLanguageResolver
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

@DisableCachingByDefault(because = "Filesystem-bound: reads script files and writes TTS placeholder audio")
open class CapsuleBuildTask : DefaultTask() {

    @get:OutputDirectory
    val outputDir: DirectoryProperty = project.objects.directoryProperty()

    @get:Internal
    internal var capsuleExtension: CapsuleExtension
        get() = _capsuleExtension ?: project.extensions.getByType(CapsuleExtension::class.java).also { _capsuleExtension = it }
        set(value) { _capsuleExtension = value }

    private var _capsuleExtension: CapsuleExtension? = null

    init {
        outputDir.convention(project.layout.buildDirectory.dir("capsule"))
    }

    @get:Internal
    internal var ttsEngine: TtsEngine? = null

    private fun resolveTtsEngine(): TtsEngine {
        if (ttsEngine != null) return ttsEngine!!

        val configuredEngine = capsuleExtension.ttsEngine.get()
        val langCode = capsuleExtension.ttsLanguage.get()
        val resolvedLanguage = MultiLanguageResolver.resolve(langCode)?.language

        return when (configuredEngine.lowercase()) {
            "piper" -> {
                val piperPath = capsuleExtension.piperExecutablePath.get()
                val voice = capsuleExtension.ttsVoice.get()
                val engine = PiperTtsEngine(piperPath, voice, language = resolvedLanguage)
                if (engine.isAvailable()) {
                    logger.lifecycle("TTS engine: piper → {}", piperPath)
                    engine
                } else if (capsuleExtension.ttsFallbackEnabled.get()) {
                    logger.warn("Piper not available at {}, falling back to noop placeholder", piperPath)
                    StrictModeGuard.requireAvailable(
                        strict = capsuleExtension.strictMode.get(),
                        engineName = "piper",
                        isAvailable = false,
                        path = piperPath
                    )
                    NoOpTtsEngine()
                } else {
                    throw TtsException("Piper not available at: $piperPath and fallback is disabled")
                }
            }
            "espeak" -> {
                val voice = capsuleExtension.espeakVoice.get()
                val speed = capsuleExtension.espeakSpeed.get()
                val engine = EspeakTtsEngine(voice = voice, speed = speed, language = resolvedLanguage)
                if (engine.isAvailable()) {
                    logger.lifecycle("TTS engine: espeak (voice={}, speed={})", voice, speed)
                    engine
                } else {
                    logger.warn("espeak not available, falling back to noop placeholder")
                    StrictModeGuard.requireAvailable(
                        strict = capsuleExtension.strictMode.get(),
                        engineName = "espeak",
                        isAvailable = false,
                        path = "espeak"
                    )
                    NoOpTtsEngine()
                }
            }
            "noop" -> {
                logger.lifecycle("TTS engine: noop (placeholder)")
                NoOpTtsEngine()
            }
            else -> {
                logger.warn("Unknown TTS engine '{}', using noop placeholder", configuredEngine)
                NoOpTtsEngine()
            }
        }
    }

    @TaskAction
    fun execute() {
        val scriptDir = CapsuleManager.resolveScriptDir(project, capsuleExtension)
        val scripts = CapsuleManager.readScriptFiles(scriptDir)

        if (scripts.isEmpty()) {
            logger.warn("No capsule scripts to process. Skipping TTS generation.")
            return
        }

        val outDir = project.layout.buildDirectory.dir(
            capsuleExtension.outputDir.get()
        ).get().asFile
        outDir.mkdirs()

        val engine = resolveTtsEngine()
        logger.lifecycle("TTS engine: {}", engine.name())

        val cores = Runtime.getRuntime().availableProcessors()
        val executor = Executors.newFixedThreadPool(cores)
        val synthesized = AtomicInteger(0)
        val skipped = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val futures = mutableListOf<Future<*>>()

        for (script in scripts) {
            val parsed = CapsuleScriptReader.read(script)
            val deckOutputDir = outDir.resolve(parsed.deckName)
            deckOutputDir.mkdirs()

            for (seg in parsed.segments) {
                val idx = String.format("%02d", seg.index)
                val ttsFile = deckOutputDir.resolve("slide-$idx.mp3")

                futures.add(executor.submit {
                    try {
                        // Une narration déjà présente est respectée, comme le fait
                        // CapsuleVideoTask : elle vient d'un moteur externe (voix
                        // clonée, prise retenue à la main) que ce moteur local ne
                        // sait pas reproduire. Sans ce garde-fou, la relance du
                        // rendu remplace en silence la voix par le repli piper, et
                        // rien dans le journal ne dit qu'une piste a été perdue.
                        if (ttsFile.exists() && ttsFile.length() > 0) {
                            skipped.incrementAndGet()
                            logger.lifecycle("  TTS ↷ {} (déjà présent, conservé)", ttsFile.name)
                            return@submit
                        }
                        engine.synthesize(seg.speakerNote, ttsFile)
                        synthesized.incrementAndGet()
                        logger.lifecycle("  TTS → {} ({} chars)", ttsFile.name, seg.speakerNote.length)
                    } catch (e: TtsException) {
                        failed.incrementAndGet()
                        logger.error("  TTS FAILED slide {}: {}", seg.index, e.message)
                    }
                })
            }
        }

        futures.forEach { it.get() }
        executor.shutdown()

        if (failed.get() > 0 && !capsuleExtension.ttsFallbackEnabled.get()) {
            throw TtsException("${failed.get()} TTS synthesis failures (fallback disabled)")
        }

        logger.lifecycle(
            "TTS generation: {} synthesized, {} kept, {} failed, {} engine, {} cores",
            synthesized.get(), skipped.get(), failed.get(), engine.name(), cores
        )
    }
}
