plugins {
    id("java-module")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud Platform Governance — PII masking, field redaction, audit logging, and retention classification"

dependencies {
    api(project(":platform:java:core"))
    implementation(project(":products:data-cloud:planes:data:entity"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

// Boundary rule: Governance plane must not depend on Action internals
abstract class ValidateBoundaryRules : DefaultTask() {
    @get:Input
    abstract val forbiddenDependencies: ListProperty<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val compileClasspath: ConfigurableFileCollection

    @TaskAction
    fun validate() {
        val violations = compileClasspath
            .map { it.name }
            .filter { depName -> forbiddenDependencies.get().any { depName.contains(it) } }

        if (violations.isNotEmpty()) {
            throw GradleException(
                "Governance plane boundary violation: Governance plane must not depend on Action internals. " +
                "Found forbidden dependencies: ${violations.joinToString()}. " +
                "Action Plane semantics should be accessed through shared SPI contracts only."
            )
        }
    }
}

tasks.register<ValidateBoundaryRules>("validateBoundaryRules") {
    group = "verification"
    description = "Validates that Governance plane does not depend on Action internals"
    
    forbiddenDependencies.set(
        listOf(
            ":products:data-cloud:planes:action:engine",
            ":products:data-cloud:planes:action:orchestrator",
            ":products:data-cloud:planes:action:agent-runtime",
            ":products:data-cloud:planes:action:central-runtime",
            ":products:data-cloud:planes:action:operator-contracts"
        )
    )
    
    compileClasspath.from(configurations.named("compileClasspath"))
}

tasks.named("check") {
    dependsOn("validateBoundaryRules")
}
