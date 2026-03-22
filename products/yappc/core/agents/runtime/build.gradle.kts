plugins {
    id("java-library")
    id("jacoco")
}

group = "com.ghatana.products.yappc"
version = rootProject.version

description = "YAPPC Agent Runtime — base types, contracts, dispatch, coordination, tools and prompt templates"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Framework core (api — FeatureFlags/FeatureFlag used in YAPPCAgentBase public API)
    api(project(":products:yappc:core:framework"))

    // Domain types and repository ports
    implementation(project(":products:yappc:backend:persistence"))

    // Agent framework from platform (api — types are part of YAPPCAgentBase public API)
    api(project(":platform:java:agent-core"))
    implementation(project(":platform:java:agent-dispatch"))
    implementation(project(":platform:java:agent-registry"))
    implementation(project(":platform:java:agent-memory"))
    implementation(project(":platform:java:agent-learning"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:event-cloud"))

    // AEP central runtime — for YappcAepIntegration bridge
    implementation(project(":products:aep:platform-bundle"))

    // AI module (api — LLMProvider/LLMRequest/LLMResponse in public agent API)
    api(project(":products:yappc:core:ai"))

    // ActiveJ for async operations
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.http)

    // Jackson
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

jacoco { toolVersion = "0.8.11" }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:unchecked", "-Xlint:deprecation"))
}
