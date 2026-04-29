/**
 * products/aep/contracts — AEP Formal API Contract Module
 *
 * Holds the canonical OpenAPI 3.0 specification for the AEP REST API and
 * validates it at build time using the OpenAPI Generator Gradle plugin.
 *
 * Design intent:
 *  - The spec ({@code openapi.yaml}) is the single source of truth for AEP's
 *    HTTP API surface. It must be kept in sync with the actual Fastify BFF
 *    and the Java HTTP server handlers.
 *  - At build time, {@code validateAepSpec} runs OpenAPI Generator in
 *    validation-only mode. Any schema error or broken reference fails the build.
 *  - The {@code generateAepTypescriptTypes}, {@code generateAepJavaSdk} tasks
 *    can generate typed client/server stubs for use in {@code api/} and
 *    test-scripts respectively.
 *
 * Usage:
 *   ./gradlew :products:aep:contracts:validateAepSpec
 *   ./gradlew :products:aep:contracts:generateAepTypescriptTypes
 *   ./gradlew :products:aep:contracts:generateAepJavaSdk
 *   ./gradlew :products:aep:contracts:build   (runs validateAepSpec automatically)
 *
 * @doc.type module
 * @doc.purpose AEP API contract validation and codegen
 * @doc.layer product
 * @doc.pattern Contract-First
 */
plugins {
    base
}

group = "com.ghatana.aep"
version = rootProject.version

val openApiGeneratorCli by configurations.creating

dependencies {
    openApiGeneratorCli("org.openapitools:openapi-generator-cli:${libs.versions.openapi.generator.get()}")
}

// ─────────────────────────────────────────────────────────────────────────────
// Canonical spec location
// ─────────────────────────────────────────────────────────────────────────────
val specFile = file("openapi.yaml")

// ─────────────────────────────────────────────────────────────────────────────
// TASK: Validate spec at build time
//
// Runs openapi-generator in validation mode only — does not produce output
// files. Any schema error, broken $ref, or invalid security definition
// causes this task to fail and blocks the build.
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Validates the AEP OpenAPI specification at build time.
 *
 * @doc.type task
 * @doc.purpose Ensure AEP OpenAPI spec is schema-valid before any build artifact is produced
 * @doc.layer product
 * @doc.pattern Contract Validation
 */
