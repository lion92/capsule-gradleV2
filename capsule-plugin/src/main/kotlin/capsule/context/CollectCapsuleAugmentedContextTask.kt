package capsule.context

import contracts.context.ChannelBudget
import contracts.context.CompositeContext
import contracts.context.CompositeContextConfig
import contracts.context.ContextChannel
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Gradle task: `collectCapsuleAugmentedContext`
 *
 * Collects the augmented context that feeds capsule content generation
 * (CAP-ARCH-2): the EAGER governance files of the consumer project
 * (`INDEX.adoc`, `PROMPT_REPRISE.adoc`, `AGENT.adoc`) plus the optional
 * RAG / Graphify / Docs channels injected via Gradle properties. The raw
 * sections are assembled into a [contracts.context.CompositeContext], budgeted
 * and rendered through [CapsuleContextBuilder], then written to the output
 * artefact `build/capsule/augmented-context.txt`.
 *
 * The `capsule.pipeline` (CAP-ARCH-3) will consume this artefact — never the
 * raw contract. Task inputs/outputs are declared Gradle-native, so the build
 * is UP-TO-DATE when nothing changed (law of economy of ink — AGENT.adoc).
 *
 * RAG / Graphify channels are injected via `-Pcontext.*` properties
 * (mockable in tests, codebase pgvector integration is out of scope here):
 *   - `-Pcontext.ragContent=...`      RAG pgvector section
 *   - `-Pcontext.graphifyContent=...` Graphify relations section
 *   - `-Pcontext.tokenBudget=...`     total token budget (default 8000)
 *
 * Docs channel (CAP-DOCCONTEXT): two sources feed `docsSection`:
 *   - `-Pcontext.docsContent=...`    raw string (legacy, rétrocompat)
 *   - `-Pcapsule.context.docsGlobs=...`  comma-separated globs resolved by the
 *     wiring layer into [docsFiles] (CAP-DOCCONTEXT-3). Globs take precedence
 *     over the raw string when non-empty.
 */
@DisableCachingByDefault(because = "Augmented context collection — governance files, non-cacheable")
abstract class CollectCapsuleAugmentedContextTask : DefaultTask() {

    /** EAGER governance files of the consumer project (missing files skipped). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val eagerFiles: ConfigurableFileCollection

    /** Documentary corpus files resolved from `context.docsGlobs` (CAP-DOCCONTEXT-3). */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val docsFiles: ConfigurableFileCollection

    /** Glob patterns that triggered [docsFiles] resolution (for input tracking). */
    @get:Input
    abstract val docsGlobs: ListProperty<String>

    /** RAG pgvector section content (optional). */
    @get:Input
    @get:Optional
    abstract val ragContent: Property<String>

    /** Graphify relations section content (optional). */
    @get:Input
    @get:Optional
    abstract val graphifyContent: Property<String>

    /** Codex/documentary section content (optional, legacy CLI string). */
    @get:Input
    @get:Optional
    abstract val docsContent: Property<String>

    /** Total token budget applied across the channels. */
    @get:Input
    abstract val tokenBudget: Property<Int>

    /**
     * Pedagogical scenario file/directory (CAP-SPD-3). When present, the
     * [CapsuleScenarioLoader] resolves `metadata.json` + companion AsciiDoc
     * and renders the scenario section appended after the N0 channels.
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val scenarioFile: ConfigurableFileCollection

    /**
     * Glossary AsciiDoc file (CAP-GLOSSARY-2). When present, the
     * [GlossaryLoader] parses the `== Glossary` section + `- term: definition`
     * bullets and renders the glossary section appended after the scenario
     * section. Missing file gracefully skipped (backward compatible no-op).
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val glossaryFile: ConfigurableFileCollection

    /** Rendered augmented context artefact. */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /**
     * Provenance artefact (CAP-PROVENANCE): `build/capsule/context-provenance.json`.
     *
     * A serialized [ContextProvenance] snapshot describing which sources fed
     * the channels that survived the token budget — the anti-hallucination
     * audit trail of the augmented context.
     */
    @get:OutputFile
    abstract val provenanceFile: RegularFileProperty

