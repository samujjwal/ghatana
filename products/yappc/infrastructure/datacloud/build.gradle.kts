plugins {
    id("java-library")
}

group = "com.ghatana.products.yappc"
version = "1.0.0-SNAPSHOT"

dependencies {
    // Data-Cloud Core (merged: domain, application, infrastructure, api, spi)
    api(project(":products:data-cloud:platform"))
    
    // YAPPC domain models (for mapping)
    implementation(project(":products:yappc:libs:java:yappc-domain"))
    implementation(project(":products:yappc:core:domain"))
    
    // Platform libs
    implementation(project(":platform:java:core"))
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
    testImplementation(libs.junit.jupiter.api)
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
}

description = "YAPPC Infrastructure - Data-Cloud Integration"
