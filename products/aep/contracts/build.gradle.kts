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
    alias(libs.plugins.openapi.generator)
}

group = "com.ghatana.aep"
version = rootProject.version

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
tasks.register<org.openapitools.generator.gradle.plugin.tasks.ValidateTask>("validateAepSpec") {
    group = "contracts"
    description = "Validates the AEP OpenAPI 3.0 specification against the OpenAPI schema."

    inputSpec.set(specFile.absolutePath)
    recommend.set(true)
}

// ─────────────────────────────────────────────────────────────────────────────
// TASK: Generate TypeScript types for the AEP BFF (api/ module)
//
// Generates TypeScript interfaces + fetch-based client stubs from the spec.
// Output lands in build/generated/typescript/ and can be copied/linked into
// the api/ Node.js module for type-safe proxy routes.
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Generates TypeScript type definitions and a fetch client from the AEP spec.
 *
 * @doc.type task
 * @doc.purpose Generate TypeScript types for the AEP BFF and UI
 * @doc.layer product
 * @doc.pattern Code Generation
 */
tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generateAepTypescriptTypes") {
    group = "contracts"
    description = "Generates TypeScript types and a fetch client from the AEP OpenAPI spec."

    generatorName.set("typescript-fetch")
    inputSpec.set(specFile.absolutePath)
    outputDir.set(layout.buildDirectory.dir("generated/typescript").get().asFile.absolutePath)

    apiPackage.set("com.ghatana.aep.api")
    modelPackage.set("com.ghatana.aep.model")

    configOptions.set(
        mapOf(
            "npmName" to "@ghatana/aep-client",
            "npmVersion" to project.version.toString(),
            "supportsES6" to "true",
            "typescriptThreePlus" to "true",
            "withInterfaces" to "true",
        )
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
tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generateAepJavaSdk") {
    group = "contracts"
    description = "Generates a Java (okhttp-gson) client SDK from the AEP OpenAPI spec."

    generatorName.set("java")
    inputSpec.set(specFile.absolutePath)
    outputDir.set(layout.buildDirectory.dir("generated/java-sdk").get().asFile.absolutePath)

    apiPackage.set("com.ghatana.aep.sdk.api")
    modelPackage.set("com.ghatana.aep.sdk.model")
    invokerPackage.set("com.ghatana.aep.sdk")

    groupId.set("com.ghatana")
    id.set("aep-java-sdk")
    version.set(project.version.toString())

    configOptions.set(
        mapOf(
            "library" to "okhttp-gson",
            "dateLibrary" to "java8",
            "openApiNullable" to "false",
            "useRxJava2" to "false",
            "useRxJava3" to "false",
        )
    )
}

// Wire validate into the standard build lifecycle so `./gradlew build` always
// validates the spec — failing fast on any API contract regression.
tasks.named("build") {
    dependsOn("validateAepSpec")
}
tasks.named("check") {
    dependsOn("validateAepSpec")
}
