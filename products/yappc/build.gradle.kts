plugins {
    id("java")
    id("org.owasp.dependencycheck") version "12.1.6"
    id("com.github.spotbugs") version "6.4.2" apply false
    // NOTE: spotless plugin is provided via buildSrc and applied selectively via conventions;
    // do NOT re-declare it here to avoid plugin marker resolution conflicts.
    // Individual modules apply it by referencing the convention plugin.
}

group = "com.ghatana.products.yappc"
version = "2026.3.1-SNAPSHOT"

description = "YAPPC — AI-Native Product Development Platform"

// Add dependencies for JSON and YAML processing
buildscript {
    dependencies {
        classpath("org.json:json:20231013")
        classpath("org.yaml:snakeyaml:2.0")
    }
}

// ============================================================================
// Shared Configuration for All Subprojects
// ============================================================================
subprojects {
    if (!file("$projectDir/src/main/java").exists() &&
        !file("$projectDir/src/main/kotlin").exists() &&
        !file("$projectDir/src/test/java").exists()) {
        return@subprojects
    }

    apply(plugin = "java-library")

    group = "com.ghatana.products.yappc"
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
        withJavadocJar()
        withSourcesJar()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    tasks.withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
        options.encoding = "UTF-8"
    }

    val spotbugsProjects = setOf(
        ":products:yappc:backend:api",
        ":products:yappc:services:platform",  // replaces the merged domain + infrastructure
        ":products:yappc:services:lifecycle"   // absorbs services:ai and services:scaffold
    )

    if (path in spotbugsProjects) {
        apply(plugin = "com.github.spotbugs")

        configure<com.github.spotbugs.snom.SpotBugsExtension> {
            effort.set(com.github.spotbugs.snom.Effort.MAX)
            reportLevel.set(com.github.spotbugs.snom.Confidence.LOW)
            excludeFilter.set(rootProject.file("config/spotbugs/spotbugs-exclude.xml"))
        }

        tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
            reports {
                create("html") {
                    required.set(true)
                }
                create("xml") {
                    required.set(false)
                }
            }
        }

        tasks.matching { it.name == "check" }.configureEach {
            dependsOn("spotbugsMain")
        }
    }

    // ========================================================================
    // Dependency Governance Constraints
    // ========================================================================
    configurations.all {
        // Ban direct LangChain4J — use platform:java:ai-integration instead
        exclude(group = "dev.langchain4j", module = "langchain4j")
        exclude(group = "dev.langchain4j", module = "langchain4j-open-ai")
        exclude(group = "dev.langchain4j", module = "langchain4j-anthropic")

        // Ban Spring Reactor — use ActiveJ Promise
        exclude(group = "io.projectreactor", module = "reactor-core")

        // Ban RxJava — use ActiveJ Promise
        exclude(group = "io.reactivex.rxjava3", module = "rxjava")
    }
}

configure<org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension> {
    suppressionFile = rootProject.file("../../config/owasp-suppressions.xml").absolutePath
    formats = listOf("HTML", "JSON")
    failBuildOnCVSS = 9.0F
}

// ============================================================================
// Config & Catalog Validation Tasks
// ============================================================================
// Validation tasks are maintained in a dedicated script for readability.
// Tasks registered: validateAgentCatalog, validateEventSchemas, validatePipelines,
//                   validateLifecycleConfig, validateWorkflowConfig, validatePolicyConfig
apply(from = "gradle/yappc-validations.gradle.kts")

// ============================================================================
// Phase F: Thin-Module Re-introduction Guard
// ============================================================================
// Prevents accidental re-introduction of the modules removed during the
// YAPPC consolidation (see docs/adr/YAPPC_RESTRUCTURING_PLAN.md).
tasks.register("checkNoThinModuleReintroduction") {
    description = "Fails if any banned thin YAPPC module is re-introduced into settings"
    group = "verification"

    val settingsFile = layout.projectDirectory.file("settings.gradle.kts")
    inputs.file(settingsFile)

    doLast {
        val settingsText = settingsFile.asFile.readText()

        val bannedModules = listOf(
            ":services:ai",
            ":services:scaffold",
            ":core:scaffold:packs",
            ":backend:websocket",
            ":infrastructure:security",
            ":launcher"
        )

        val violations = bannedModules.filter { mod ->
            // Match an uncommented include() call for the banned module
            settingsText.lines().any { line ->
                !line.trimStart().startsWith("//") &&
                (line.contains("include(\"$mod\")") || line.contains("include(':$mod')"))
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Thin module re-introduction detected in settings.gradle.kts:\n" +
                violations.joinToString("\n") { "  - $it" } + "\n" +
                "These modules were consolidated per YAPPC_RESTRUCTURING_PLAN.md. " +
                "Add code to their target hosts instead."
            )
        }

        logger.lifecycle("✓ No banned thin modules detected in settings.gradle.kts")
    }
}

tasks.named("check") {
    dependsOn("checkNoThinModuleReintroduction")
}
