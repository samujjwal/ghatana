/**
 * Platform Audit Module
 * 
 * Provides audit logging and event tracking capabilities
 */
plugins {
    id("java-library")
}

group = "com.ghatana.platform"
description = "Platform Audit - Audit logging and tracking"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

sourceSets {
    main {
        java {
            // DataCloudAuditService moved to products/data-cloud/platform
            // This module now contains only platform-neutral audit abstractions
        }
    }
}

dependencies {
    // Platform dependencies
    api(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:event-cloud"))
    implementation(project(":platform:java:observability"))
    
    // ActiveJ (async framework) — api because audit interfaces expose Promise in public API
    api(libs.activej.promise)
    implementation(libs.activej.inject)
    
    // JPA (compileOnly — callers supply the EntityManager at runtime via platform:java:database)
    compileOnly(libs.jakarta.persistence.api)

    // Logging
    implementation(libs.slf4j.api)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}