    @TaskAction
    fun run() {
        val eager = eagerFiles.files
            .filter { it.exists() }
            .sortedBy { it.name }
            .joinToString("\n\n") { file ->
                "--- ${file.name} ---\n${file.readText().trim()}"
            }

        val budget = ChannelBudget(totalTokenBudget = tokenBudget.get())

        val docsSection = resolveDocsSection(budget)

        val scenarioSection = resolveScenarioSection(budget)

        val glossarySection = resolveGlossarySection(budget)

        val composite = CompositeContext(
            eagerSection = eager,
            ragSection = ragContent.orNull.orEmpty(),
            graphifySection = graphifyContent.orNull.orEmpty(),
            docsSection = docsSection,
            config = CompositeContextConfig(),
        )
        val tracker = ProvenanceTracker()
        trackSources(tracker)
        val context = CapsuleContextBuilder.build(
            composite = composite,
            budget = budget,
            scenarioSection = scenarioSection,
            glossarySection = glossarySection,
            tracker = tracker,
        )

        val output = outputFile.asFile.get()
        output.parentFile.mkdirs()
        output.writeText(context.rendered)

        val provenance = tracker.build()
        val provenanceOut = provenanceFile.asFile.get()
        provenanceOut.parentFile.mkdirs()
        provenanceOut.writeText(provenance.toJson())

        logger.lifecycle(
            "CAPSULE CONTEXT → ${context.nonEmptyCount} non-empty channels, " +
                "~${context.tokenEstimate} tokens → ${output.absolutePath}",
        )
        logger.lifecycle(
            "CAPSULE PROVENANCE → ${provenance.channels.size} channels, " +
                "${provenance.channels.sumOf { it.sources.size }} sources → ${provenanceOut.absolutePath}",
        )
        if (context.isEmpty && context.scenarioSection.isBlank()) {
            logger.warn("CAPSULE CONTEXT → no EAGER/RAG/Graphify/Docs/scenario content collected (empty augmented context)")
        }
    }

    /**
     * Tracks the per-channel sources into [tracker] (CAP-PROVENANCE US-2).
     *
     * The builder prunes the tracker to the channels that survived the token
     * budget via [ProvenanceTracker.retainOnly] — a channel truncated to zero
     * content is dropped from the provenance. Source measurement: raw chars
     * and token estimate via the N0 [ContextChannel.estimateTokens] heuristic,
     * both taken from a single read (each source was being read from disk twice,
     * once per measure).
     */
    private fun provenanceOf(file: java.io.File): ProvenanceSource {
        val text = file.readText()
        return ProvenanceSource(
            fileName = file.name,
            chars = text.length,
            tokens = ContextChannel.estimateTokens(text),
        )
    }

    private fun trackSources(tracker: ProvenanceTracker) {
        val eagerSources = eagerFiles.files.filter { it.exists() }.sortedBy { it.name }.map(::provenanceOf)
        tracker.trackChannel("EAGER", eagerSources)

        val rag = ragContent.orNull.orEmpty()
        if (rag.isNotBlank()) {
            tracker.trackChannel(
                "RAG",
                listOf(
                    ProvenanceSource(
                        fileName = "rag-injected",
                        chars = rag.length,
                        tokens = ContextChannel.estimateTokens(rag),
                    ),
                ),
            )
        }

        val graphify = graphifyContent.orNull.orEmpty()
        if (graphify.isNotBlank()) {
            tracker.trackChannel(
                "GRAPHIFY",
                listOf(
                    ProvenanceSource(
                        fileName = "graphify-injected",
                        chars = graphify.length,
                        tokens = ContextChannel.estimateTokens(graphify),
                    ),
                ),
            )
        }

        val docsFilesResolved = docsFiles.files.filter { it.exists() }.sortedBy { it.name }
        val docsSources = if (docsFilesResolved.isNotEmpty()) {
            docsFilesResolved.map(::provenanceOf)
        } else {
            val legacy = docsContent.orNull.orEmpty()
            if (legacy.isBlank()) emptyList()
            else listOf(
                ProvenanceSource(
                    fileName = "docs-injected",
                    chars = legacy.length,
                    tokens = ContextChannel.estimateTokens(legacy),
                ),
            )
        }
        tracker.trackChannel("DOCS", docsSources)

        val scenarioTarget = scenarioFile.files.firstOrNull()
        if (scenarioTarget != null && scenarioTarget.exists()) {
            val resolved = if (scenarioTarget.isDirectory) {
                scenarioTarget.listFiles()?.firstOrNull { it.extension.equals("adoc", ignoreCase = true) }
            } else {
                scenarioTarget
            }
            if (resolved != null && resolved.exists()) {
                tracker.trackChannel(
                    ContextProvenance.SCENARIO_CHANNEL,
                    listOf(provenanceOf(resolved)),
                )
            }
        }

        val glossaryTarget = glossaryFile.files.firstOrNull()
        if (glossaryTarget != null && glossaryTarget.exists()) {
            tracker.trackChannel(
                ContextProvenance.GLOSSARY_CHANNEL,
                listOf(provenanceOf(glossaryTarget)),
            )
        }
    }

