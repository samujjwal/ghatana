plugins {
    id("java-module")
    `maven-publish`
    alias(libs.plugins.spotbugs)
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
    implementation(project(":products:data-cloud:platform-launcher"))
    implementation(project(":products:data-cloud:platform-api"))
    implementation(project(":products:data-cloud:platform-plugins"))

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

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
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
    // useJUnitPlatform() already applied by java-module; increase parallelism for faster test execution
    maxParallelForks = 4
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
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal()
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
 *   <li>the canonical OpenAPI spec ({@code products/data-cloud/api/openapi.yaml})
 *   <li>the canonical route registration source ({@code DataCloudRouterBuilder.java})
 * </ul>
 *
 * <p>After normalising path parameters (ActiveJ {@code :param} → OpenAPI {@code {param}}),
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
    abstract val openapiSpec: RegularFileProperty

    @get:InputFile
    abstract val routerSource: RegularFileProperty

    @TaskAction
    fun check() {
        val specPaths = extractSpecPaths(openapiSpec.get().asFile)
        val routerPaths = extractRouterPaths(routerSource.get().asFile)

        val specOnly = (specPaths - routerPaths).sorted()
        val routerOnly = (routerPaths - specPaths).sorted()

        if (specOnly.isEmpty() && routerOnly.isEmpty()) {
            logger.lifecycle(
                "✓ Data Cloud OpenAPI ↔ router in sync ({} paths, 0 drift)",
                specPaths.size
            )
            return
        }

        val report = buildString {
            appendLine()
            appendLine("╔══════════════════════════════════════════════════════════════════════╗")
            appendLine("║  Data Cloud: OpenAPI ↔ router drift detected                        ║")
            appendLine("╠══════════════════════════════════════════════════════════════════════╣")
            if (specOnly.isNotEmpty()) {
                appendLine("║  SPEC-ONLY paths (documented but NOT implemented in router):         ║")
                specOnly.forEach { path ->
                    appendLine("║    - $path")
                }
                appendLine("║  → Add these routes to DataCloudRouterBuilder or remove from spec.  ║")
            }
            if (routerOnly.isNotEmpty()) {
                appendLine("║  ROUTER-ONLY paths (implemented but NOT documented in spec):         ║")
                routerOnly.forEach { path ->
                    appendLine("║    - $path")
                }
                appendLine("║  → Add these paths to openapi.yaml or remove from the router.       ║")
            }
            appendLine("╠══════════════════════════════════════════════════════════════════════╣")
            appendLine("║  Spec: ${openapiSpec.get().asFile.relativeTo(project.rootDir)}")
            appendLine("║  Router: ${routerSource.get().asFile.relativeTo(project.rootDir)}")
            appendLine("╚══════════════════════════════════════════════════════════════════════╝")
        }
        throw GradleException(report)
    }

    /** Extracts and normalises path keys from an OpenAPI YAML {@code paths:} section. */
    private fun extractSpecPaths(spec: File): Set<String> {
        val pathPattern = Regex("""^  (/[^\s:]+)\s*:""")
        val paramPattern = Regex("""\{[^}]+\}""")
        return spec.readLines()
            .mapNotNull { line -> pathPattern.find(line)?.groupValues?.get(1) }
            .map { path -> paramPattern.replace(path, ":p") }
            .toSet()
    }

    /** Extracts and normalises path strings from ActiveJ {@code RoutingServlet.with()} calls. */
    private fun extractRouterPaths(source: File): Set<String> {
        val routePattern = Regex("""\.with\(HttpMethod\.\w+,\s*"([^"]+)"""")
        val paramPattern = Regex(""":[^/]+""")
        return source.readLines()
            .mapNotNull { line -> routePattern.find(line)?.groupValues?.get(1) }
            .map { path -> paramPattern.replace(path, ":p") }
            .toSet()
    }
}

tasks.register<CheckDataCloudOpenApiRouterSync>("checkDataCloudOpenApiSync") {
    group = "contracts"
    description = "Bidirectional check: OpenAPI spec ↔ DataCloudRouterBuilder route registration."
    openapiSpec.set(rootProject.file("products/data-cloud/api/openapi.yaml"))
    routerSource.set(file("src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java"))
}

tasks.named("check") {
    dependsOn("checkDataCloudOpenApiSync")
}

spotbugs {
    toolVersion = "4.8.6"
    ignoreFailures = true
    effort = com.github.spotbugs.snom.Effort.MAX
    reportLevel = com.github.spotbugs.snom.Confidence.MEDIUM
    excludeFilter = file("config/spotbugs-exclude.xml")
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required = true }
    reports.create("xml") { required = true }
}

