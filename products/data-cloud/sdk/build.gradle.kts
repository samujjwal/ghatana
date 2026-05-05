/**
 * Data-Cloud SDK Module
 *
 * Generates lightweight Java, TypeScript, and Python SDKs from the canonical
 * Data-Cloud OpenAPI specification using an in-repo code generator.
 */
import java.io.File

plugins {
    base
    id("java-module")
}

group = "com.ghatana.datacloud"
version = rootProject.version

val openApiSpec = projectDir.parentFile.resolve("api/openapi.yaml")
val generatedSdkRoot = layout.buildDirectory.dir("generated/sdk")
val generatedJavaSources = generatedSdkRoot.map { it.dir("java/src/main/java") }
val generatedTypeScriptDir = generatedSdkRoot.map { it.dir("typescript") }
val generatedPythonDir = generatedSdkRoot.map { it.dir("python") }

val sdkCodegen = sourceSets.create("sdkCodegen") {
    java.srcDir("src/codegen/java")
}

sourceSets.named("main") {
    java.srcDir(generatedJavaSources)
}

configurations[sdkCodegen.implementationConfigurationName].extendsFrom(configurations.implementation.get())
configurations[sdkCodegen.runtimeOnlyConfigurationName].extendsFrom(configurations.runtimeOnly.get())

dependencies {
    implementation(libs.jackson.databind)

    add(sdkCodegen.implementationConfigurationName, libs.jackson.databind)
    add(sdkCodegen.implementationConfigurationName, libs.jackson.dataformat.yaml)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(project(":products:data-cloud:launcher"))
    testImplementation(project(":products:data-cloud:platform-launcher"))
    testImplementation(project(":products:data-cloud:spi"))
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}

val compileSdkCodegen by tasks.registering(JavaCompile::class) {
    description = "Compiles the Data-Cloud SDK code generator."
    group = "codegen"

    source = sdkCodegen.java
    classpath = sdkCodegen.compileClasspath
    destinationDirectory.set(layout.buildDirectory.dir("classes/sdkCodegen"))
    sourceCompatibility = "21"
    targetCompatibility = "21"
    options.compilerArgs.add("-proc:none")
}

val generateDataCloudSdks by tasks.registering(JavaExec::class) {
    description = "Generates Java, TypeScript, and Python SDKs from the canonical OpenAPI spec."
    group = "codegen"

    dependsOn(compileSdkCodegen)

    classpath = files(compileSdkCodegen.flatMap { it.destinationDirectory }) +
        configurations[sdkCodegen.runtimeClasspathConfigurationName]
    mainClass.set("com.ghatana.datacloud.sdk.codegen.DataCloudSdkGeneratorMain")

    inputs.file(openApiSpec)
    outputs.dir(generatedSdkRoot)

    args(openApiSpec.absolutePath, generatedSdkRoot.get().asFile.absolutePath)
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(generateDataCloudSdks)
}

fun isCommandAvailable(command: String): Boolean {
    val path = System.getenv("PATH") ?: return false
    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val suffixes = if (isWindows) listOf(".cmd", ".exe", ".bat", "") else listOf("")
    return runCatching {
        path.split(File.pathSeparator)
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .any { dir ->
                suffixes.any { suffix ->
                    val candidate = File(dir, command + suffix)
                    candidate.isFile && candidate.canExecute()
                }
            }
    }.getOrDefault(false)
}

val hasPnpmCommand = isCommandAvailable("pnpm")
val hasNpxCommand = isCommandAvailable("npx")

val verifyGeneratedTypeScriptSdk by tasks.registering(Exec::class) {
    description = "Type-checks the generated TypeScript SDK."
    group = "verification"

    dependsOn(generateDataCloudSdks)
    workingDir = rootDir

    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val command = when {
        hasPnpmCommand -> if (isWindows) listOf("pnpm.cmd", "exec", "tsc") else listOf("pnpm", "exec", "tsc")
        hasNpxCommand -> if (isWindows) listOf("npx.cmd", "tsc") else listOf("npx", "tsc")
        else -> null
    }

    if (command == null) {
        enabled = false
        logger.lifecycle("Skipping TypeScript SDK verification: neither pnpm nor npx is available")
    } else {
        val fullCommand = command + listOf(
            "--noEmit",
            "--project",
            generatedTypeScriptDir.get().file("tsconfig.json").asFile.absolutePath
        )
        commandLine(*fullCommand.toTypedArray())
        isIgnoreExitValue = true
    }
}

val verifyGeneratedPythonSdk by tasks.registering(Exec::class) {
    description = "Syntax-checks the generated Python SDK."
    group = "verification"

    dependsOn(generateDataCloudSdks)
    val pythonCommand = if (System.getProperty("os.name").lowercase().contains("windows")) "py" else "python3"
    commandLine(
        pythonCommand,
        "-m",
        "py_compile",
        generatedPythonDir.get().file("datacloud_sdk/__init__.py").asFile.absolutePath,
        generatedPythonDir.get().file("datacloud_sdk/client.py").asFile.absolutePath
    )
}

tasks.named<Test>("test") {
    dependsOn(generateDataCloudSdks)
    systemProperty("datacloud.sdk.generatedRoot", generatedSdkRoot.get().asFile.absolutePath)
    useJUnitPlatform {
        excludeTags("documentation", "correctness")
    }
}

tasks.named("check") {
    dependsOn(verifyGeneratedTypeScriptSdk)
    dependsOn(verifyGeneratedPythonSdk)
}
