package capsule

import capsule.audio.AudioPostConfig
import capsule.audio.NoOpAudioPostProcessor
import capsule.feed.CapsuleScript
import capsule.feed.CapsuleScriptReader
import capsule.feed.SlideSegment
import capsule.feed.SlideType
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
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

@DisableCachingByDefault(because = "Filesystem-bound: injects audio into HTML deck and captures video via Playwright")
open class CapsuleVideoTask : DefaultTask() {

    @get:OutputDirectory
    val outputDir: DirectoryProperty = project.objects.directoryProperty()

    @get:Internal
    internal var capsuleExtension: CapsuleExtension
        get() = _capsuleExtension ?: project.extensions.getByType(CapsuleExtension::class.java).also { _capsuleExtension = it }
        set(value) { _capsuleExtension = value }

    private var _capsuleExtension: CapsuleExtension? = null

    companion object {
        /**
         * Default per-slide capture timeout (5 minutes). A capture that exceeds
         * this bound aborts the parallel run and shuts the executor down.
         * Configurable via the [CapsuleExtension.captureTimeoutMinutes] DSL (CR-2.3).
         */
        const val DEFAULT_CAPTURE_TIMEOUT_MILLIS: Long = 5 * 60 * 1000L

        private const val AUDIO_INJECT_SCRIPT = """
<!-- CAPSULE-GRADLE: Autoplay audio injection -->
<script>
(function() {
  var currentAudio = null;
  var sections = document.querySelectorAll('.reveal .slides section[data-audio]');
  var audios = [];
  sections.forEach(function(sec) {
    var src = sec.getAttribute('data-audio');
    if (src) {
      var audio = new Audio(src.replace('file://', ''));
      audio.id = 'audio-' + audios.length;
      document.body.appendChild(audio);
      audios.push(audio);
    }
  });
  function playSlideAudio(idx) {
    if (currentAudio) { currentAudio.pause(); currentAudio.currentTime = 0; }
    currentAudio = audios[idx];
    if (currentAudio) {
      currentAudio.currentTime = 0;
      currentAudio.play().catch(function(e) { console.warn('Audio play failed:', e); });
    }
  }
  if (typeof Reveal !== 'undefined') {
    Reveal.on('slidechanged', function(event) {
      playSlideAudio(event.indexh);
    });
    if (audios.length > 0) playSlideAudio(0);
  }
})();
</script>
"""

        @JvmStatic
        fun concatWebmFiles(webmFiles: List<File>, outputFile: File, ffmpegPath: String = "ffmpeg"): Boolean {
            if (webmFiles.isEmpty()) return false
            val concatList = File(outputFile.parentFile, "concat-${System.currentTimeMillis()}.txt")
            concatList.writeText(webmFiles.joinToString("\n") { "file '${it.absolutePath.replace("'", "'\\''")}'" })
            val success = try {
                val proc = ProcessBuilder(
                    ffmpegPath, "-y", "-f", "concat", "-safe", "0", "-i", concatList.absolutePath, "-c", "copy", outputFile.absolutePath
                ).redirectErrorStream(true).start()
                val exitCode = proc.waitFor()
                exitCode == 0 && outputFile.exists()
            } catch (e: Exception) {
                false
            } finally {
                concatList.delete()
            }
            if (success) {
                webmFiles.forEach { it.delete() }
            }
            return success
        }

        /**
         * Extracts a single slide from a reveal.js deck HTML and produces a standalone HTML document
         * that can be rendered independently by Playwright/ScreenshotCapture.
         *
         * @param deckHtml The full deck HTML source
         * @param slideIndex 0-based index of the section within `.slides` container
         * @return Standalone HTML with only the requested slide, preserving head, styles, and scripts
         */
        @JvmStatic
        fun createSingleSlideHtml(deckHtml: String, slideIndex: Int): String {
            val headMatch = Regex("""(?s)(<head>.*?</head>)""").find(deckHtml)
            val headSection = headMatch?.value ?: ""

            val slidesDivRegex = Regex("""(?s)<div class="slides">\s*(.*?)\s*</div>""", RegexOption.DOT_MATCHES_ALL)
            val slidesDivMatch = slidesDivRegex.find(deckHtml)
            if (slidesDivMatch == null) {
                return deckHtml
            }
            val slidesContent = slidesDivMatch.groupValues[1]

            // Extract top-level <section> elements (may contain nested <section> for vertical stacks)
            val topLevelSections = HtmlSectionParser.extractTopLevelSections(slidesContent)

            if (slideIndex < 0 || slideIndex >= topLevelSections.size) {
                return deckHtml
            }

            val targetSection = topLevelSections[slideIndex]

            return buildString {
                appendLine("<!DOCTYPE html>")
                appendLine("<html>")
                appendLine(headSection)
                appendLine("<body>")
                appendLine("""<div class="reveal">""")
                appendLine("""  <div class="slides">""")
                appendLine("    $targetSection")
                appendLine("  </div>")
                appendLine("</div>")
                appendLine("""<script src="https://cdn.jsdelivr.net/npm/reveal.js@5.1.0/dist/reveal.js"></script>""")
                appendLine("<script>Reveal.initialize();</script>")
                appendLine("</body>")
                appendLine("</html>")
            }.trimIndent()
        }

    }

