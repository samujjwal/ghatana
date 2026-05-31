plugins {
    id("java-module")
    `maven-publish`
    alias(libs.plugins.spotbugs)
}

sourceSets {
    named("main") {
        java {
            // Keep contract-generated snippet artifacts out of javac inputs.
            exclude("**/*.generated.java")
        }
    }
}

val runLauncher by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Runs the Data Cloud standalone launcher"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.ghatana.datacloud.launcher.DataCloudLauncher")
    standardInput = System.`in`
}

dependencies {
    // Platform modules
    implementation(project(":products:data-cloud:delivery:runtime-composition"))
    implementation(project(":products:data-cloud:delivery:api"))
    implementation(project(":products:data-cloud:extensions:agent-registry"))
    implementation(project(":products:data-cloud:extensions:plugins"))
    implementation(project(":products:data-cloud:planes:action:operator-contracts"))

    // Core platform dependencies
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:governance"))
    implementation(project(":platform:java:audit"))
    implementation(project(":platform:java:security"))

    // AI platform integration — model registry, feature store, observability (all merged into ai-integration)
    implementation(project(":platform:java:ai-integration"))

    // HikariCP for AI service DataSource creation in standalone launcher
    implementation(libs.hikaricp)

    // gRPC transport (runtime) — needed to start the gRPC server
    implementation(libs.grpc.netty.shaded)

    // ActiveJ framework
    implementation(libs.activej.launcher)
    implementation(libs.activej.http)
    implementation(libs.activej.inject)
    implementation(libs.activej.config)
    implementation(libs.bundles.activej.core)
    implementation(libs.activej.promise)
    implementation(libs.activej.csp)
    implementation(libs.activej.bytebuf)

    // Jackson for JSON
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.core)
    compileOnly("com.github.spotbugs:spotbugs-annotations:4.8.6")

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation(libs.h2)
    testImplementation(project(":platform:java:testing"))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")

    // Testcontainers for integration testing
    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("org.testcontainers:postgresql:1.19.7")
    testImplementation("org.testcontainers:kafka:1.19.7")
    testImplementation("org.testcontainers:localstack:1.19.7")
    testImplementation("org.testcontainers:junit-jupiter:1.19.7")
}

