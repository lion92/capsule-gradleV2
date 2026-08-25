// ── buildscript resolutionStrategy ────────────────────────────────────────────────
// koog-agents 1.0.0 → koog-utils/koog-http-client-core/koog-prompt-llm → annotations.
// codebase-plugin exclut koog-agents mais les sous-modules koog transitifs
// contournent l'exclusion. Solution : forcer annotations:26.0.2-1 (pattern slider).
import build.CucumberTaskSpec
buildscript {
    repositories { mavenLocal(); mavenCentral() }
    configurations.all { resolutionStrategy { force("org.jetbrains:annotations:26.0.2-1") } }
}

plugins {
    id("education.cccp.build.gradle-plugin") version "0.0.3"
    id("education.cccp.build.publishing") version "0.0.3"
    id("education.cccp.build.functional-test") version "0.0.3"
    id("education.cccp.build.cucumber") version "0.0.3"
    alias(libs.plugins.kover)
    alias(libs.plugins.codebase)
}

group = "education.cccp"
version = libs.plugins.capsule.get().version

repositories {
    mavenLocal()
    mavenCentral()
}

cucumberConventions {
    featuresDir = "src/test/features"
    additionalTasks = listOf(
        // Focused cucumber run for the CAP-ARCH-2 augmented context feature
        // (fast iteration without the full Playwright suite, ~15 min).
        CucumberTaskSpec(
            name = "cucumberTestContext",
            features = listOf("src/test/features/capsule_context.feature"),
            tags = listOf("@context"),
            runnerClass = "capsule.scenarios.CapsuleContextCucumberRunner",
            timeoutMinutes = 30,
        ),
        // Focused cucumber run for the CAP-ARCH-4 content+videos wiring feature
        // (fast iteration without the full Playwright suite, ~15 min).
        CucumberTaskSpec(
            name = "cucumberTestContentAndVideos",
            features = listOf("src/test/features/capsule_content_and_videos.feature"),
            tags = listOf("@architecture"),
            runnerClass = "capsule.scenarios.CapsuleContentAndVideosCucumberRunner",
            timeoutMinutes = 30,
        ),
        // Focused cucumber run for the CAP-ARCH-6 content + video boundary feature
        // (fast iteration — single-language French, NoOp engines, mock LLM HTTP).
        CucumberTaskSpec(
            name = "cucumberTestArchBoundary",
            features = listOf("src/test/features/capsule_arch_boundary.feature"),
            tags = listOf("@cross-borough"),
            runnerClass = "capsule.scenarios.CapsuleArchBoundaryCucumberRunner",
            timeoutMinutes = 30,
        ),
        // Focused cucumber run for the CAP-ARCH-7 US-4 video destination versioning feature
        // (fast iteration without the full Playwright suite, ~15 min).
        CucumberTaskSpec(
            name = "cucumberTestDistrib",
            features = listOf("src/test/features/capsule_distrib.feature"),
            tags = listOf("@distrib"),
            runnerClass = "capsule.scenarios.CapsuleDistribCucumberRunner",
            timeoutMinutes = 30,
        ),
        // Focused cucumber run for the CAP-CR3-2 strict mode anti-NoOp feature
        // (fast iteration — pure config, no Playwright/FFmpeg needed).
        CucumberTaskSpec(
            name = "cucumberTestStrictMode",
            features = listOf("src/test/features/capsule_strict_mode.feature"),
            tags = listOf("@strict"),
            runnerClass = "capsule.scenarios.CapsuleStrictModeCucumberRunner",
            timeoutMinutes = 30,
        ),
        // Focused cucumber run for the CAP-DOCCONTEXT-4 documentary corpus injection feature
        // (fast iteration — pure config + file globs, no Playwright/FFmpeg needed).
        CucumberTaskSpec(
            name = "cucumberTestDocContext",
            features = listOf("src/test/features/capsule_doc_context.feature"),
            tags = listOf("@context", "@docs"),
            runnerClass = "capsule.scenarios.CapsuleDocContextCucumberRunner",
            timeoutMinutes = 30,
        ),
        // Focused cucumber run for the CAP-SPD-4 pedagogical scenario injection feature
        // (fast iteration — pure config + scenario file, no Playwright/FFmpeg needed).
        CucumberTaskSpec(
            name = "cucumberTestScenarioContext",
            features = listOf("src/test/features/capsule_scenario_context.feature"),
            tags = listOf("@context", "@scenario"),
            runnerClass = "capsule.scenarios.CapsuleScenarioContextCucumberRunner",
            timeoutMinutes = 30,
        ),
        // CAP-CR3-3-5 — capture strategy selection (PLAYWRIGHT default, SCREENSHOT alt).
        // Dedicated runner so it never runs the full Playwright suite (pattern S-082).
        CucumberTaskSpec(
            name = "cucumberTestCaptureStrategy",
            features = listOf("src/test/features/capsule_capture_strategy.feature"),
            tags = listOf("@capture", "@strategy"),
            runnerClass = "capsule.scenarios.CapsuleCaptureStrategyCucumberRunner",
            timeoutMinutes = 30,
        ),
        // CAP-MP4 US-3 — output format distribution (WEBM default, MP4, BOTH).
        // Dedicated runner so it never runs the full Playwright suite (pattern S-082).
        // Uses a NoOp converter (no real FFmpeg); validates DistributeCapsuleVideoTask filter.
        CucumberTaskSpec(
            name = "cucumberTestFormat",
            features = listOf("src/test/features/capsule_format.feature"),
            tags = listOf("@distrib", "@format"),
            runnerClass = "capsule.scenarios.CapsuleFormatCucumberRunner",
            timeoutMinutes = 30,
        ),
        // CAP-CR3-1 US-3 — duration validation (disabled default, enabled skip,
        // enabled valid). Dedicated runner so it never runs the full Playwright
        // suite (pattern S-082). Uses NoOp probe (no real ffprobe).
        CucumberTaskSpec(
            name = "cucumberTestDurationValidation",
            features = listOf("src/test/features/capsule_duration_validation.feature"),
            tags = listOf("@validation", "@duration"),
            runnerClass = "capsule.scenarios.CapsuleDurationValidationCucumberRunner",
            timeoutMinutes = 30,
        ),
        // CAP-AUDIO US-4 — audio post-production (BGM + loudness EBU R128 + ducking).
        // Dedicated runner so it never runs the full Playwright suite (pattern S-082).
        // Uses a NoOp processor (no real FFmpeg); validates applyAudioPostIfEnabled
        // wiring + economy-of-ink guard + factory dispatch in CapsuleVideoTask.
        CucumberTaskSpec(
            name = "cucumberTestAudioPost",
            features = listOf("src/test/features/capsule_audio_post.feature"),
            tags = listOf("@audio", "@post"),
            runnerClass = "capsule.scenarios.CapsuleAudioPostCucumberRunner",
            timeoutMinutes = 30,
        ),
        // CAP-PROVENANCE US-2 — context provenance audit artefact.
        // Dedicated runner so it never runs the full Playwright suite (pattern S-082).
        // Validates the context-provenance.json channels/sources/totals + log line.
        CucumberTaskSpec(
            name = "cucumberTestProvenance",
            features = listOf("src/test/features/capsule_provenance.feature"),
            tags = listOf("@context", "@provenance"),
            runnerClass = "capsule.scenarios.CapsuleProvenanceCucumberRunner",
            timeoutMinutes = 30,
        ),
        // CAP-GLOSSARY US-3 — glossary terminology injection (context augmenté).
        // Dedicated runner so it never runs the full Playwright suite (pattern S-082).
        // Validates the ==== Official Glossary (glossary) section + no-op fallback
        // + malformed glossary gracefully skipped via collectCapsuleAugmentedContext.
        CucumberTaskSpec(
            name = "cucumberTestGlossaryContext",
            features = listOf("src/test/features/capsule_glossary_context.feature"),
            tags = listOf("@context", "@glossary"),
            runnerClass = "capsule.scenarios.CapsuleGlossaryContextCucumberRunner",
            timeoutMinutes = 30,
        ),
        // CAP-TRANSCRIPT US-5 — transcript generation (TEMPLATE and LLM strategies).
        // Dedicated runner so it never runs the full Playwright suite (pattern S-082).
        CucumberTaskSpec(
            name = "cucumberTestTranscript",
            features = listOf("src/test/features/capsule_transcript.feature"),
            tags = listOf("@transcript"),
            runnerClass = "capsule.scenarios.CapsuleTranscriptCucumberRunner",
            timeoutMinutes = 30,
        ),
        // CAP-ANIM US-2 — Remotion capture strategy BDD (NoOp fallback, strictMode,
        // config DSL). Dedicated runner pattern S-082; steps prefixed "remotion"
        // (bug S-088 glue capsule.scenarios shared). No @integration — real
        // Remotion render is gated CI/dogfooding.
        CucumberTaskSpec(
            name = "cucumberTestRemotion",
            features = listOf("src/test/features/capsule_remotion.feature"),
            tags = listOf("@anim", "@remotion"),
            runnerClass = "capsule.scenarios.CapsuleRemotionCucumberRunner",
            timeoutMinutes = 15,
        ),
        // CAP-PODCAST US-2 — podcast MP3 extraction (audio-only mobile consumption).
        // Dedicated runner pattern S-082; steps prefixed "podcast" (bug S-088 glue
        // capsule.scenarios shared). Uses NoOp concatenator (no real FFmpeg); validates
        // wiring + economy-of-ink guard + factory dispatch, not the concat itself
        // (covered by US-1 unit tests for PodcastConcatenatorImpl/PodcastConcatCommand).
        CucumberTaskSpec(
            name = "cucumberTestPodcast",
            features = listOf("src/test/features/capsule_podcast.feature"),
            tags = listOf("@podcast"),
            runnerClass = "capsule.scenarios.CapsulePodcastCucumberRunner",
            timeoutMinutes = 30,
        ),
        // Integration cucumber run — scenarios tagged @integration or @manim
        // (burn-in E2E with real ffmpeg, Manim pipeline NoOp). Excluded from the
        // default cucumberTest runner by `not @integration`; this dedicated task
        // exercises them via CucumberIntegrationTestRunner. Run explicitly:
        //   ./gradlew cucumberTestIntegration -PrunCucumber
        CucumberTaskSpec(
            name = "cucumberTestIntegration",
            features = listOf("src/test/features"),
            tags = listOf("@integration"),
            runnerClass = "capsule.scenarios.CucumberIntegrationTestRunner",
            timeoutMinutes = 30,
        ),
    )
}