    @get:Internal
    internal var playwrightCapture: PlaywrightCapture? = null

    @get:Internal
    internal var ttsEngine: TtsEngine? = null

    @get:Internal
    internal var manimEngine: ManimEngine? = null

    @get:Internal
    internal var manimVideoMixer: ManimVideoMixer? = null

    @get:Internal
    internal var manimSlideReplacer: ManimSlideReplacer? = null

    @get:Internal
    internal var manimParallelRenderer: ManimParallelRenderer? = null

    @get:Internal
    internal var subtitleGenerator: SubtitleGenerator? = null

    @get:Internal
    internal var subtitleBurnInService: SubtitleBurnInService? = null

    init {
        outputDir.convention(
            project.layout.buildDirectory.dir("capsule")
        )
    }

    private fun resolvePlaywrightCapture(slideDurations: List<Double>): PlaywrightCapture {
        if (playwrightCapture != null) return playwrightCapture!!

        val defaultDur = capsuleExtension.slideDurationSeconds.get()
        val strategy = capsuleExtension.captureStrategy.get()
        val resolved = CaptureResolver.resolve(
            strategy = strategy,
            strict = capsuleExtension.strictMode.get(),
            playwrightFactory = {
                PlaywrightCaptureImpl(
                    timeout = capsuleExtension.playwrightTimeout.get(),
                    defaultSlideDuration = defaultDur
                )
            },
            screenshotFactory = {
                ScreenshotCaptureImpl(timeout = capsuleExtension.playwrightTimeout.get())
            },
            noOpCapture = NoOpPlaywrightCapture(),
            enginePath = capsuleExtension.chromiumExecutablePath.get()
        )
        if (resolved.isAvailable()) {
            val totalSecs = slideDurations.sum()
            logger.lifecycle(
                "Capture: {} strategy ({} slides, {}s total)",
                strategy.name.lowercase(),
                slideDurations.size,
                String.format("%.1f", totalSecs)
            )
        } else {
            logger.warn("{} not available, falling back to noop capture", strategy.name.lowercase())
        }
        return resolved
    }

    internal fun resolveTtsEngine(): TtsEngine {
        if (ttsEngine != null) return ttsEngine!!

        val langCode = capsuleExtension.ttsLanguage.get()
        val resolvedLanguage = MultiLanguageResolver.resolve(langCode)?.language

        return when (capsuleExtension.ttsEngine.get().lowercase()) {
            "piper" -> {
                val engine = PiperTtsEngine(
                    capsuleExtension.piperExecutablePath.get(),
                    capsuleExtension.ttsVoice.get(),
                    language = resolvedLanguage
                )
                if (engine.isAvailable()) {
                    engine
                } else {
                    StrictModeGuard.requireAvailable(
                        strict = capsuleExtension.strictMode.get(),
                        engineName = "piper",
                        isAvailable = false,
                        path = capsuleExtension.piperExecutablePath.get()
                    )
                    NoOpTtsEngine()
                }
            }
            "espeak" -> {
                val voice = capsuleExtension.espeakVoice.get()
                val speed = capsuleExtension.espeakSpeed.get()
                val engine = EspeakTtsEngine(voice = voice, speed = speed, language = resolvedLanguage)
                if (engine.isAvailable()) {
                    engine
                } else {
                    StrictModeGuard.requireAvailable(
                        strict = capsuleExtension.strictMode.get(),
                        engineName = "espeak",
                        isAvailable = false,
                        path = "espeak"
                    )
                    NoOpTtsEngine()
                }
            }
            else -> NoOpTtsEngine()
        }
    }

    private fun resolveManimEngineInternal(): ManimEngine {
        if (manimEngine != null) return manimEngine!!

        val config = ManimConfig(
            executablePath = capsuleExtension.manimExecutablePath.get(),
            quality = capsuleExtension.manimQuality.get(),
            scriptsDir = capsuleExtension.manimScriptsDir.get(),
            outputDir = capsuleExtension.manimOutputDir.get()
        )
        val engine = CapsuleManager.resolveManimEngine(config, strict = capsuleExtension.strictMode.get())
        if (engine.isAvailable()) {
            logger.lifecycle("Manim engine: {} (available)", engine.name())
        } else {
            logger.warn("Manim engine not available, using noop fallback")
        }
        return engine
    }

    private fun resolveManimVideoMixerInternal(): ManimVideoMixer {
        if (manimVideoMixer != null) return manimVideoMixer!!

        val ffmpegPath = capsuleExtension.ffmpegExecutablePath.get()
        val mixer = CapsuleManager.resolveManimVideoMixer(ffmpegPath, strict = capsuleExtension.strictMode.get())
        if (mixer.isAvailable()) {
            logger.lifecycle("Manim video mixer: {} (available)", mixer.name())
        } else {
            logger.warn("Manim video mixer not available, using noop fallback")
        }
        return mixer
    }

    private fun resolveManimSlideReplacerInternal(): ManimSlideReplacer {
        if (manimSlideReplacer != null) return manimSlideReplacer!!
        val replacer = CapsuleManager.resolveManimSlideReplacer()
        logger.lifecycle("Manim slide replacer: {} (available)", replacer.name())
        return replacer
    }

