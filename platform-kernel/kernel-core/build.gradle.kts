plugins {
    id("java-module")
}

description = "Platform Kernel Core - module lifecycle and context abstractions"

dependencies {
    api(project(":platform:java:core"))  // JsonUtils and core utilities
    api(project(":platform:java:observability"))  // OpenTelemetry API for tracing
    api(libs.bundles.activej.core)
    api(libs.bundles.jackson.json)
    implementation(libs.bundles.jackson.yaml)
    implementation(libs.bundles.common.utils)
    api(libs.bundles.logging.core)
    compileOnly(libs.bundles.dev.tools)
    testCompileOnly(libs.bundles.dev.tools)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.jmh.core)
    testImplementation(libs.jmh.generator.annprocess)
}

// Fix JaCoCo task dependency
tasks.named("jacocoTestReport") {
    dependsOn("compileJava")
}

// ── Kernel Purity Gates ──────────────────────────────────────────────────────
// These tasks enforce that no product-domain terms leak into kernel source,
// resources, or documentation. Wired into the standard `check` lifecycle.

tasks.register("checkKernelPurity") {
    description = "Fails the build if product domain terms appear in kernel main sources."
    group = "verification"
    val srcDir = layout.projectDirectory.file("src/main/java").asFile
    val projectDirPath = layout.projectDirectory.asFile
    doLast {
        val PRODUCT_TERMS = listOf(
            // Legacy product-specific terms (should not appear after neutralization)
            "phr", "PHR", "finance", "Finance", "FINANCE", "CLINICAL", "Clinical",
            "phr-kernel", "finance-kernel", "patient\\.records", "trade\\.records",
            "nepal-2081", "sebon", "BillingLedger", "RiskType\\.CLINICAL",
            // Compliance tags that should be neutralized
            "SOX", "HIPAA", "GDPR", "PCI-DSS", "PCIDSS",
            // Product package references
            "com\\.ghatana\\.phr", "com\\.ghatana\\.finance",
            "com\\.ghatana\\.products\\.(aura|flashit|yappc|aep|datacloud)"
        )
        if (!srcDir.exists()) return@doLast
        val violations = mutableListOf<String>()
        srcDir.walkTopDown().filter { it.isFile && it.extension == "java" }.forEach { javaFile ->
            val content = javaFile.readText()
            PRODUCT_TERMS.forEach { term ->
                val regex = Regex(term, RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(content)) {
                    violations += "${javaFile.relativeTo(projectDirPath)}: contains banned term '$term'"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Kernel purity violation — product domain terms found in main sources:\n" +
                violations.joinToString("\n") { "  $it" }
            )
        }
        logger.lifecycle("checkKernelPurity: PASSED — no product domain terms in main sources.")
    }
}

tasks.register("checkKernelResourcePurity") {
    description = "Fails the build if product domain terms appear in kernel resources."
    group = "verification"
    val resourceDir = layout.projectDirectory.file("src/main/resources").asFile
    val projectDirPath = layout.projectDirectory.asFile
    doLast {
        val PRODUCT_TERMS = listOf(
            // Legacy product-specific terms (should not appear after neutralization)
            "phr", "PHR", "finance", "Finance", "FINANCE", "CLINICAL", "Clinical",
            "phr-kernel", "finance-kernel", "patient\\.records", "trade\\.records",
            "nepal-2081", "sebon", "BillingLedger", "RiskType\\.CLINICAL",
            // Compliance tags that should be neutralized
            "SOX", "HIPAA", "GDPR", "PCI-DSS", "PCIDSS",
            // Product package references
            "com\\.ghatana\\.phr", "com\\.ghatana\\.finance",
            "com\\.ghatana\\.products\\.(aura|flashit|yappc|aep|datacloud)"
        )
        if (!resourceDir.exists()) return@doLast
        val violations = mutableListOf<String>()
        resourceDir.walkTopDown().filter { it.isFile }.forEach { resFile ->
            val content = resFile.readText()
            PRODUCT_TERMS.forEach { term ->
                val regex = Regex(term, RegexOption.IGNORE_CASE)
                if (regex.containsMatchIn(content)) {
                    violations += "${resFile.relativeTo(projectDirPath)}: contains banned term '$term'"
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Kernel resource purity violation — product domain terms found in resources:\n" +
                violations.joinToString("\n") { "  $it" }
            )
        }
        logger.lifecycle("checkKernelResourcePurity: PASSED — no product domain terms in resources.")
    }
}

tasks.register("checkKernelDocsPurity") {
    description = "Fails the build if product domain terms appear in kernel docs or examples."
    group = "verification"
    val docsDir = layout.projectDirectory.dir("../docs").asFile
    val rootDirPath = layout.projectDirectory.dir("../..").asFile
    doLast {
        val PRODUCT_TERMS = listOf(
            // Legacy product-specific terms (should not appear after neutralization)
            "phr", "PHR", "finance", "Finance", "FINANCE", "CLINICAL", "Clinical",
            "phr-kernel", "finance-kernel", "patient\\.records", "trade\\.records",
            "nepal-2081", "sebon", "BillingLedger", "RiskType\\.CLINICAL",
            // Compliance tags that should be neutralized
            "SOX", "HIPAA", "GDPR", "PCI-DSS", "PCIDSS",
            // Product package references
            "com\\.ghatana\\.phr", "com\\.ghatana\\.finance",
            "com\\.ghatana\\.products\\.(aura|flashit|yappc|aep|datacloud)"
        )
        if (!docsDir.exists()) return@doLast
        val violations = mutableListOf<String>()
        docsDir.walkTopDown()
            .filter { it.isFile && (it.extension == "md" || it.extension == "txt" || it.extension == "yaml") }
            .forEach { docFile ->
                val content = docFile.readText()
                PRODUCT_TERMS.forEach { term ->
                    val regex = Regex(term, RegexOption.IGNORE_CASE)
                    if (regex.containsMatchIn(content)) {
                        violations += "${docFile.relativeTo(rootDirPath)}: contains banned term '$term'"
                    }
                }
            }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "Kernel docs purity violation — product domain terms found in kernel docs:\n" +
                violations.joinToString("\n") { "  $it" }
            )
        }
        logger.lifecycle("checkKernelDocsPurity: PASSED — no product domain terms in kernel docs.")
    }
}

// Wire purity gates into check lifecycle
tasks.named("check") {
    dependsOn("checkKernelPurity", "checkKernelResourcePurity", "checkKernelDocsPurity")
}

// Ensure purity validation tests run as part of test
tasks.named<Test>("test") {
    // Run purity validation tests as part of standard test suite
    useJUnitPlatform {
        includeTags("purity-validation")
    }
}
