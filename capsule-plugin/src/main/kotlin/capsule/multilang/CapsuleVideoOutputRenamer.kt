package capsule.multilang

import java.io.File

/**
 * Aligns the WebM produced by the single-language pipeline with the plan
 * output naming contract `<deckName>_<lang>.webm`.
 *
 * The single-language pipeline names the final video from the script header
 * deckName, which in the real slider feed is `<deckName>_<lang>-deck` — hence
 * a rendered file like `capsule-feed-demo-fr-deck.webm`. The authoritative
 * artifact name is [CapsuleVideoEntry.outputVideo] (CAP-29 contract); it is
 * also the path [CapsuleVideoAllLanguagesRunner] probes to decide whether the
 * Economy of Ink skip applies, so the produced artifact MUST match it.
 */
object CapsuleVideoOutputRenamer {

    fun rename(rendered: File, target: File): File {
        if (!rendered.exists() || rendered.absolutePath == target.absolutePath) return rendered
        target.parentFile?.mkdirs()
        // Un seul déplacement dans le plugin : cf. capsule.FileReplace, qui gère
        // le passage d'un système de fichiers à l'autre.
        capsule.FileReplace.moveOver(rendered, target)
        return target
    }
}