// CR-10 — cucumberTest is expensive (Playwright + FFmpeg, ~15 min).
// Skip unless -PrunCucumber or CI env var is active.
// Decision logic documented/tested in capsule.ci.CucumberTestGuard (pure domain).
afterEvaluate {
    val hasRunCucumber = project.hasProperty("runCucumber")
    val isCi = System.getenv("CI") == "true"
    val shouldRun = hasRunCucumber || isCi

    tasks.named("cucumberTest").configure {
        onlyIf { shouldRun }
        doFirst {
            if (!shouldRun) {
                logger.lifecycle("cucumberTest skipped (pass -PrunCucumber or set CI=true to enable)")
            }
        }
    }

    // Playwright tests — spin a real Chromium browser, 15-30 min.
    // Skip by default unless -PrunPlaywrightTests or CI env var is active.
    // Pattern mirror CR-10. Decision logic in capsule.ci.PlaywrightTestGuard.
    val hasRunPlaywright = project.hasProperty("runPlaywrightTests")
    val shouldRunPlaywright = hasRunPlaywright || isCi

    tasks.named("cucumberTestIntegration").configure {
        onlyIf { shouldRunPlaywright }
        doFirst {
            if (!shouldRunPlaywright) {
                logger.lifecycle("cucumberTestIntegration skipped (pass -PrunPlaywrightTests or set CI=true to enable)")
            }
        }
    }
}

