package capsule.preview

/**
 * Configuration section for the capsule preview feature
 * (CAP-PREVIEW US-0).
 *
 * When [enabled] is true, the capsule pipeline runs in dry-run mode:
 * PNG screenshots are captured but FFmpeg encoding, audio post-production,
 * and format conversion are skipped. This enables fast visual validation
 * of slide layouts without the full video pipeline cost.
 *
 * Default is disabled to preserve backward compatibility — existing
 * configs without a `preview` section keep the full-pipeline behavior.
 *
 * Resolution follows the 4-source precedence:
 * ENV (`CAPSULE_PREVIEW_ENABLED`)
 * < gradle.properties (`capsule.preview.enabled`)
 * < YAML (`preview.enabled`)
 * < CLI (`-Pcapsule.preview.*`).
 *
 * @param enabled  `true` to enable preview dry-run mode
 *        (default `false` — backward compat, opt-in).
 */
data class PreviewConfig(
    val enabled: Boolean = false
)
