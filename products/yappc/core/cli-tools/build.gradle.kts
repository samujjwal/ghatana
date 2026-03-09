/**
 * cli-tools: Knowledge Graph CLI Tools
 * 
 * This module provides command-line tools for managing the Knowledge Graph.
 * 
 * Architecture: Hexagonal - Adapter layer (CLI)
 * Dependencies: knowledge-graph (domain), platform:java:core (JSON utils)
 */

plugins {
    id("java-library")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    // Domain models (using consolidated knowledge-graph module)
    api(project(":products:yappc:core:knowledge-graph"))
    
    // Platform core (JsonUtils)
    implementation(project(":platform:java:core"))
    
    // CLI framework
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")
    
    // JSON processing (via version catalog)
    implementation(libs.jackson.databind)
    
    // Logging
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)
    
    // Testing
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

description = "Knowledge Graph - CLI Tools"