tasks.test {
    // Launcher HTTP tests bind real local ports and mutate process-level runtime profile state.
    // Keep them in one fork so the full repo check is deterministic.
    maxParallelForks = 1
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            // J1: Raised threshold to 55% after adding comprehensive tests for media, connectors, and Action Plane handlers
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.55".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

// ── OpenAPI ↔ router drift detection ────────────────────────────────────────

/**
 * Bidirectional OpenAPI ↔ controller drift check for Data Cloud.
 *
 * <p>Extracts all HTTP paths from:
 * <ul>
 *   <li>the non-Action Data Cloud OpenAPI spec ({@code products/data-cloud/contracts/openapi/data-cloud.yaml})
 *   <li>the canonical Action Plane OpenAPI spec ({@code products/data-cloud/contracts/openapi/action-plane.yaml})
 *   <li>the canonical route registration source ({@code DataCloudRouterBuilder.java})
 * </ul>
 *
 * <p>After converting path parameters (ActiveJ {@code :param} → OpenAPI {@code {param}}),
 * the task fails the build if:
 * <ul>
 *   <li><b>spec-only</b>: a path appears in the spec but has no matching route registration —
 *       the endpoint is documented but unimplemented.
 *   <li><b>router-only</b>: a path appears in the router but is absent from the spec —
 *       the endpoint is implemented but undocumented.
 * </ul>
 *
 * @doc.type task
 * @doc.purpose Detect bidirectional drift between the OpenAPI specification and the
 *              Data Cloud HTTP router registration to prevent undocumented or
 *              unimplemented endpoints from reaching production.
 * @doc.layer product
 * @doc.pattern Contract Validation
 */
abstract class CheckDataCloudOpenApiRouterSync : DefaultTask() {

    @get:InputFile
    abstract val dataOpenapiSpec: RegularFileProperty

    @get:InputFile
    abstract val actionOpenapiSpec: RegularFileProperty

    @get:InputFile
    abstract val compatibilityRegistry: RegularFileProperty

    @get:InputFile
    abstract val routerSource: RegularFileProperty

    @get:Internal
    val rootDir = project.rootDir

    @TaskAction
    fun check() {
        val dataSpecPaths = extractSpecPaths(dataOpenapiSpec.get().asFile)
        val actionSpecPaths = extractSpecPaths(actionOpenapiSpec.get().asFile)
        val compatibilityPaths = extractCompatibilityPaths(compatibilityRegistry.get().asFile)
        val routerPaths = extractRouterPaths(routerSource.get().asFile)
        val nonActionRouterPaths = routerPaths.filterNot { it.startsWith("/api/v1/action/") }.toSet()
        val dataRouterPaths = nonActionRouterPaths.filterNot { it in compatibilityPaths }.toSet()
        val compatibilitySpecInData = dataSpecPaths.intersect(compatibilityPaths).sorted()
        val actionRouterPaths = routerPaths.filter { it.startsWith("/api/v1/action/") }.toSet()

        val dataSpecOnly = (dataSpecPaths - dataRouterPaths).sorted()
        val dataRouterOnly = (dataRouterPaths - dataSpecPaths).sorted()
        val actionSpecOnly = (actionSpecPaths - actionRouterPaths).sorted()
        val actionRouterOnly = (actionRouterPaths - actionSpecPaths).sorted()

        if (dataSpecOnly.isEmpty()
            && dataRouterOnly.isEmpty()
            && actionSpecOnly.isEmpty()
            && actionRouterOnly.isEmpty()
            && compatibilitySpecInData.isEmpty()) {
            logger.lifecycle(
                "✓ Data Cloud OpenAPI ↔ router in sync ({} data paths, {} action paths, {} compatibility aliases, 0 drift)",
                dataSpecPaths.size,
                actionSpecPaths.size,
                nonActionRouterPaths.intersect(compatibilityPaths).size
            )
            return
        }

        val report = buildString {
            appendLine()
            appendLine("╔══════════════════════════════════════════════════════════════════════╗")
            appendLine("║  Data Cloud: OpenAPI ↔ router drift detected                        ║")
            appendLine("╠══════════════════════════════════════════════════════════════════════╣")
            if (dataSpecOnly.isNotEmpty()) {
                appendLine("║  DATA SPEC-ONLY paths (documented but NOT implemented in router):    ║")
                dataSpecOnly.forEach { path ->
                    appendLine("║    - $path")
                }
                appendLine("║  → Add these routes to DataCloudRouterBuilder or remove from spec.  ║")
            }
            if (dataRouterOnly.isNotEmpty()) {
                appendLine("║  DATA ROUTER-ONLY paths (implemented but NOT documented in spec):    ║")
                dataRouterOnly.forEach { path ->
                    appendLine("║    - $path")
                }
                appendLine("║  → Add these paths to data-cloud.yaml or remove from the router.     ║")
            }
            if (actionSpecOnly.isNotEmpty()) {
                appendLine("║  ACTION SPEC-ONLY paths (documented but NOT implemented in router):  ║")
                actionSpecOnly.forEach { path ->
                    appendLine("║    - $path")
                }
                appendLine("║  → Add these routes to DataCloudRouterBuilder or remove from spec.  ║")
            }
            if (actionRouterOnly.isNotEmpty()) {
                appendLine("║  ACTION ROUTER-ONLY paths (implemented but NOT documented in spec):  ║")
                actionRouterOnly.forEach { path ->
                    appendLine("║    - $path")
                }
                appendLine("║  → Add these paths to action-plane.yaml or remove from the router.  ║")
            }
            if (compatibilitySpecInData.isNotEmpty()) {
                appendLine("║  DATA SPEC CONTAINS LEGACY ACTION COMPATIBILITY PATHS:               ║")
                compatibilitySpecInData.forEach { path ->
                    appendLine("║    - $path")
                }
                appendLine("║  → Move these to route-compatibility-registry.yaml/aep.yaml only.   ║")
            }
            appendLine("╠══════════════════════════════════════════════════════════════════════╣")
            appendLine("║  Data spec: ${dataOpenapiSpec.get().asFile.relativeTo(rootDir)}")
            appendLine("║  Action spec: ${actionOpenapiSpec.get().asFile.relativeTo(rootDir)}")
            appendLine("║  Compatibility registry: ${compatibilityRegistry.get().asFile.relativeTo(rootDir)}")
            appendLine("║  Router: ${routerSource.get().asFile.relativeTo(rootDir)}")
            appendLine("╚══════════════════════════════════════════════════════════════════════╝")
        }
        throw GradleException(report)
    }

    /** Extracts path keys from an OpenAPI YAML {@code paths:} section. */
    private fun extractSpecPaths(spec: File): Set<String> {
        val pathPattern = Regex("""^  (/[^\s:]+)\s*:""")
        return spec.readLines()
            .mapNotNull { line -> pathPattern.find(line)?.groupValues?.get(1) }
            .toSet()
    }

    /** Extracts legacy compatibility paths from route-compatibility-registry.yaml. */
    private fun extractCompatibilityPaths(registry: File): Set<String> {
        val pathPattern = Regex("""^\s*- path: "([^"]+)"""")
        val paramPattern = Regex(""":([A-Za-z_][A-Za-z0-9_]*)""")
        return registry.readLines()
            .mapNotNull { line -> pathPattern.find(line)?.groupValues?.get(1) }
            .map { path -> paramPattern.replace(path, "{$1}") }
            .toSet()
    }

    /** Extracts path strings from ActiveJ {@code RoutingServlet.with()} calls. */
    private fun extractRouterPaths(source: File): Set<String> {
        val routePattern = Regex("""\.with\(HttpMethod\.\w+,\s*"([^"]+)"""")
        val paramPattern = Regex(""":([A-Za-z_][A-Za-z0-9_]*)""")
        return source.readLines()
            .mapNotNull { line -> routePattern.find(line)?.groupValues?.get(1) }
            .map { path -> paramPattern.replace(path, "{$1}") }
            .toSet()
    }
}

tasks.register<CheckDataCloudOpenApiRouterSync>("checkDataCloudOpenApiSync") {
    group = "contracts"
    description = "Bidirectional check: OpenAPI specs ↔ DataCloudRouterBuilder route registration."
    dataOpenapiSpec.set(rootProject.file("products/data-cloud/contracts/openapi/data-cloud.yaml"))
    actionOpenapiSpec.set(rootProject.file("products/data-cloud/contracts/openapi/action-plane.yaml"))
    compatibilityRegistry.set(rootProject.file("products/data-cloud/contracts/openapi/route-compatibility-registry.yaml"))
    routerSource.set(file("src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java"))
}

abstract class CheckForbiddenLauncherMarkers : DefaultTask() {

    @get:InputDirectory
    abstract val sourceDir: DirectoryProperty

    @TaskAction
    fun check() {
        val patterns = listOf(
            Regex("temporarily disabled due to .*type mismatch", RegexOption.IGNORE_CASE),
            Regex("TODO:\\s*Fix\\s+.*type mismatch", RegexOption.IGNORE_CASE)
        )

        val violations = mutableListOf<String>()
        sourceDir.get().asFile
            .walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (patterns.any { it.containsMatchIn(line) }) {
                        val relativePath = file.relativeTo(project.rootDir).path
                        violations.add("$relativePath:${index + 1}: ${line.trim()}")
                    }
                }
            }

        if (violations.isNotEmpty()) {
            val report = buildString {
                appendLine("Forbidden temporary-disable/type-mismatch markers found in production launcher source:")
                violations.sorted().forEach { appendLine(" - $it") }
                appendLine("Remove these markers by implementing capability gates or production code paths.")
            }
            throw GradleException(report)
        }

        logger.lifecycle("✓ No forbidden temporary-disable/type-mismatch markers found in launcher production source")
    }
}

tasks.register<CheckForbiddenLauncherMarkers>("checkForbiddenLauncherMarkers") {
    group = "verification"
    description = "Fails build if forbidden temporary-disable or type-mismatch TODO markers are present in launcher production source."
    sourceDir.set(layout.projectDirectory.dir("src/main/java"))
}

tasks.named("check") {
    dependsOn("checkDataCloudOpenApiSync")
    dependsOn("checkForbiddenLauncherMarkers")
    dependsOn("spotbugsMain")
}

spotbugs {
    toolVersion = "4.8.6"
    ignoreFailures = false
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    excludeFilter = file("config/spotbugs-exclude.xml")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
    reports.create("xml") { required = true }
    // J1: Fail build if high-confidence bugs are found
    maxHeapSize = "2g"
}

tasks.register<Test>("boundaryCheck") {
    group = "verification"
    description = "Runs Data Cloud launcher boundary/architecture ArchUnit suites."
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    include("**/DataCloudArchitectureTest.class")
    include("**/DataCloudPlaneBoundaryTest.class")
}