dependencies {
    implementation(platform("education.cccp:workspace-bom:${libs.versions.workspace.bom.get()}"))
    implementation(kotlin("stdlib-jdk8"))

    compileOnly(libs.slider)
    implementation(libs.playwright)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.dataformat.yaml)

    // N1 codebase — LLM socle (CAP-ARCH-1): LlmBuildService + LlmProvider
    implementation(libs.codebase.plugin)

    // koog-agents — orchestration DSL (CAP-ARCH-3). Not transitive via codebase
    // (codebase exposes it as `implementation`), so capsule depends directly via BOM.
    implementation(libs.koog.agents) {
        exclude(group = "org.jetbrains", module = "annotations")
    }

    // langchain4j — ChatModel bridge (LlmProviderChatModelAdapter, CAP-ARCH-1)
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.ollama)

    // N0 contracts — i18n (LanguageCatalog 10 languages, cross-borough translation alliance)
    implementation(libs.i18n.contracts)

    // N0 contracts — codebase context (CompositeContext/ContextChannel/ChannelBudget, CAP-ARCH-2)
    implementation(libs.codebase.contracts)

    testImplementation(kotlin("test-junit5"))
    testImplementation("org.assertj:assertj-core:3.27.7")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("ch.qos.logback:logback-classic:1.5.26")
    testImplementation(libs.bundles.cucumber)
    testImplementation("io.cucumber:cucumber-junit:7.34.3")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.10.0")
}

afterEvaluate {
    configurations.getByName("functionalTestImplementation").extendsFrom(
        configurations.getByName("testImplementation")
    )
}

// Groovy 3 leaks onto the unit-test runtime classpath through
// slider → asciidoctor-gradle-jvm-slides → jrubygradle-resolver
// (org.codehaus.groovy:groovy:3.0.17). Groovy 3 cannot initialize on JDK 24+
// ("Could not initialize class org.codehaus.groovy.runtime.InvokerHelper") and it
// shadows the Groovy 4 that Gradle puts on the test worker classpath, so every
// ProjectBuilder-based test blows up. Gradle ships the Groovy that ProjectBuilder
// needs. Scoped to `test`: functionalTest applies slider in a real nested build.
configurations.named("testRuntimeClasspath") {
    exclude(group = "org.codehaus.groovy")
}

gradlePlugin {
    val capsule by plugins.creating {
        id = "education.cccp.capsule"
        implementationClass = "capsule.CapsulePlugin"
    }
}

kover {
    reports {
        total {
            xml { onCheck = true }
            html { onCheck = true }
        }
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            if (name == "pluginMaven") {
                pom {
                    name.set("Capsule Gradle Plugin")
                    description.set("Generation automatisee de capsules video pedagogiques depuis des decks reveal.js")
                    url.set("https://github.com/cheroliv/capsule-gradle/")
                }
            }
        }
    }
}