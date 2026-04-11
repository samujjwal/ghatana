plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform.agent"
version = rootProject.version

description = "Agent Core - Unified agent contracts, SPI, registry, and coordination framework"

dependencies {
    // ActiveJ for async operations
    api(libs.bundles.activej.core)

    // Core Platform dependencies
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:agent-memory"))

    // NOTE: ai-integration and governance as implementation to avoid circular dependencies
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:governance"))

    // Jackson YAML for agent config materialization
    implementation(libs.bundles.jackson.json)
    implementation(libs.bundles.jackson.yaml)

    // LangChain4j for LLM integration (optional runtime dependency)
    implementation(libs.bundles.ai.integration)

    // Logging
    api(libs.bundles.logging.core)

    // Annotations
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Testing
    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform:java:testing"))
    // JMH Benchmarks
    testImplementation(libs.jmh.core)
    testAnnotationProcessor(libs.jmh.generator.annprocess)
}

tasks.test {
    useJUnitPlatform()
    
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
