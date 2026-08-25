package capsule

/**
 * Enforces the strict-mode contract for engine resolution (CAP-CR3-2).
 *
 * Pure object — no I/O, no Gradle dependencies, fully testable.
 *
 * When `strict` is `true` and the resolved engine reports
 * `isAvailable == false`, [requireAvailable] throws an
 * [IllegalStateException] with an actionable message that:
 * - names the engine (`engineName`),
 * - mentions the resolved `path` (when non-blank),
 * - suggests disabling `strictMode` or installing the tool.
 *
 * When `strict` is `false`, the guard is a no-op — the caller falls
 * back to the matching NoOp engine exactly as before (backward
 * compatibility).
 */
object StrictModeGuard {

    /**
     * Asserts that an engine is available when strict mode is enabled.
     *
     * @param strict      whether strict mode is enabled
     * @param engineName  human-readable engine name (e.g. "piper", "ffmpeg")
     * @param isAvailable whether the engine reported itself as available
     * @param path        the resolved executable/path, blank if N/A
     * @throws IllegalStateException if `strict` is `true` and
     *         `isAvailable` is `false`
     */
    fun requireAvailable(
        strict: Boolean,
        engineName: String,
        isAvailable: Boolean,
        path: String
    ) {
        if (!strict || isAvailable) return

        val pathSegment = if (path.isNotBlank()) " at '$path'" else ""
        throw IllegalStateException(
            "strictMode is enabled: $engineName is not available$pathSegment. " +
                "Disable strictMode (capsule.strictMode.enabled=false) or install $engineName."
        )
    }
}