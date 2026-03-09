plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform.agent"
version = "1.0.0-SNAPSHOT"

description = "Agent Framework - Core abstractions and coordination for AI agents"

dependencies {
    // ActiveJ for async operations
    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(libs.activej.common)
    
    // Core Platform dependencies
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:ai-integration"))
    api(project(":platform:java:governance"))
    
    // Jackson YAML for agent config materialization
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    
    // LangChain4j for LLM integration (optional runtime dependency)
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j)
    
    // Logging
    api(libs.slf4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j.impl)
    
    // Annotations
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    // Testing
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
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
