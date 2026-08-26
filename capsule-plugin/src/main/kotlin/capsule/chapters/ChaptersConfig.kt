package capsule.chapters

/**
 * Configuration section for the capsule chapters feature (CAP-CHAPITRE).
 *
 * When [enabled] is true, the `generateCapsuleChapters` task generates
 * Matroska/WebM chapter metadata from the slide segment titles and
 * durations. The chapter metadata file is injected into the final video
 * via FFmpeg `-i chapters.txt -map_metadata 1 -codec copy`.
 *
 * Optional [introText] and [outroText] drive the card rendering —
 * the task renders intro/outro cards as PNG images using Playwright,
 * converts them to short WebM segments, and concatenates them with the
 * main video.
 *
 * All fields default to disabled/empty to preserve backward
 * compatibility — existing configs without a `chapters` section
 * keep the no-chapters behavior.
 *
 * Resolution follows the 4-source precedence:
 * ENV (`CAPSULE_CHAPTERS_ENABLED`, `CAPSULE_CHAPTERS_INTRO_TEXT`, ...)
 * < gradle.properties (`capsule.chapters.enabled`, ...) < YAML
 * (`chapters.enabled`, ...) < CLI (`-Pcapsule.chapters.*`).
 *
 * @param enabled    `true` to enable chapter metadata generation
 *        (default `false` — backward compat, opt-in).
 * @param introText  custom text for the intro card title
 *        (default `""` — the task uses the deck name when blank).
 * @param outroText  custom text for the outro card title
 *        (default `""` — the task uses "Thank you" when blank).
 * @param outputDir  output directory for the chapter metadata file
 *        (default `""` — resolves under `build/capsule/` when blank).
 */
data class ChaptersConfig(
    val enabled: Boolean = false,
    val introText: String = "",
    val outroText: String = "",
    val outputDir: String = ""
)