    private fun resolveManimParallelRenderer(): ManimParallelRenderer {
        if (manimParallelRenderer != null) return manimParallelRenderer!!
        val parallelism = if (capsuleExtension.manimParallelRender.get()) capsuleExtension.manimParallelRenderThreads.get() else 1
        val renderer = CapsuleManager.resolveManimParallelRenderer(parallelism)
        logger.lifecycle("Manim parallel renderer: {} (parallelism={})", renderer.name(), parallelism)
        return renderer
    }

    internal fun computeSlideDurations(parsed: CapsuleScript, audioDir: File): List<Double> {
        return computeSlideDurationsWithManim(parsed, audioDir, emptyMap())
    }

    /**
     * Computes slide durations, using Manim MP4 probe duration when available.
     *
     * Priority for each slide:
     * 1. MANIM slide with probed duration > 0: use manim duration
     * 2. TTS MP3 file exists with probed duration > 0: use audio duration
     * 3. Fallback: use capsuleExtension.slideDurationSeconds default
     */
    internal fun computeSlideDurationsWithManim(parsed: CapsuleScript, audioDir: File, manimDurations: Map<Int, Double>): List<Double> {
        val defaultDur = capsuleExtension.slideDurationSeconds.get()
        return parsed.segments.map { seg ->
            // Priority 1: MANIM slide with probed video duration
            val manimDur = manimDurations[seg.index]
            if (seg.type == SlideType.MANIM && manimDur != null && manimDur > 0.0) {
                manimDur
            } else {
                // Priority 2: TTS audio file with probed duration
                val idx = String.format("%02d", seg.index)
                val mp3 = audioDir.resolve("slide-$idx.mp3")
                if (mp3.exists()) {
                    val realDur = MediaProbeUtil.probeDuration(mp3)
                    if (realDur > 0.0) realDur else defaultDur
                } else defaultDur
            }
        }
    }

    internal fun synthesizeTtsForScript(parsed: CapsuleScript, audioDir: File, engine: TtsEngine): List<Int> {
        val failedSlides = mutableListOf<Int>()
        for (seg in parsed.segments) {
            val idx = String.format("%02d", seg.index)
            val ttsFile = audioDir.resolve("slide-$idx.mp3")
            if (!ttsFile.exists()) {
                try {
                    engine.synthesize(seg.speakerNote, ttsFile)
                    logger.lifecycle("  TTS → {} ({} chars)", ttsFile.name, seg.speakerNote.length)
                } catch (e: TtsException) {
                    logger.warn("  TTS retry slide {}: {}", seg.index, e.message)
                    try {
                        engine.synthesize(seg.speakerNote, ttsFile)
                        logger.lifecycle("  TTS → {} (retry, {} chars)", ttsFile.name, seg.speakerNote.length)
                    } catch (e2: TtsException) {
                        failedSlides.add(seg.index)
                        logger.error("  TTS SKIP slide {} after retry: {}", seg.index, e2.message)
                    }
                }
            }
        }
        if (failedSlides.isNotEmpty()) {
            logger.error(
                "  TTS: {} slide(s) failed synthesis after retry: {}",
                failedSlides.size,
                failedSlides.joinToString(", ")
            )
        }
        return failedSlides
    }

    internal fun renderManimSlides(
        parsed: CapsuleScript,
        manim: ManimEngine,
        manimMixer: ManimVideoMixer,
        manimScriptsDir: File,
        manimOutputDir: File,
        audioDir: File,
        manimDurations: MutableMap<Int, Double>
    ): Map<Int, File> {
        val manimSlides = parsed.segments.filter { it.type == SlideType.MANIM }
        if (manimSlides.isEmpty()) return emptyMap()

        logger.lifecycle("  Manim slides detected: {} slides with Manim animations", manimSlides.size)
        manimOutputDir.mkdirs()

        val parallelRenderer = resolveManimParallelRenderer()
        val rendered = parallelRenderer.renderAll(manimSlides, manim, manimScriptsDir, manimOutputDir)
        logger.lifecycle("  Manim render complete: {}/{} slides rendered", rendered.size, manimSlides.size)

        for ((slideIdx, manimVideo) in rendered) {
            val seg = manimSlides.find { it.index == slideIdx } ?: continue
            val sceneName = seg.manimScene ?: continue

            logger.lifecycle("    Manim → {} (scene: {})", manimVideo.name, sceneName)

            val probedDur = manim.probeDuration(manimVideo)
            if (probedDur > 0.0) {
                manimDurations[seg.index] = probedDur
                logger.lifecycle("    Manim duration: {}s (scene: {})", String.format("%.1f", probedDur), sceneName)
            }

            val slideIdxStr = String.format("%02d", seg.index)
            val ttsFile = audioDir.resolve("slide-$slideIdxStr.mp3")
            val muxedFile = manimOutputDir.resolve("${sceneName}-muxed.mp4")
            try {
                val muxed = manimMixer.mix(manimVideo, ttsFile, muxedFile)
                logger.lifecycle("    Manim+TTS → {} ({} bytes)", muxed.name, muxed.length())
                val muxedDur = manimMixer.probeDuration(muxed)
                if (muxedDur > 0.0) {
                    manimDurations[seg.index] = muxedDur
                    logger.lifecycle("    Muxed duration: {}s (scene: {})", String.format("%.1f", muxedDur), sceneName)
                }
            } catch (e: MixerException) {
                logger.warn("    Manim mux failed for scene '{}': {} — using unmixed video", sceneName, e.message)
            }
        }
        return rendered
    }

