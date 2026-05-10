/**
 * Core Observability Module for Platform Plugins
 *
 * @doc.type build-script
 * @doc.purpose Provides base observability abstractions (spans, metrics, correlation IDs) for all platform plugins
 * @doc.layer platform
 */
plugins {
    id("java-module")
}

group = "com.ghatana.plugin"
version = rootProject.version
description = "Core Observability - OpenTelemetry instrumentation base for platform plugins"

dependencies {
    // Kernel and Platform libraries via BOMs
    implementation(platform(project(":platform-kernel:kernel-bom")))
    implementation(platform(project(":platform:java:platform-bom")))

    // Kernel core for CorrelationIdContext
    api(project(":platform-kernel:kernel-core"))

    // OpenTelemetry API (context is bundled in the API artifact)
    api(libs.opentelemetry.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
}

// ── Plugin Purity Gate ───────────────────────────────────────────────────────
// Enforces that no product domain terms appear in plugin main sources.
// See docs/PLUGIN_PURITY_RULES.md for the full list of banned patterns.

tasks.register("checkPluginPurity") {
    description = "Fails the build if product domain terms appear in plugin main sources."
    group = "verification"
    val srcDir = layout.projectDirectory.file("src/main/java").asFile
    val projectBaseDir = layout.projectDirectory.asFile
    doLast {
        val PLUGIN_BANNED_TERMS = listOf(
            "\\bPHR\\b", "CLINICAL", "\\bFinance\\b", "FINANCE", "SOX", "HIPAA",
            "GDPR", "PCI-DSS", "PCIDSS", "patient\\.records", "trade\\.records"
        )
        if (!srcDir.exists()) return@doLast
        val violations = mutableListOf<String>()
        srcDir.walkTopDown().filter { it.isFile && it.extension == "java" }.forEach { javaFile ->
            val content = javaFile.readText()
            PLUGIN_BANNED_TERMS.forEach { term ->
                if (Regex(term).containsMatchIn(content)) {
                    violations += "${javaFile.relativeTo(projectBaseDir)}: contains banned product term '$term'"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Plugin purity violation — product domain terms found in main sources:\n" +
                violations.joinToString("\n") { "  $it" }
            )
        }
        logger.lifecycle("checkPluginPurity: PASSED — no product domain terms in main sources.")
    }
}

tasks.named("check") {
    dependsOn("checkPluginPurity")
}
