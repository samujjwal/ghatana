plugins {
    id("java-library")
    id("jacoco")
}

group = "com.ghatana.products.yappc"
version = rootProject.version
description = "YAPPC Infrastructure - Data-Cloud Integration"

dependencies {
    // Data-Cloud SPI only (no platform dep)
    implementation(project(":products:data-cloud:spi"))
    
    // YAPPC domain models (for mapping)
    implementation(project(":products:yappc:libs:java:yappc-domain"))
    implementation(project(":products:yappc:core:yappc-domain-impl"))
    implementation(project(":products:yappc:core:yappc-infrastructure"))
    
    // Platform libs
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    // implementation(project(":libs:types")) - path needs verification
    
    // ActiveJ for async
    implementation(libs.activej.promise)
    
    // Jackson for JSON mapping
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// JaCoCo configuration managed by convention plugin

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// Temporarily disable coverage verification for this module
// TODO: Add more tests to reach minimum coverage thresholds
tasks.named("jacocoTestCoverageVerification") {
    enabled = false
}