    internal fun replaceManimSlidesInDeck(
        parsed: CapsuleScript,
        modifiedDeck: File,
        manimOutputDir: File,
        renderedFiles: Map<Int, File>,
        manimReplacer: ManimSlideReplacer,
        subtitleFile: File?
    ): File {
        val manimSlides = parsed.segments.filter { it.type == SlideType.MANIM }
        var finalDeckHtml = modifiedDeck.readText()

        val manimSlideIndices = manimSlides.mapIndexedNotNull { _, seg ->
            val fullIdx = parsed.segments.indexOf(seg)
            if (fullIdx >= 0) {
                val sceneName = seg.manimScene ?: return@mapIndexedNotNull null
                val muxedFile = manimOutputDir.resolve("${sceneName}-muxed.mp4")
                val manimFile = manimOutputDir.resolve("${sceneName}.mp4")
                val renderFile = renderedFiles[seg.index]
                val videoPath = when {
                    muxedFile.exists() -> muxedFile.absolutePath
                    renderFile != null && renderFile.exists() -> renderFile.absolutePath
                    manimFile.exists() -> manimFile.absolutePath
                    else -> null
                }
                if (videoPath != null) {
                    logger.lifecycle("    Manim slide replacement: slide {} → {}", seg.index, videoPath)
                    fullIdx to videoPath
                } else null
            } else null
        }

        for ((slideIdx, videoPath) in manimSlideIndices) {
            finalDeckHtml = manimReplacer.replaceSlideAt(finalDeckHtml, slideIdx, videoPath)
        }

        if (subtitleFile != null) {
            finalDeckHtml = injectSubtitleTrack(finalDeckHtml, subtitleFile)
            modifiedDeck.writeText(finalDeckHtml)
        }

        return if (manimSlideIndices.isNotEmpty() || subtitleFile != null) {
            val replacedDeck = project.layout.buildDirectory.dir("capsule/replaced").get().asFile
            replacedDeck.mkdirs()
            val outFile = replacedDeck.resolve(modifiedDeck.name)
            outFile.writeText(finalDeckHtml)
            outFile
        } else modifiedDeck
    }

    internal fun captureDeckSequential(
        parsed: CapsuleScript,
        finalDeckFile: File,
        videoOutputDir: File,
        audioDir: File,
        outDir: File,
        slideDurations: List<Double>,
        subtitleFile: File?
    ) {
        val deckCapture = resolvePlaywrightCapture(slideDurations)
        try {
            deckCapture.capture(
                deckHtmlPath = finalDeckFile.absolutePath,
                outputDir = videoOutputDir,
                viewportWidth = capsuleExtension.viewportWidth.get(),
                viewportHeight = capsuleExtension.viewportHeight.get(),
                slideDurations = slideDurations
            )
        } catch (e: CapturingException) {
            logger.error("Playwright capture failed for '{}': {}", parsed.deckName, e.message)
            throw e
        } finally {
            deckCapture.close()
        }

        // SCREENSHOT strategy leaves the per-slide `slide-N.webm` next to the
        // concatenated `capsule.webm`, and listFiles() order is unspecified:
        // always prefer the concatenated file, never a single slide.
        val producedWebms = videoOutputDir.listFiles { f -> f.name.endsWith(".webm") }?.toList().orEmpty()
        val generatedVideo = producedWebms.firstOrNull { it.name == ScreenshotPlanner.FINAL_WEBM_NAME }
            ?: producedWebms.firstOrNull { !it.name.startsWith("slide-") }
            ?: producedWebms.firstOrNull()
        if (generatedVideo != null) {
            val finalVideo = outDir.resolve("${parsed.deckName}.webm")
            generatedVideo.copyTo(finalVideo, overwrite = true)
            mixAudioWithVideo(finalVideo, audioDir, parsed.segments)
            burnInSubtitlesIfEnabled(finalVideo, subtitleFile)
            applyAudioPostIfEnabled(finalVideo)
            val produced = convertFormatIfEnabled(finalVideo)
            logger.lifecycle("CAPSULE → {}", produced.absolutePath)
        } else {
            logger.warn("No video generated by Playwright capture for '{}'", parsed.deckName)
        }
    }

    internal fun captureDeckParallel(
        parsed: CapsuleScript,
        finalDeckFile: File,
        videoOutputDir: File,
        audioDir: File,
        outDir: File,
        subtitleFile: File?
    ) {
        logger.lifecycle("  Parallel capture enabled for '{}' ({} slides)", parsed.deckName, parsed.segments.size)
        captureSlideParallel(
            deckHtmlPath = finalDeckFile.absolutePath,
            outputDir = videoOutputDir,
            viewportWidth = capsuleExtension.viewportWidth.get(),
            viewportHeight = capsuleExtension.viewportHeight.get(),
            parsed = parsed,
            audioDir = audioDir,
            captureTimeoutMillis = capsuleExtension.captureTimeoutMinutes.get().toLong() * 60_000L
        )
        val concatVideo = videoOutputDir.resolve("${parsed.deckName}.webm")
        if (concatVideo.exists()) {
            val finalVideo = outDir.resolve("${parsed.deckName}.webm")
            concatVideo.copyTo(finalVideo, overwrite = true)
            mixAudioWithVideo(finalVideo, audioDir, parsed.segments)
            burnInSubtitlesIfEnabled(finalVideo, subtitleFile)
            applyAudioPostIfEnabled(finalVideo)
            val produced = convertFormatIfEnabled(finalVideo)
            logger.lifecycle("CAPSULE (parallel) → {}", produced.absolutePath)
        } else {
            logger.warn("Parallel capture produced no video for '{}'", parsed.deckName)
        }
    }