tasks.register<JavaExec>("validateAepSpec") {
    group = "contracts"
    description = "Validates the AEP OpenAPI 3.0 specification against the OpenAPI schema."

    classpath = openApiGeneratorCli
    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")
    args("validate", "-i", specFile.absolutePath, "--recommend")
}
// 
// // ─────────────────────────────────────────────────────────────────────────────
// // TASK: Generate TypeScript types for the AEP BFF (api/ module)
// //
// // Generates TypeScript interfaces + fetch-based client stubs from the spec.
// // Output lands in build/generated/typescript/ and can be copied/linked into
// // the api/ Node.js module for type-safe proxy routes.
// // ─────────────────────────────────────────────────────────────────────────────
// /**
//  * Generates TypeScript type definitions and a fetch client from the AEP spec.
//  *
//  * @doc.type task
//  * @doc.purpose Generate TypeScript types for the AEP BFF and UI
//  * @doc.layer product
//  * @doc.pattern Code Generation
//  */
tasks.register<JavaExec>("generateAepTypescriptTypes") {
    group = "contracts"
    description = "Generates TypeScript types and a fetch client from the AEP OpenAPI spec."

    val outputDir = layout.buildDirectory.dir("generated/typescript")
    classpath = openApiGeneratorCli
    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")
    args(
        "generate",
        "-g", "typescript-fetch",
        "-i", specFile.absolutePath,
        "-o", outputDir.get().asFile.absolutePath,
        "--api-package", "com.ghatana.aep.api",
        "--model-package", "com.ghatana.aep.model",
        "--additional-properties",
        listOf(
            "npmName=@ghatana/aep-client",
            "npmVersion=${project.version}",
            "supportsES6=true",
            "typescriptThreePlus=true",
            "withInterfaces=true"
        ).joinToString(",")
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// TASK: Generate Java SDK for integration tests
//
// Generates a minimal Java client that test-scripts and integration tests
// can use to call the live AEP server. Not intended for production use.
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Generates a Java HTTP client SDK from the AEP spec for use in integration tests.
 *
 * @doc.type task
 * @doc.purpose Provide a typed Java client for AEP integration tests
 * @doc.layer product
 * @doc.pattern Code Generation
 */
tasks.register<JavaExec>("generateAepJavaSdk") {
    group = "contracts"
    description = "Generates a Java (okhttp-gson) client SDK from the AEP OpenAPI spec."

    val outputDir = layout.buildDirectory.dir("generated/java-sdk")
    classpath = openApiGeneratorCli
    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")
    args(
        "generate",
        "-g", "java",
        "-i", specFile.absolutePath,
        "-o", outputDir.get().asFile.absolutePath,
        "--api-package", "com.ghatana.aep.sdk.api",
        "--model-package", "com.ghatana.aep.sdk.model",
        "--invoker-package", "com.ghatana.aep.sdk",
        "--group-id", "com.ghatana",
        "--artifact-id", "aep-java-sdk",
        "--artifact-version", project.version.toString(),
        "--additional-properties",
        listOf(
            "library=okhttp-gson",
            "dateLibrary=java8",
            "openApiNullable=false",
            "useRxJava2=false",
            "useRxJava3=false"
        ).joinToString(",")
    )
}

// Wire validate into the standard build lifecycle so `./gradlew build` always
// validates the spec — failing fast on any API contract regression.
tasks.named("build") {
    dependsOn("validateAepSpec")
}
tasks.named("check") {
    dependsOn("validateAepSpec")
    dependsOn("checkAepOpenApiSync")
}

// ─────────────────────────────────────────────────────────────────────────────
// TASK: Verify that all three OpenAPI spec copies are byte-for-byte identical.
//
// Three locations hold the AEP OpenAPI spec:
//   1. products/aep/contracts/openapi.yaml       — canonical source (this module)
//   2. products/aep/server/src/main/resources/   — served at runtime by the Java HTTP layer
//   3. platform/contracts/openapi/aep.yaml       — shared platform-level registry
//
// This task computes SHA-256 digests of all three files and fails with a
// descriptive message if any pair diverges. It is wired into `check` so that
// drift is caught on every CI build.
//
// Update all three files in lockstep. Do not symlink — Gradle tracks each
// input independently for correct up-to-date checking.
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Verifies that all three AEP OpenAPI specification copies are byte-for-byte identical.
 *
 * @doc.type task
 * @doc.purpose Catch OpenAPI spec drift across the three canonical copies before it reaches CI
 * @doc.layer product
 * @doc.pattern Contract Validation
 */
abstract class CheckOpenApiSync : DefaultTask() {

    @get:InputFiles
    abstract val specFiles: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val digests = specFiles.files.associateWith { file ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var read = stream.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = stream.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

        val reference = digests.values.first()
        val drifted = digests.filter { (_, hash) -> hash != reference }

        if (drifted.isNotEmpty()) {
            val canonical = specFiles.files.first()
            val lines = buildString {
                appendLine()
                appendLine("╔══════════════════════════════════════════════════════════════════╗")
                appendLine("║  AEP OpenAPI spec drift detected — all three copies must match  ║")
                appendLine("╠══════════════════════════════════════════════════════════════════╣")
                digests.forEach { (file, hash) ->
                    val marker = if (drifted.containsKey(file)) "DIFFERS" else "OK     "
                    appendLine("║  [$marker]  ${file.relativeTo(project.rootDir)}  ║")
                    appendLine("║           sha256: $hash  ║")
                }
                appendLine("╠══════════════════════════════════════════════════════════════════╣")
                appendLine("║  Canonical copy: ${canonical.relativeTo(project.rootDir)}")
                appendLine("║  Update all three files in lockstep to resolve drift.")
                appendLine("╚══════════════════════════════════════════════════════════════════╝")
            }
            throw GradleException(lines)
        }

        logger.lifecycle("✓ AEP OpenAPI specs are in sync (${specFiles.files.size} copies, SHA-256: $reference)")
    }
}

tasks.register<CheckOpenApiSync>("checkAepOpenApiSync") {
    group = "contracts"
    description = "Verifies that all three AEP OpenAPI spec copies are byte-for-byte identical."
    specFiles.from(
        file("openapi.yaml"),
        rootProject.file("products/aep/server/src/main/resources/openapi.yaml"),
        rootProject.file("platform/contracts/openapi/aep.yaml")
    )
}
