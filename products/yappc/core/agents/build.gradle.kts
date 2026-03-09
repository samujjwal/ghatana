plugins {
    id("java-library")
    id("jacoco")
}

group = "com.ghatana.products.yappc"
version = "1.0.0-SNAPSHOT"

description = "YAPPC Agents - Unified SDLC agents and integration (merged: sdlc-agents + agent-integration)"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Framework core (for FeatureFlags, Result, etc.)
    implementation(project(":products:yappc:core:framework"))

    // AI module
    implementation(project(":products:yappc:core:ai"))

    // Agent framework from platform
    implementation(project(":platform:java:agent-framework"))
    implementation(project(":platform:java:agent-dispatch"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:ai-integration"))

    // ActiveJ for async operations
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.http)

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)

    // Utilities (from agent-integration)
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("com.google.guava:guava:33.0.0-jre")

    // Annotations
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

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