    @TaskAction
    open fun execute() {
        val deckDir = CapsuleManager.resolveDeckDir(project, capsuleExtension)
        val scriptDir = CapsuleManager.resolveScriptDir(project, capsuleExtension)

        val deckFiles = deckDir.listFiles { f -> f.name.endsWith("-deck.html") }?.toList()
            ?: emptyList()
        val scripts = CapsuleManager.readScriptFiles(scriptDir)

        if (deckFiles.isEmpty()) {
            logger.warn("No *-deck.html files found in {}", deckDir.absolutePath)
            logger.warn("Run 'asciidoctorRevealJs' from slider-gradle first.")
            return
        }

        if (scripts.isEmpty()) {
            logger.warn("No capsule scripts found. Run 'asciidocCapsule' from slider-gradle first.")
            return
        }

        val outDir = project.layout.buildDirectory.dir(
            capsuleExtension.outputDir.get()
        ).get().asFile
        outDir.mkdirs()

        val engine = resolveTtsEngine()
        val manim = resolveManimEngineInternal()
        val manimMixer = resolveManimVideoMixerInternal()
        val manimReplacer = resolveManimSlideReplacerInternal()
        val manimConfig = ManimConfig(
            executablePath = capsuleExtension.manimExecutablePath.get(),
            quality = capsuleExtension.manimQuality.get(),
            scriptsDir = capsuleExtension.manimScriptsDir.get(),
            outputDir = capsuleExtension.manimOutputDir.get()
        )
        val manimScriptsDir = project.file(manimConfig.scriptsDir)

        for (script in scripts) {
            val parsed = CapsuleScriptReader.read(script)
            val audioDir = outDir.resolve(parsed.deckName)
            audioDir.mkdirs()

            synthesizeTtsForScript(parsed, audioDir, engine)

            val deckFile = deckFiles.find { it.nameWithoutExtension.startsWith(parsed.deckName) }
                ?: deckFiles.firstOrNull()
            if (deckFile == null) {
                logger.warn("No matching deck HTML found for '{}'", parsed.deckName)
                continue
            }

            val modifiedDeck = injectAudio(deckFile, parsed, audioDir)
            val videoOutputDir = outDir.resolve(parsed.deckName).resolve("video")
            videoOutputDir.mkdirs()

            val manimDurations = mutableMapOf<Int, Double>()
            val manimOutputDir = project.layout.buildDirectory.dir(manimConfig.outputDir).get().asFile.resolve(parsed.deckName).resolve("manim")
            val renderedFiles = renderManimSlides(parsed, manim, manimMixer, manimScriptsDir, manimOutputDir, audioDir, manimDurations)

            val slideDurations = computeSlideDurationsWithManim(parsed, audioDir, manimDurations)

            // Generate subtitles if enabled
            val subtitleFile: File? = if (capsuleExtension.subtitleEnabled.get()) {
                generateSubtitles(parsed, slideDurations, outDir)
            } else null

            val finalDeckFile = replaceManimSlidesInDeck(
                parsed, modifiedDeck, manimOutputDir, renderedFiles, manimReplacer, subtitleFile
            )

            if (capsuleExtension.parallelCaptureEnabled.get()) {
                captureDeckParallel(parsed, finalDeckFile, videoOutputDir, audioDir, outDir, subtitleFile)
            } else {
                captureDeckSequential(parsed, finalDeckFile, videoOutputDir, audioDir, outDir, slideDurations, subtitleFile)
            }
        }
    }

    internal fun generateSubtitles(parsed: CapsuleScript, slideDurations: List<Double>, outDir: File): File? {
        val format = SubtitleFormat.fromString(capsuleExtension.subtitleFormat.get())
        val generator = resolveSubtitleGenerator(format)
        val entries = SubtitleTimingCalculator.calculate(parsed.segments, slideDurations)
        val content = generator.generate(entries)
        val subtitleFile = outDir.resolve("${parsed.deckName}${format.fileExtension}")
        subtitleFile.writeText(content)
        logger.lifecycle("  Subtitles → {} ({} cues, {} format)", subtitleFile.name, entries.size, format.name.lowercase())
        return subtitleFile
    }

    private fun resolveSubtitleGenerator(format: SubtitleFormat): SubtitleGenerator {
        if (subtitleGenerator != null) return subtitleGenerator!!
        return when (format) {
            SubtitleFormat.VTT -> VttGenerator()
            SubtitleFormat.SRT -> SrtGenerator()
        }
    }

