package capsule

import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

/**
 * Runs an external command — ffmpeg, ffprobe, manim, piper, espeak, node — and
 * returns its exit code together with what it printed.
 *
 * ## Pourquoi ce détour
 *
 * Le motif qui traînait dans tout le plugin était :
 *
 * ```kotlin
 * val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
 * val exitCode = proc.waitFor()                       // ← peut ne jamais revenir
 * if (exitCode != 0) proc.inputStream.bufferedReader().readText()
 * ```
 *
 * Deux défauts, tous les deux invisibles tant que la commande reste bavarde
 * *avec modération* :
 *
 * 1. **Interblocage.** `redirectErrorStream(true)` fait passer les deux flux du
 *    fils par un tube de 64 Kio. Personne ne le vide avant `waitFor()` : dès que
 *    la commande dépasse cette taille, elle bloque sur son écriture, le père
 *    bloque sur `waitFor()`, et le build est figé pour toujours. ffmpeg et manim
 *    dépassent 64 Kio sans effort — une barre de progression suffit.
 * 2. **Message d'erreur tronqué.** Lire `inputStream` *après* `waitFor()` ne
 *    ramène que ce qui tenait dans le tube ; le début du diagnostic est perdu.
 *
 * La sortie fusionnée est donc redirigée vers un fichier — le fils écrit sans
 * jamais attendre personne — puis relue à la fin. C'est déjà ce que faisaient
 * les briques les plus récentes ([ScreenshotCaptureImpl], [RemotionCaptureImpl],
 * [RemotionTemplate]) ; cet objet en fait la règle commune.
 *
 * Un délai de garde borne l'attente : une commande qui ne rend jamais la main
 * est tuée au lieu d'immobiliser le démon Gradle.
 */
object ProcessRunner {

    /** Délai de garde par défaut. Un rendu Manim 4K peut durer longtemps. */
    const val DEFAULT_TIMEOUT_MINUTES: Long = 60L

    /** Délai de garde des sondes `--version` / `--help` : elles rendent la main tout de suite. */
    const val PROBE_TIMEOUT_MINUTES: Long = 1L

    /**
     * Taille maximale relue depuis le journal. Un rendu bavard produit des
     * mégaoctets ; seul le dernier tronçon porte le diagnostic.
     */
    internal const val MAX_CAPTURED_BYTES: Int = 64 * 1024

    /**
     * @param exitCode code de sortie, ou [TIMED_OUT] si la commande a été tuée.
     * @param output fin de la sortie fusionnée (stdout + stderr), au plus
     *        [MAX_CAPTURED_BYTES] octets.
     */
    data class Result(val exitCode: Int, val output: String) {
        val isSuccess: Boolean get() = exitCode == 0
        val timedOut: Boolean get() = exitCode == TIMED_OUT

        /** Les [lines] dernières lignes, pour un message d'exception lisible. */
        fun tail(lines: Int = 20): String =
            output.lineSequence().filter { it.isNotBlank() }.toList()
                .takeLast(lines)
                .joinToString(System.lineSeparator())
    }

    /** Code de sortie conventionnel d'une commande tuée par le délai de garde. */
    const val TIMED_OUT: Int = -1

    /**
     * Lance [command] et attend sa fin.
     *
     * @param workingDir répertoire de travail du fils. À renseigner pour toute
     *        commande qui écrit *relativement* à son CWD — Manim, par exemple,
     *        pose son arborescence `media/` là où il a été lancé, pas à côté du
     *        script qu'on lui donne.
     * @param stdin texte poussé sur l'entrée standard puis fermée (piper).
     * @param logFile journal où verser la sortie fusionnée. Un fichier temporaire
     *        est créé puis effacé si rien n'est fourni.
     * @param timeoutMinutes délai de garde ; au-delà le processus est tué et
     *        [Result.timedOut] est vrai.
     * @throws java.io.IOException si la commande n'a pas pu être lancée
     *         (binaire absent) — le motif historique laissait fuiter la même
     *         exception, les appelants la traitent déjà.
     */
    fun run(
        command: List<String>,
        workingDir: File? = null,
        stdin: String? = null,
        logFile: File? = null,
        timeoutMinutes: Long = DEFAULT_TIMEOUT_MINUTES,
    ): Result {
        val ownLog = logFile == null
        val log = logFile ?: File.createTempFile("capsule-proc", ".log")
        log.parentFile?.mkdirs()

        val builder = ProcessBuilder(command)
            .redirectErrorStream(true)
            .redirectOutput(log)
        if (workingDir != null) builder.directory(workingDir)

        val process = builder.start()
        try {
            // Fermer l'entrée est ce qui dit à la commande que le texte est
            // complet ; sans quoi piper attend indéfiniment une suite. Même
            // sans texte à pousser, l'entrée est refermée aussitôt : ffmpeg lit
            // stdin pour ses raccourcis interactifs, et un tube laissé ouvert
            // le fait patienter au lieu de rendre la main.
            if (stdin != null) {
                process.outputStream.bufferedWriter().use { it.write(stdin) }
            } else {
                process.outputStream.close()
            }
            val finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(10, TimeUnit.SECONDS)
                return Result(TIMED_OUT, readTail(log))
            }
            return Result(process.exitValue(), readTail(log))
        } finally {
            if (ownLog) log.delete()
        }
    }

    /** Variante commode : `command` en varargs, réglages par défaut. */
    fun run(vararg command: String): Result = run(command.toList())

    /**
     * Sonde de disponibilité d'un binaire : vrai si la commande se lance et
     * sort en 0. Toute erreur de lancement (binaire absent) donne faux.
     */
    fun probe(command: List<String>): Boolean =
        try {
            run(command, timeoutMinutes = PROBE_TIMEOUT_MINUTES).isSuccess
        } catch (_: Exception) {
            false
        }

    /** Sonde `<executable> <args…>`, cf. [probe]. */
    fun probe(executable: String, vararg args: String): Boolean =
        probe(listOf(executable) + args)

    /** Relit au plus [MAX_CAPTURED_BYTES] octets à la fin de [log]. */
    private fun readTail(log: File): String {
        if (!log.isFile) return ""
        val length = log.length()
        if (length == 0L) return ""
        if (length <= MAX_CAPTURED_BYTES) return log.readText()
        return RandomAccessFile(log, "r").use { raf ->
            raf.seek(length - MAX_CAPTURED_BYTES)
            val buffer = ByteArray(MAX_CAPTURED_BYTES)
            raf.readFully(buffer)
            val decoded = String(buffer, Charsets.UTF_8)
            // Le premier octet tombe peut-être au milieu d'un caractère UTF-8 :
            // la ligne partielle de tête est jetée — sauf s'il n'y a aucun saut
            // de ligne, cas de ffmpeg dont la barre de progression n'écrit que
            // des retours chariot : tout jeter effacerait le diagnostic.
            decoded.substringAfter('\n', decoded)
        }
    }
}
