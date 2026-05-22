plugins {
    base
}

group = "com.ghatana.datacloud"
version = rootProject.version

val openApiGeneratorCli by configurations.creating

dependencies {
    openApiGeneratorCli("org.openapitools:openapi-generator-cli:${libs.versions.openapi.generator.get()}")
}

val dataCloudSpec = layout.projectDirectory.file("openapi/data-cloud.yaml")
val aepSpec = layout.projectDirectory.file("openapi/aep.yaml")
val actionPlaneSpec = layout.projectDirectory.file("openapi/action-plane.yaml")
val platformAepSpec = rootProject.layout.projectDirectory.file("platform/contracts/openapi/aep.yaml")
val aepRuntimeSpec = rootProject.layout.projectDirectory.file("products/data-cloud/planes/action/server/src/main/resources/openapi.yaml")

fun registerOpenApiValidation(taskName: String, specFile: RegularFile) {
    tasks.register<JavaExec>(taskName) {
        group = "contracts"
        description = "Validates ${specFile.asFile.relativeTo(rootDir)} as an OpenAPI document."
        classpath = openApiGeneratorCli
        mainClass.set("org.openapitools.codegen.OpenAPIGenerator")
        args("validate", "-i", specFile.asFile.toURI().toString(), "--recommend")
        inputs.file(specFile)
    }
}

registerOpenApiValidation("validateDataCloudSpec", dataCloudSpec)
registerOpenApiValidation("validateAepSpec", aepSpec)
registerOpenApiValidation("validateActionPlaneSpec", actionPlaneSpec)

tasks.register<JavaExec>("generateAepTypescriptTypes") {
    group = "contracts"
    description = "Generates TypeScript types and a fetch client from the AEP OpenAPI spec."

    val outputDir = layout.buildDirectory.dir("generated/aep/typescript")
    classpath = openApiGeneratorCli
    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")
    args(
        "generate",
        "-g", "typescript-fetch",
        "-i", aepSpec.asFile.toURI().toString(),
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
    inputs.file(aepSpec)
    outputs.dir(outputDir)
}

tasks.register<JavaExec>("generateAepJavaSdk") {
    group = "contracts"
    description = "Generates a Java client SDK from the AEP OpenAPI spec for integration tests."

    val outputDir = layout.buildDirectory.dir("generated/aep/java-sdk")
    classpath = openApiGeneratorCli
    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")
    args(
        "generate",
        "-g", "java",
        "-i", aepSpec.asFile.toURI().toString(),
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
    inputs.file(aepSpec)
    outputs.dir(outputDir)
}

tasks.register<JavaExec>("generateDataCloudTypescriptSdk") {
    group = "contracts"
    description = "Generates a TypeScript fetch client from the canonical Data Cloud OpenAPI spec (data-cloud.yaml)."

    val outputDir = layout.buildDirectory.dir("generated/data-cloud/typescript")
    classpath = openApiGeneratorCli
    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")
    args(
        "generate",
        "-g", "typescript-fetch",
        "-i", dataCloudSpec.asFile.toURI().toString(),
        "-o", outputDir.get().asFile.absolutePath,
        "--api-package", "com.ghatana.datacloud.api",
        "--model-package", "com.ghatana.datacloud.model",
        "--additional-properties",
        listOf(
            "npmName=@ghatana/data-cloud-client",
            "npmVersion=${project.version}",
            "supportsES6=true",
            "typescriptThreePlus=true",
            "withInterfaces=true"
        ).joinToString(",")
    )
    inputs.file(dataCloudSpec)
    outputs.dir(outputDir)
}

tasks.register<JavaExec>("generateDataCloudJavaSdk") {
    group = "contracts"
    description = "Generates a Java client SDK from the canonical Data Cloud OpenAPI spec (data-cloud.yaml) for integration tests."

    val outputDir = layout.buildDirectory.dir("generated/data-cloud/java-sdk")
    classpath = openApiGeneratorCli
    mainClass.set("org.openapitools.codegen.OpenAPIGenerator")
    args(
        "generate",
        "-g", "java",
        "-i", dataCloudSpec.asFile.toURI().toString(),
        "-o", outputDir.get().asFile.absolutePath,
        "--api-package", "com.ghatana.datacloud.sdk.api",
        "--model-package", "com.ghatana.datacloud.sdk.model",
        "--invoker-package", "com.ghatana.datacloud.sdk",
        "--group-id", "com.ghatana",
        "--artifact-id", "data-cloud-java-sdk",
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
    inputs.file(dataCloudSpec)
    outputs.dir(outputDir)
}

abstract class CheckProductOpenApiSync : DefaultTask() {
    @get:InputFiles
    abstract val specFiles: ConfigurableFileCollection

    @TaskAction
    fun check() {
        val existingFiles = specFiles.files.filter { it.exists() }
        if (existingFiles.size != specFiles.files.size) {
            val missing = specFiles.files.filterNot { it.exists() }
                .joinToString(System.lineSeparator()) { " - ${it.relativeTo(project.rootDir)}" }
            throw GradleException("Missing OpenAPI sync file(s):${System.lineSeparator()}$missing")
        }

        val normalized = existingFiles.associateWith { file ->
            file.readLines()
                .dropWhile { it.startsWith("#") }
                .joinToString("\n")
                .trim()
        }
        val reference = normalized.values.first()
        val drifted = normalized.filterValues { it != reference }

        if (drifted.isNotEmpty()) {
            val report = buildString {
                appendLine("AEP OpenAPI drift detected. These files must remain equivalent:")
                normalized.keys.forEach { file ->
                    val marker = if (drifted.containsKey(file)) "DIFFERS" else "OK"
                    appendLine(" - [$marker] ${file.relativeTo(project.rootDir)}")
                }
            }
            throw GradleException(report)
        }
    }
}

tasks.register<CheckProductOpenApiSync>("checkProductOpenApiSync") {
    group = "contracts"
    description = "Verifies Action Plane/AEP product, runtime, and platform OpenAPI copies are equivalent."
    dependsOn(":products:data-cloud:planes:action:server:syncOpenApiSpec")
    // Action plane has its own contract and is validated separately; only AEP copies must match.
    specFiles.from(aepSpec, aepRuntimeSpec, platformAepSpec)
}

tasks.named("check") {
    dependsOn("validateDataCloudSpec")
    dependsOn("validateAepSpec")
    dependsOn("validateActionPlaneSpec")
    dependsOn("checkProductOpenApiSync")
}

tasks.named("build") {
    dependsOn("check")
}
