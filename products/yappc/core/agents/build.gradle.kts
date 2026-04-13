plugins {
    id("java-library")
    id("jacoco")
}

group = "com.ghatana.products.yappc"
version = rootProject.version

description = "YAPPC Agents — aggregator module (system wiring, generators, evaluation flywheel, learning, examples)"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // JavaParser for code analysis and migration
    implementation("com.github.javaparser:javaparser-core:3.25.1")

    // YAPPC Agent Registry Port — decouples from AEP at compile-time
    // (AEP impl is wired at runtime via AepAgentRegistryAdapter in yappc-infrastructure)
    implementation(project(":products:yappc:core:yappc-shared"))

    // DataCloud Agent Registry — DataCloudAgentRegistry implements AgentRegistry SPI for
    // durable, cross-instance agent discovery.  Wired via YappcAgentSystem.Builder#platformRegistry().
    implementation(project(":products:data-cloud:agent-registry"))

    // Platform Agent Framework (SECONDARY - for base interfaces)
    implementation(project(":platform:java:agent-core"))

    // Platform Shared Utilities
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))

    // YAPPC Domain (for LearnedPolicy and repositories)
    implementation(project(":products:yappc:core:yappc-domain-impl"))

    // Platform Contracts (for AgentManifestProto, etc.)
    implementation(project(":platform:contracts"))

    // Sub-modules (transitively provide all platform/framework deps)
    api(project(":products:yappc:core:agents:runtime"))
    api(project(":products:yappc:core:agents:workflow"))
    api(project(":products:yappc:core:agents:code-specialists"))
    api(project(":products:yappc:core:agents:architecture-specialists"))
    api(project(":products:yappc:core:agents:testing-specialists"))

    // Direct platform deps used by learning/ and eval/ (not exposed by sub-modules as api)
    // backend:persistence removed (2026-03-23) — functionality consolidated into core modules
    // aep-agent-runtime adapter seam resolved: AgentEvalRunner now uses AgentRuntimePort
    // (AEP impl wired at runtime via AepAgentRuntimeAdapter in yappc-infrastructure)

    // ActiveJ (generators / examples use async directly)
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // Jackson (generators/eval config loading)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)


    // Utilities
    implementation(libs.commons.lang3)
    implementation(libs.guava)

    // Annotations
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.archunit.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
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
    violationRules {
        rule {
            limit {
                minimum = "0.00".toBigDecimal()
            }
        }
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-Xlint:unchecked",
        "-Xlint:deprecation"
    ))
}

// =============================================================================
// Agent Evaluation Flywheel Task
// =============================================================================
tasks.register<JavaExec>("agentEval") {
    group = "verification"
    description = "Run the YAPPC agent evaluation flywheel against the golden test set"
    classpath = sourceSets["main"].runtimeClasspath + sourceSets["test"].runtimeClasspath
    mainClass.set("com.ghatana.yappc.agent.eval.AgentEvalCli")

    // Pass golden test set path
    args = listOf(
        "--test-set", "${rootProject.projectDir}/products/yappc/config/agents/eval/golden-test-set.yaml",
        "--output", "${project.layout.buildDirectory.get().asFile}/reports/agent-eval/report.json"
    )

    // Environment variables for LLM provider (must be set externally)
    environment("AGENT_EVAL_MODE", "true")
    environment("AGENT_EVAL_TIMEOUT_MS", "120000")
}