    private fun resolveSubtitleBurnInService(): SubtitleBurnInService {
        if (subtitleBurnInService != null) return subtitleBurnInService!!
        val ffmpegPath = capsuleExtension.ffmpegExecutablePath.get()
        val style = SubtitleBurnInStyle(
            fontSize = capsuleExtension.subtitleBurnInFontSize.get(),
            fontColor = capsuleExtension.subtitleBurnInFontColor.get(),
            outlineColor = capsuleExtension.subtitleBurnInOutlineColor.get(),
            position = capsuleExtension.subtitleBurnInPosition.get()
        )
        val service = CapsuleManager.resolveSubtitleBurnInService(ffmpegPath, style, strict = capsuleExtension.strictMode.get())
        if (service.isAvailable()) {
            logger.lifecycle("Subtitle burn-in service: {} (available, style: fontSize={}, color={}, position={})", service.name(), style.fontSize, style.fontColor, style.position)
        } else {
            logger.warn("Subtitle burn-in service not available, using noop fallback")
        }
        return service
    }

    internal fun injectSubtitleTrack(deckHtml: String, subtitleFile: File): String {
        val format = SubtitleFormat.fromString(capsuleExtension.subtitleFormat.get())
        val lang = HtmlEscape.escape(capsuleExtension.ttsLanguage.get())
        val src = HtmlEscape.escape(subtitleFile.name)
        val label = HtmlEscape.escape(format.name)
        val trackElement = """<track kind="captions" src="$src" srclang="$lang" label="$label captions" default>"""
        // The autoplay audio script is already injected by injectAudio — re-adding it
        // here would build a second set of Audio elements and play the narration twice.
        return deckHtml.replace(
            "</body>",
            "$trackElement\n</body>"
        )
    }

