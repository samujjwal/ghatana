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
    // Sub-modules (transitively provide all platform/framework deps)
    api(project(":products:yappc:core:agents:runtime"))
    api(project(":products:yappc:core:agents:workflow"))
    api(project(":products:yappc:core:agents:specialists"))

    // Direct platform deps used by learning/ and eval/ (not exposed by sub-modules as api)
    implementation(project(":products:yappc:backend:persistence"))
    implementation(project(":platform:java:agent-memory"))
    implementation(project(":platform:java:agent-dispatch"))

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
    toolVersion = "0.8.11"
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
                minimum = "0.35".toBigDecimal()
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
