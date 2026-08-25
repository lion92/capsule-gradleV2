package capsule.i18n

import org.gradle.api.Project
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.text.MessageFormat
import java.util.Locale
import java.util.PropertyResourceBundle
import java.util.ResourceBundle

/**
 * Internationalization (i18n) message resolver for the Capsule plugin.
 *
 * Loads messages from `capsule/i18n/Messages_{code}.properties` resource bundles
 * using UTF-8 encoding. Supports 10 languages: en, zh, hi, es, fr, ar, bn,
 * pt, ru, ur.
 *
 * Usage:
 * ```
 * CapsuleMessages.get("task.generateCapsuleVideo.description", "fr")
 * CapsuleMessages.format("task.deployCapsule.description", "fr")
 * ```
 *
 * Pattern aligned on [slider.i18n.SliderMessages] and
 * [plantuml.PlantumlMessages] (cross-borough convention).
 */
object CapsuleMessages {

    // Namespaced under `capsule/`: slider (and the other cccp plugins) ship their own
    // bundle at the bare `i18n/Messages` path. Two plugins on the same classpath would
    // shadow each other's bundle — whichever jar comes first wins — and the loser blows
    // up with MissingResourceException on its own keys.
    private val baseName = "capsule/i18n/Messages"

    private val utf8Control = object : ResourceBundle.Control() {
        override fun newBundle(
            baseName: String,
            locale: Locale,
            format: String,
            loader: ClassLoader,
            reload: Boolean
        ): ResourceBundle? {
            if (format != "java.properties") return super.newBundle(baseName, locale, format, loader, reload)
            val bundleName = toBundleName(baseName, locale)
            val resourceName = toResourceName(bundleName, "properties")
            val url = loader.getResource(resourceName) ?: return null
            val connection = url.openConnection()
            if (reload) connection.useCaches = false
            connection.connect()
            return InputStreamReader(connection.inputStream, StandardCharsets.UTF_8).use { reader ->
                PropertyResourceBundle(reader)
            }
        }
    }

    /**
     * Loads the [ResourceBundle] for a given language code.
     */
    fun forLanguage(code: String): ResourceBundle {
        val locale = localeFor(code)
        return ResourceBundle.getBundle(baseName, locale, utf8Control)
    }

    /**
     * Retrieves a simple message by key.
     */
    fun get(key: String, language: String = "en"): String {
        val bundle = forLanguage(language)
        return bundle.getString(key)
    }

    /**
     * Retrieves and formats a parameterized message.
     */
    fun format(key: String, language: String = "en", vararg args: Any): String {
        val pattern = get(key, language)
        return MessageFormat.format(pattern, *args)
    }

    private fun localeFor(code: String): Locale = when (code) {
        "zh" -> Locale.SIMPLIFIED_CHINESE
        "hi" -> Locale.forLanguageTag("hi")
        "es" -> Locale.forLanguageTag("es")
        "fr" -> Locale.FRENCH
        "ar" -> Locale.forLanguageTag("ar")
        "bn" -> Locale.forLanguageTag("bn")
        "pt" -> Locale.forLanguageTag("pt")
        "ru" -> Locale.forLanguageTag("ru")
        "ur" -> Locale.forLanguageTag("ur")
        else -> Locale.ENGLISH
    }

    /**
     * Resolves the active language from the project configuration cascade:
     * CLI `-Pcapsule.language` > `gradle.properties` > DSL `capsule { language }` >
     * default `"en"`.
     */
    fun resolveLanguage(project: Project): String {
        val cli = project.findProperty("capsule.language") as? String
        if (!cli.isNullOrBlank()) return cli

        try {
            val ext = project.extensions.findByName("capsule")
            if (ext != null) {
                val langField = ext::class.java.getMethod("getLanguage").invoke(ext) as? String
                if (!langField.isNullOrBlank()) return langField
            }
        } catch (_: Exception) {
            // DSL extension not configured — fall through
        }

        return "en"
    }
}