    internal fun burnInSubtitlesIfEnabled(videoFile: File, subtitleFile: File?) {
        if (!capsuleExtension.subtitleBurnIn.get()) return
        if (subtitleFile == null || !subtitleFile.exists()) {
            logger.warn("Subtitle burn-in skipped: no subtitle file available")
            return
        }

        val service = resolveSubtitleBurnInService()
        val tmpFile = File(videoFile.absolutePath + ".burnin.webm")
        try {
            val burned = service.burnIn(videoFile, subtitleFile, tmpFile)
            if (burned.exists() && burned.length() > 0) {
                burned.renameTo(videoFile)
                logger.lifecycle("  Subtitle burn-in: {} burned into {}", subtitleFile.name, videoFile.name)
            } else {
                logger.warn("  Subtitle burn-in produced empty file, keeping original video")
            }
        } catch (e: BurnInException) {
            logger.warn("  Subtitle burn-in error: {} — keeping original video", e.message)
        } finally {
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    /**
     * Audio post-production (CAP-AUDIO US-3). Applies BGM mix, loudness
     * normalization (EBU R128), and sidechain ducking to the final WebM,
     * *after* subtitle burn-in and *before* MP4 format conversion.
     *
     * Economy of ink (AGENT.adoc): the step is skipped entirely when both
     * [CapsuleExtension.audioPostBgmEnabled] and
     * [CapsuleExtension.audioPostDuckingEnabled] are `false` (default —
     * backward compat). When enabled, the resolved [capsule.audio.AudioPostProcessor]
     * is invoked; a `false` result (NoOp fallback, ffmpeg unavailable,
     * missing BGM file) keeps the original video (degraded mode).
     */
    internal fun applyAudioPostIfEnabled(finalVideo: File) {
        val bgmEnabled = capsuleExtension.audioPostBgmEnabled.get()
        val duckingEnabled = capsuleExtension.audioPostDuckingEnabled.get()
        if (!bgmEnabled && !duckingEnabled) return

        if (!finalVideo.exists()) {
            logger.warn("Audio post: final video not found ('{}') — skipping", finalVideo.name)
            return
        }

        val ffmpegPath = capsuleExtension.ffmpegExecutablePath.get()
        val processor = CapsuleManager.resolveAudioPostProcessor(ffmpegPath, strict = capsuleExtension.strictMode.get())
        if (processor !is NoOpAudioPostProcessor) {
            logger.lifecycle(
                "Audio post: {} (available, ffmpeg={})",
                processor.name(),
                ffmpegPath
            )
        }

        val config = AudioPostConfig(
            bgmEnabled = bgmEnabled,
            bgmFile = capsuleExtension.audioPostBgmFile.get(),
            bgmLevel = capsuleExtension.audioPostBgmLevel.get(),
            loudnessTarget = capsuleExtension.audioPostLoudnessTarget.get(),
            duckingEnabled = duckingEnabled
        )

        val tmpFile = File(finalVideo.absolutePath + ".audiopost.webm")
        try {
            val success = processor.process(finalVideo, tmpFile, config)
            if (success && tmpFile.exists() && tmpFile.length() > 0) {
                tmpFile.renameTo(finalVideo)
                logger.lifecycle("Audio post: BGM + loudness + ducking applied to {}", finalVideo.name)
            } else {
                logger.warn("Audio post: {} returned false — keeping original video", processor.name())
            }
        } catch (e: capsule.audio.AudioPostException) {
            logger.warn("Audio post: error '{}' — keeping original video", e.message)
        } finally {
            if (tmpFile.exists()) tmpFile.delete()
        }
    }

    /**
     * Post-burnIn format conversion (CAP-MP4 US-2). Dispatches on
     * [CapsuleExtension.outputFormat] via [FormatConversion.convertIfNeeded]:
     * - WEBM → no-op (backward compat).
     * - MP4  → transcode WebM→MP4 via FFmpeg, remove the WebM intermediate.
     * - BOTH → transcode and keep the WebM.
     *
     * Economy of ink: skips transcode if the MP4 already exists and probes
     * with a positive duration.
     *
     * @return the produced final video file (WebM or MP4) for logging.
     */
    internal fun convertFormatIfEnabled(finalVideo: File): File {
        val format = capsuleExtension.outputFormat.get()
        if (format == OutputFormat.WEBM) return finalVideo
        val ffmpegPath = capsuleExtension.ffmpegExecutablePath.get()
        val converter = CapsuleManager.resolveFormatConverter(ffmpegPath, strict = capsuleExtension.strictMode.get())
        if (converter !is NoOpVideoFormatConverter) {
            logger.lifecycle("Format conversion: {} (available, ffmpeg={})", converter.name(), ffmpegPath)
        } else if (format != OutputFormat.WEBM) {
            logger.warn("Format conversion: NoOp fallback (ffmpeg unavailable), keeping WebM")
        }
        val produced = FormatConversion.convertIfNeeded(
            finalVideo = finalVideo,
            format = format,
            converter = converter,
            probeDuration = { f -> MediaProbeUtil.probeDuration(f) }
        )
        if (produced != finalVideo && produced.exists()) {
            logger.lifecycle("  Format conversion: {} → {}", finalVideo.name, produced.name)
        }
        return produced
    }

    internal fun captureSlideParallel(
        deckHtmlPath: String,
        outputDir: File,
        viewportWidth: Int,
        viewportHeight: Int,
        parsed: CapsuleScript,
        audioDir: File,
        captureFactory: (() -> PlaywrightCapture)? = null,
        captureTimeoutMillis: Long = DEFAULT_CAPTURE_TIMEOUT_MILLIS
    ): Int {
        val executor = Executors.newFixedThreadPool(capsuleExtension.parallelCaptureThreads.get())
        val futures = mutableListOf<Future<File?>>()
        val failedSlides = mutableListOf<Int>()
        val slideDurations = computeSlideDurations(parsed, audioDir)

        // Read the deck HTML once for createSingleSlideHtml extraction
        val deckHtml = File(deckHtmlPath).readText()

        for ((idx, seg) in parsed.segments.withIndex()) {
            val slideDir = outputDir.resolve("slide-${String.format("%02d", seg.index)}")
            futures.add(executor.submit<File?> {
                val capture = captureFactory?.invoke() ?: resolvePlaywrightCapture(listOf(slideDurations[idx]))
                try {
                    // Create a standalone HTML for this specific slide
                    val singleSlideHtml = createSingleSlideHtml(deckHtml, idx)
                    val singleSlideFile = slideDir.resolve("slide.html")
                    slideDir.mkdirs()
                    singleSlideFile.writeText(singleSlideHtml)

                    capture.capture(singleSlideFile.absolutePath, slideDir, viewportWidth, viewportHeight, listOf(slideDurations[idx]))
                    // No capture engine writes a file called `slide.webm`: Playwright
                    // names its recording with a random id, ScreenshotCaptureImpl emits
                    // `capsule.webm`. Take whatever WebM landed in the slide directory.
                    val produced = slideDir.listFiles { f -> f.name.endsWith(".webm") }?.toList().orEmpty()
                    val source = produced.firstOrNull { it.name == ScreenshotPlanner.FINAL_WEBM_NAME }
                        ?: produced.firstOrNull { !it.name.startsWith("slide-") }
                        ?: produced.firstOrNull()
                    if (source != null && source.exists()) {
                        val target = outputDir.resolve("slide-${String.format("%02d", seg.index)}.webm")
                        source.copyTo(target, overwrite = true)
                        target
                    } else null
                } catch (e: Exception) {
                    // Degraded mode: a failing slide is reported, never fatal for the whole deck.
                    logger.warn("  Slide {} capture failed: {}", seg.index, e.message)
                    synchronized(failedSlides) { failedSlides.add(seg.index) }
                    null
                } finally {
                    capture.close()
                }
            })
        }

        val webmFiles = try {
            futures.mapNotNull { future ->
                try {
                    future.get(captureTimeoutMillis, TimeUnit.MILLISECONDS)
                } catch (e: TimeoutException) {
                    logger.error(
                        "Parallel capture timed out after {} ms — aborting. Shutting down executor.",
                        captureTimeoutMillis
                    )
                    throw e
                }
            }
        } finally {
            executor.shutdownNow()
        }

        if (failedSlides.isNotEmpty()) {
            logger.error(
                "{} slide(s) failed to capture: {} — resulting video may be incomplete",
                failedSlides.size, failedSlides.sorted().joinToString(", ")
            )
        }

        if (webmFiles.isNotEmpty()) {
            val finalVideo = outputDir.resolve("${parsed.deckName}.webm")
            concatWebmFiles(webmFiles, finalVideo, capsuleExtension.ffmpegExecutablePath.get())
        }

        return failedSlides.size
    }

    internal fun injectAudio(deckFile: File, script: CapsuleScript, audioDir: File): File {
        val originalHtml = deckFile.readText()
        val injectedDir = project.layout.buildDirectory.dir("capsule/injected").get().asFile
        injectedDir.mkdirs()

        val hasAudio = script.segments.any { seg ->
            val idx = String.format("%02d", seg.index)
            val audioFile = audioDir.resolve("slide-$idx.mp3")
            audioFile.exists()
        }

        if (!hasAudio) {
            val outFile = injectedDir.resolve(deckFile.name)
            outFile.writeText(originalHtml)
            return outFile
        }

        val hasDataCapsuleSlide = originalHtml.contains("data-capsule-slide=")

        val injectedHtml = originalHtml.lines().map { line ->
            if (line.contains("<section") && !line.contains("</section>")) {
                var mutableLine = line
                for (seg in script.segments) {
                    if (line.contains("data-capsule-slide=\"${seg.index}\"") || line.contains("data-capsule-slide='${seg.index}'")) {
                        val idx = String.format("%02d", seg.index)
                        val audioPath = audioDir.resolve("slide-$idx.mp3").absolutePath
                        mutableLine = mutableLine.replace(
                            "<section",
                            "<section data-audio=\"file://$audioPath\""
                        )
                        break
                    }
                }
                mutableLine
            } else {
                line
            }
        }.joinToString("\n")

        if (!hasDataCapsuleSlide) {
            return injectAudioSequentialFallback(deckFile, script, audioDir, injectedDir)
        }

        val injected = injectedHtml.replace(
            "</body>",
            "$AUDIO_INJECT_SCRIPT</body>"
        )

        val outFile = injectedDir.resolve(deckFile.name)
        outFile.writeText(injected)
        return outFile
    }

    private fun injectAudioSequentialFallback(
        deckFile: File,
        script: CapsuleScript,
        audioDir: File,
        injectedDir: File
    ): File {
        val originalHtml = deckFile.readText()

        val sections = HtmlSectionParser.findTopLevelSectionOpenTags(originalHtml)

        val injectedHtml = buildString {
            var lastEnd = 0
            var slideIdx = 0
            for (match in sections) {
                append(originalHtml.substring(lastEnd, match.range.first))
                var tag = match.value
                if (slideIdx < script.segments.size) {
                    val seg = script.segments[slideIdx]
                    val idx = String.format("%02d", seg.index)
                    val audioPath = audioDir.resolve("slide-$idx.mp3").absolutePath
                    tag = tag.replace("<section", "<section data-audio=\"file://$audioPath\"")
                }
                append(tag)
                lastEnd = match.range.last + 1
                slideIdx++
            }
            append(originalHtml.substring(lastEnd))
        }

        val sequentialScript = AUDIO_INJECT_SCRIPT.replace(
            "<!-- CAPSULE-GRADLE: Autoplay audio injection -->",
            "<!-- CAPSULE-GRADLE: Autoplay audio injection (sequential fallback) -->"
        )

        val injected = injectedHtml.replace(
            "</body>",
            "$sequentialScript</body>"
        )

        val outFile = injectedDir.resolve(deckFile.name)
        outFile.writeText(injected)
        return outFile
    }

    private fun mixAudioWithVideo(videoFile: File, audioDir: File, slides: List<SlideSegment>) {
        val mp3Files = slides.mapNotNull { seg ->
            val idx = String.format("%02d", seg.index)
            val f = audioDir.resolve("slide-$idx.mp3")
            f.takeIf { it.exists() }
        }
        if (mp3Files.isEmpty()) return

        val cmd = mutableListOf(capsuleExtension.ffmpegExecutablePath.get(), "-y", "-i", videoFile.absolutePath)
        val concatInputs = mutableListOf<String>()

        for ((i, mp3) in mp3Files.withIndex()) {
            val inputIdx = i + 1
            cmd.addAll(listOf("-i", mp3.absolutePath))
            concatInputs.add("[$inputIdx:a]")
        }

        val filterComplex = "${concatInputs.joinToString("")}concat=n=${mp3Files.size}:v=0:a=1[aout]"
        cmd.addAll(listOf("-filter_complex", filterComplex, "-map", "0:v", "-map", "[aout]", "-c:v", "copy", "-c:a", "libvorbis", "-shortest"))

        val tmpFile = File(videoFile.absolutePath + ".tmp.webm")
        cmd.add(tmpFile.absolutePath)

        try {
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val exitCode = proc.waitFor()
            if (exitCode == 0 && tmpFile.exists()) {
                tmpFile.renameTo(videoFile)
                val totalSlides = mp3Files.size
                val audioDur = mp3Files.sumOf { f ->
                    MediaProbeUtil.probeDuration(f)
                }
                logger.lifecycle("  Audio mix: {} slides concatenated (audio={}s, video={}s)", totalSlides, String.format("%.1f", audioDur), String.format("%.1f", MediaProbeUtil.probeDuration(videoFile)))
            } else {
                logger.warn("  Audio mix failed (ffmpeg exit code {}), video remains silent", exitCode)
            }
        } catch (e: Exception) {
            logger.warn("  Audio mix error: {} — video remains silent", e.message)
        } finally {
            if (tmpFile.exists()) tmpFile.delete()
        }
    }
}
