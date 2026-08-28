package capsule

import capsule.multilang.MultiLanguageResolver
import contracts.i18n.SupportedLanguage
import java.io.File

interface TtsEngine {
    fun synthesize(text: String, outputFile: File)
    fun isAvailable(): Boolean
    fun name(): String
    fun language(): SupportedLanguage? = null
}

class PiperTtsEngine(
    private val executablePath: String = "piper",
    private val model: String = "fr_FR-siwis-medium",
    private val language: SupportedLanguage? = null
) : TtsEngine {

    private val resolvedModel: String =
        language?.let { MultiLanguageResolver.piperModel(it.code) } ?: model

    // Sondé une fois par instance : `synthesize()` vérifie la disponibilité et
    // CapsuleBuildTask synthétise une diapo par thread — sans mémorisation,
    // c'est un `piper --help` de plus par diapo, en parallèle.
    private var availabilityProbe: Boolean? = null

    override fun isAvailable(): Boolean =
        availabilityProbe ?: ProcessRunner.probe(executablePath, "--help").also { availabilityProbe = it }

    override fun name(): String = "piper"

    override fun language(): SupportedLanguage? =
        language ?: MultiLanguageResolver.resolveByPiperModel(resolvedModel)

    override fun synthesize(text: String, outputFile: File) {
        if (!isAvailable()) {
            throw TtsException("Piper executable not found at: $executablePath")
        }

        val wavFile = File(outputFile.parentFile, outputFile.nameWithoutExtension + ".wav")

        val args = listOf(
            executablePath,
            "--model", resolvedModel,
            "--output_file", wavFile.absolutePath
        )

        // Le texte part sur l'entrée standard, la sortie est drainée vers un
        // journal : piper écrit une ligne par phrase, et le père qui pousse du
        // texte pendant que le fils bloque sur un tube plein est un
        // interblocage franc.
        val result = ProcessRunner.run(args, stdin = text)
        if (!result.isSuccess) {
            throw TtsException("Piper exited with code ${result.exitCode}: ${result.tail()}")
        }

        AudioConversionUtil.wavToMp3(wavFile, outputFile)
        wavFile.delete()
    }
}

class NoOpTtsEngine : TtsEngine {
    override fun isAvailable(): Boolean = true
    override fun name(): String = "noop"

    override fun synthesize(text: String, outputFile: File) {
        outputFile.parentFile.mkdirs()
        val placeholder = listOf(
            "# TTS PLACEHOLDER (noop engine)",
            "# Text: ${text.take(100)}..."
        ).joinToString("\n")
        outputFile.writeText(placeholder)
    }
}

class TtsException(message: String) : RuntimeException(message)

class EspeakTtsEngine(
    private val executablePath: String = "espeak",
    private val voice: String = "fr",
    private val speed: Int = 150,
    private val language: SupportedLanguage? = null
) : TtsEngine {

    private val resolvedVoice: String =
        language?.let { MultiLanguageResolver.espeakVoice(it.code) } ?: voice

    /**
     * Binaire réellement utilisé.
     *
     * Debian et Ubuntu ne livrent plus `espeak` : le paquet installé est
     * `espeak-ng`, dont les options `-v`, `-s` et `-w` sont les mêmes. Sans ce
     * repli, le moteur se déclarait indisponible sur une machine où la synthèse
     * marche parfaitement, et la capsule repartait avec un texte d'espace
     * réservé à la place de la voix.
     */
    private val resolution: Pair<String, Boolean> by lazy {
        when {
            ProcessRunner.probe(executablePath, "--help") -> executablePath to true
            executablePath == DEFAULT_EXECUTABLE && ProcessRunner.probe(NG_EXECUTABLE, "--help") -> NG_EXECUTABLE to true
            else -> executablePath to false
        }
    }

    private val resolvedExecutable: String get() = resolution.first

    // Sondé une fois par instance : `synthesize()` vérifie la disponibilité et
    // CapsuleBuildTask synthétise une diapo par thread — sans mémorisation,
    // c'est une sonde de plus par diapo, en parallèle.
    override fun isAvailable(): Boolean = resolution.second

    override fun name(): String = "espeak"

    override fun language(): SupportedLanguage? =
        language ?: MultiLanguageResolver.resolveByEspeakVoice(resolvedVoice)

    override fun synthesize(text: String, outputFile: File) {
        if (!isAvailable()) {
            throw TtsException("espeak executable not found at: $executablePath (ni '$NG_EXECUTABLE')")
        }

        outputFile.parentFile.mkdirs()

        val wavFile = File(outputFile.parentFile, outputFile.nameWithoutExtension + ".wav")

        val result = ProcessRunner.run(
            listOf(
                resolvedExecutable,
                "-v", resolvedVoice,
                "-s", speed.toString(),
                "-w", wavFile.absolutePath,
                text,
            )
        )
        if (!result.isSuccess) {
            throw TtsException("espeak exited with code ${result.exitCode}: ${result.tail()}")
        }

        AudioConversionUtil.wavToMp3(wavFile, outputFile)
        wavFile.delete()
    }

    companion object {
        /** Nom historique du binaire. */
        internal const val DEFAULT_EXECUTABLE: String = "espeak"

        /** Le seul paquet encore livré par Debian et Ubuntu, ligne de commande compatible. */
        internal const val NG_EXECUTABLE: String = "espeak-ng"
    }
}
