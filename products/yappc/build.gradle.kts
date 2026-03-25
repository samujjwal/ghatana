import org.gradle.api.DefaultTask
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

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
        ":products:yappc:core:services-platform",  // Phase 2: moved from services/ to core/ (reusable library)
        ":products:yappc:core:services-lifecycle"   // Phase 2: moved from services/ to core/ (reusable library)
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

// ============================================================================
// Module Size Enforcement
// ============================================================================
tasks.register<CheckModuleSizeTask>("checkModuleSize") {
    description = "Fails if any module exceeds size limits"
    group = "verification"

    maxJavaFiles.set(150)
    moduleJavaDirs.set(
        subprojects.associate { subproject ->
            subproject.path to subproject.layout.projectDirectory.dir("src/main/java").asFile.absolutePath
        }
    )
}

abstract class CheckModuleSizeTask : DefaultTask() {
    @get:Input
    abstract val maxJavaFiles: Property<Int>

    @get:Input
    abstract val moduleJavaDirs: MapProperty<String, String>

    @TaskAction
    fun checkSizes() {
        val violations = mutableListOf<String>()

        moduleJavaDirs.get().forEach { (projectPath, srcDirPath) ->
            val srcDir = File(srcDirPath)
            if (srcDir.exists()) {
                val fileCount = srcDir.walkTopDown()
                    .filter { it.isFile && it.extension == "java" }
                    .count()

                if (fileCount > maxJavaFiles.get()) {
                    violations.add("$projectPath: $fileCount files (max: ${maxJavaFiles.get()})")
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Modules exceed size limit:\n" +
                    violations.joinToString("\n") { "  - $it" } + "\n" +
                    "Consider splitting large modules into focused submodules."
            )
        }

        logger.lifecycle("✓ All modules within size limits")
    }
}
tasks.named("check") {
    dependsOn("checkModuleSize")
}

// ============================================================================
// Phase 7: Structural Governance Guard
// ============================================================================
// Enforces architecture decisions from YAPPC_STRUCTURE_SIMPLIFICATION_PLAN.md:
//   Phase 2: services/ contains only the deployable (no java-library submodules)
//   Phase 3: core:yappc-domain-impl is internal-only (libs:java:yappc-domain is public)
//   Phase 5: frontend/libs/ contains no deprecated compat packages
abstract class CheckStructuralGovernanceTask : DefaultTask() {
    @get:InputFile
    abstract val settingsFile: RegularFileProperty

    @get:Input
    abstract val projectDirPath: Property<String>

    @TaskAction
    fun check() {
        val settingsText = settingsFile.get().asFile.readText()
        val projectRoot = File(projectDirPath.get())
        val violations = mutableListOf<String>()

        // RULE 1 (Phase 2): services:platform/lifecycle must NOT be re-introduced under services/
        listOf(":services:platform", ":services:lifecycle").forEach { banned ->
            val isReintroduced = settingsText.lines().any { line ->
                !line.trimStart().startsWith("//") &&
                (line.contains("include(\"$banned\")") || line.contains("\"products:yappc$banned\""))
            }
            if (isReintroduced) {
                violations.add("Phase 2: '$banned' re-introduced under services/. These are reusable libraries — put them in core/.")
            }
        }

        // RULE 2 (Phase 3): core:yappc-domain must NOT be re-introduced (renamed to yappc-domain-impl)
        val yapcDomainReintroduced = settingsText.lines().any { line ->
            !line.trimStart().startsWith("//") &&
            (line.contains("include(\":core:yappc-domain\")") ||
             line.contains("\"products:yappc:core:yappc-domain\""))
        }
        if (yapcDomainReintroduced) {
            violations.add("Phase 3: ':core:yappc-domain' re-introduced. Use ':core:yappc-domain-impl' (internal) or ':libs:java:yappc-domain' (public contract).")
        }

        // RULE 3 (Phase 5): theme must NOT appear in frontend/libs/ (only in compat/)
        if (File(projectRoot, "frontend/libs/theme").exists()) {
            violations.add("Phase 5: frontend/libs/theme/ exists. Deprecated packages must live in frontend/compat/.")
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Structural governance violations:\n" +
                violations.joinToString("\n") { "  ✗ $it" } + "\n" +
                "See docs/MODULE_CATALOG.md and docs/architecture/YAPPC_STRUCTURE_SIMPLIFICATION_PLAN.md"
            )
        }
        logger.lifecycle("✓ YAPPC structural governance: all checks passed")
    }
}

tasks.register<CheckStructuralGovernanceTask>("checkStructuralGovernance") {
    description = "Validates YAPPC module structure aligns with simplification plan"
    group = "verification"
    settingsFile.set(layout.projectDirectory.file("settings.gradle.kts"))
    projectDirPath.set(layout.projectDirectory.asFile.absolutePath)
}

tasks.named("check") {
    dependsOn("checkStructuralGovernance")
}
