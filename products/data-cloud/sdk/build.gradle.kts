/**
 * Data-Cloud SDK Generation
 *
 * Generates client libraries for the Data-Cloud REST API from
 * {@code products/data-cloud/docs/openapi.yaml} using OpenAPI Generator 7.x.
 *
 * Three SDKs are produced:
 *  - Java (okhttp-gson)     → build/generated/java-sdk
 *  - TypeScript Fetch       → build/generated/typescript-sdk
 *  - Python (urllib3)       → build/generated/python-sdk
 *
 * Usage:
 *   ./gradlew :products:data-cloud:sdk:generateAllSdks
 *   ./gradlew :products:data-cloud:sdk:generateJavaSdk
 *   ./gradlew :products:data-cloud:sdk:generateTypescriptSdk
 *   ./gradlew :products:data-cloud:sdk:generatePythonSdk
 *
 * Outputs are generated into build/ and NOT committed to VCS — consumers
 * should run the generation task (or use the published Maven/npm artifact).
 */
plugins {
    base
    alias(libs.plugins.openapi.generator)
}

group = "com.ghatana.datacloud"
version = rootProject.version

// ─────────────────────────────────────────────────────────────────────────────
// Resolved path to the canonical OpenAPI specification
// ─────────────────────────────────────────────────────────────────────────────
val specFile = rootProject.file("products/data-cloud/docs/openapi.yaml")

// ─────────────────────────────────────────────────────────────────────────────
//  Java SDK — okhttp-gson, Java 8+ compatible
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Generates a Java (okhttp-gson) client SDK.
 *
 * @doc.type task
 * @doc.purpose Generate a Java HTTP client from the Data-Cloud OpenAPI spec
 * @doc.layer product
 * @doc.pattern Code Generation
 */
tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generateJavaSdk") {
    group = "sdk"
    description = "Generates a Java (okhttp-gson) client SDK from the Data-Cloud OpenAPI spec."

    generatorName.set("java")
    inputSpec.set(specFile.absolutePath)
    outputDir.set(layout.buildDirectory.dir("generated/java-sdk").get().asFile.absolutePath)

    // Package structure
    apiPackage.set("com.ghatana.datacloud.sdk.api")
    modelPackage.set("com.ghatana.datacloud.sdk.model")
    invokerPackage.set("com.ghatana.datacloud.sdk")

    // Maven artifact coordinates for the generated pom.xml
    groupId.set("com.ghatana")
    id.set("data-cloud-java-sdk")
    version.set(project.version.toString())

    configOptions.set(
        mapOf(
            // HTTP transport
            "library" to "okhttp-gson",
            // Date/time types
            "dateLibrary" to "java8",
            "serializationLibrary" to "gson",
            // Nullable — use native Optional<T> instead of OpenAPI nullable wrapper
            "openApiNullable" to "false",
            // GZip transport encoding
            "useGzipFeature" to "true",
            // Bean Validation annotations (JSR-380)
            "performBeanValidation" to "false",
            // Sealed model hierarchy for discriminated unions
            "disallowAdditionalPropertiesIfNotPresent" to "false",
            // Do not generate reactive variants
            "useRxJava2" to "false",
            "useRxJava3" to "false",
            // Generate toString / equals / hashCode
            "hideGenerationTimestamp" to "true",
        )
    )

    globalProperties.set(
        mapOf(
            "skipFormModel" to "true",
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  TypeScript SDK — typescript-fetch, ES2020, strict mode
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Generates a TypeScript Fetch client SDK.
 *
 * @doc.type task
 * @doc.purpose Generate a TypeScript HTTP client from the Data-Cloud OpenAPI spec
 * @doc.layer product
 * @doc.pattern Code Generation
 */
tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generateTypescriptSdk") {
    group = "sdk"
    description = "Generates a TypeScript-Fetch client SDK from the Data-Cloud OpenAPI spec."

    generatorName.set("typescript-fetch")
    inputSpec.set(specFile.absolutePath)
    outputDir.set(layout.buildDirectory.dir("generated/typescript-sdk").get().asFile.absolutePath)

    configOptions.set(
        mapOf(
            // npm package identity
            "npmName" to "@ghatana/data-cloud-client",
            "npmVersion" to "1.0.0",
            // Modern TypeScript target
            "typescriptThreePlus" to "true",
            "supportsES6" to "true",
            // Enum casing
            "enumPropertyNaming" to "UPPERCASE",
            "stringEnums" to "true",
            // API method signatures
            "useSingleRequestParameter" to "true",
            // Generate Interface types alongside concrete implementations
            "withInterfaces" to "true",
            "prefixParameterInterfaces" to "false",
            // No timestamps in generated files (deterministic builds)
            "hideGenerationTimestamp" to "true",
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Python SDK — python (urllib3), for ML/data-science integrations
// ─────────────────────────────────────────────────────────────────────────────
/**
 * Generates a Python client SDK.
 *
 * @doc.type task
 * @doc.purpose Generate a Python HTTP client from the Data-Cloud OpenAPI spec
 * @doc.layer product
 * @doc.pattern Code Generation
 */
tasks.register<org.openapitools.generator.gradle.plugin.tasks.GenerateTask>("generatePythonSdk") {
    group = "sdk"
    description = "Generates a Python (urllib3) client SDK from the Data-Cloud OpenAPI spec."

    generatorName.set("python")
    inputSpec.set(specFile.absolutePath)
    outputDir.set(layout.buildDirectory.dir("generated/python-sdk").get().asFile.absolutePath)

    configOptions.set(
        mapOf(
            "packageName" to "data_cloud_client",
            "projectName" to "data-cloud-client",
            "packageVersion" to "1.0.0",
            "generateSourceCodeOnly" to "false",
            "useOneOfDiscriminatorLookup" to "false",
            "hideGenerationTimestamp" to "true",
        )
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  Convenience aggregate task
// ─────────────────────────────────────────────────────────────────────────────
tasks.register("generateAllSdks") {
    group = "sdk"
    description = "Generates all SDK client libraries (Java, TypeScript, Python)."
    dependsOn("generateJavaSdk", "generateTypescriptSdk", "generatePythonSdk")
}

// Wire into the standard build lifecycle
tasks.named("build") {
    dependsOn("generateAllSdks")
}

// ─────────────────────────────────────────────────────────────────────────────
//  Validation — fail fast when the spec file is missing rather than producing
//  a misleading "generator not found" error
// ─────────────────────────────────────────────────────────────────────────────
tasks.configureEach {
    if (name.startsWith("generate") && name.endsWith("Sdk")) {
        doFirst {
            require(specFile.exists()) {
                "OpenAPI spec not found at ${specFile.absolutePath}. " +
                    "Run the build from the repository root."
            }
        }
    }
}
