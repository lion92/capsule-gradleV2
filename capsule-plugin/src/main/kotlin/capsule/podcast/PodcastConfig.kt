package capsule.podcast

/**
 * Configuration section for the capsule podcast feature
 * (CAP-PODCAST US-1).
 *
 * When [enabled] is true, the `generateCapsulePodcast` task (US-2)
 * concatenates the per-slide MP3 files (produced by
 * `synthesizeTtsForScript` in the capsule video pipeline) into a
 * single podcast MP3 written to [outputFile]. The concatenation uses
 * the FFmpeg concat demuxer (`-f concat -safe 0 -i <listfile> -c copy`)
 * — no re-encoding, lossless, fast.
 *
 * All fields default to disabled/empty to preserve backward
 * compatibility — existing configs without a `podcast` section
 * keep the no-podcast behavior.
 *
 * Resolution follows the 4-source precedence:
 * ENV (`CAPSULE_PODCAST_ENABLED`, `CAPSULE_PODCAST_OUTPUT_FILE`)
 * < gradle.properties (`capsule.podcast.enabled`,
 * `capsule.podcast.outputFile`) < YAML (`podcast.enabled`,
 * `podcast.outputFile`) < CLI (`-Pcapsule.podcast.*`).
 *
 * @param enabled     `true` to enable podcast generation
 *        (default `false` — backward compat, opt-in).
 * @param outputFile  absolute or relative path to the output podcast
 *        MP3 file (default `""` — the task resolves a default under
 *        `build/capsule/<deckName>-podcast.mp3` when blank).
 */
data class PodcastConfig(
    val enabled: Boolean = false,
    val outputFile: String = ""
)