    /**
     * Resolves the Docs section content (CAP-DOCCONTEXT-3).
     *
     * Precedence: globs (resolved into [docsFiles]) > legacy CLI string
     * ([docsContent]). When globs are configured and files are resolved,
     * [DocContextLoader] concatenates + truncates them. Otherwise the raw CLI
     * string is used as-is (backward compatible with CAP-ARCH-2).
     */
    private fun resolveDocsSection(budget: ChannelBudget): String {
        val globFiles = docsFiles.files.toList()
        if (globFiles.isNotEmpty()) {
            return DocContextLoader.load(globFiles, budget)
        }
        return docsContent.orNull.orEmpty()
    }

    /**
     * Resolves the pedagogical scenario section content (CAP-SPD-3).
     *
     * The [scenarioFile] collection is fed by the wiring layer from the
     * 4-source config (ENV < props < YAML < CLI). When it contains a
     * directory, the [CapsuleScenarioLoader] resolves `metadata.json` +
     * the first `.adoc` companion. When it contains a direct `.adoc` file,
     * the metadata is skipped. When empty, the scenario section is blank
     * (backward compatible no-op).
     *
     * Token budget: 5% of the total budget (the scenario is a lightweight
     * anchoring payload, not a corpus).
     */
    private fun resolveScenarioSection(budget: ChannelBudget): String {
        val files = scenarioFile.files.toList()
        if (files.isEmpty()) return ""
        val target = files.first()
        if (!target.exists()) return ""

        val scenarioBudget = (budget.totalTokenBudget * 0.05).toInt().coerceAtLeast(50)
        return if (target.isDirectory) {
            val metadata = target.listFiles()?.firstOrNull { it.name == "metadata.json" }
            val adoc = target.listFiles()?.firstOrNull { it.extension.equals("adoc", ignoreCase = true) }
            if (adoc == null) return ""
            CapsuleScenarioLoader.load(metadata, adoc, scenarioBudget)
        } else {
            CapsuleScenarioLoader.load(null, target, scenarioBudget)
        }
    }

    /**
     * Resolves the official glossary section content (CAP-GLOSSARY-2).
     *
     * The [glossaryFile] collection is fed by the wiring layer from the
     * 4-source config (ENV < props < YAML < CLI). When it contains a file,
     * the [GlossaryLoader] parses the `== Glossary` section + bullets and
     * truncates to the glossary budget. When empty or missing, the glossary
     * section is blank (backward compatible no-op).
     *
     * Token budget: 5% of the total budget (the glossary is a lightweight
     * terminological payload, not a corpus — pattern `scenarioSection`).
     */
    private fun resolveGlossarySection(budget: ChannelBudget): String {
        val files = glossaryFile.files.toList()
        if (files.isEmpty()) return ""
        val target = files.first()
        if (!target.exists()) return ""
        val glossaryBudget = (budget.totalTokenBudget * 0.05).toInt().coerceAtLeast(50)
        return GlossaryLoader.load(target, glossaryBudget)
    }
}
