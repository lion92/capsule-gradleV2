package capsule

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Remplacement d'un fichier par un autre, avec un compte rendu honnête.
 *
 * Le plugin fabrique ses vidéos par passes successives : le mixage audio, la
 * gravure des sous-titres et la post-production écrivent chacun un `.tmp` à
 * côté du montage puis prennent sa place. La bascule s'écrivait
 * `tmp.renameTo(finalVideo)` — dont la valeur de retour était ignorée partout.
 *
 * `File.renameTo` échoue en rendant `false`, sans lever quoi que ce soit, dès
 * que la source et la cible ne sont pas sur le même système de fichiers (un
 * `build/` monté ailleurs, un conteneur avec `/tmp` en tmpfs). Le journal
 * annonçait alors « sous-titres gravés » ou « BGM appliqué » sur une vidéo
 * restée strictement identique, et le `.tmp` était effacé juste après dans le
 * `finally` : le travail était perdu **et** signalé comme fait.
 */
object FileReplace {

    /**
     * Met [source] à la place de [target].
     *
     * Tente d'abord le déplacement atomique, retombe sur un déplacement simple
     * puis sur copie + effacement quand la traversée de systèmes de fichiers
     * l'interdit.
     *
     * @return vrai si [target] porte bien le contenu de [source] à la sortie.
     */
    fun moveOver(source: File, target: File): Boolean {
        if (!source.isFile) return false
        return try {
            try {
                Files.move(
                    source.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            true
        } catch (_: Exception) {
            try {
                source.copyTo(target, overwrite = true)
                source.delete()
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
