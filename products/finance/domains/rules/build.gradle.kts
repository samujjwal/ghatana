/**
 * Finance Rules Domain
 *
 * Finance-specific rules engine domain. Contains business rules for trade validation,
 * compliance checking, risk assessment, and other finance-specific rule processing.
 * This domain uses kernel capabilities for generic rule processing and implements
 * finance-specific business logic.
 */
plugins {
    id("com.ghatana.finance-domain-conventions")
    id("java-library")
}

group = "com.ghatana.products.finance"
version = "1.0.0"
description = "Finance Rules Domain - finance-specific business rules engine"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Kernel Platform ───────────────────────────────────────────────────────
    api(project(":platform-kernel:kernel-core"))
    
    // ─── Kernel Modules (Generic Capabilities) ───────────────────────────────────
    
    // ─── Platform Libraries ─────────────────────────────────────────────────────
    api(project(":platform:java:security"))
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))
    
    // ─── ActiveJ (Mandatory) ─────────────────────────────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)
    
    // ─── Rules Engine (Drools or similar) ───────────────────────────────────────────
    implementation(libs.drools.core)
    implementation(libs.drools.compiler)
    implementation(libs.drools.mvel)
    
    // ─── Serialization ────────────────────────────────────────────────────────
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    
    // ─── Observability ────────────────────────────────────────────────────────
    implementation(libs.micrometer.core)
    implementation(libs.slf4j.api)
    
    // ─── Testing ─────────────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// ─── JavaDoc Generation ─────────────────────────────────────────────────────
tasks.withType<Javadoc> {
    exclude("**/internal/**")
    options {
        encoding = "UTF-8"
        source = "21"
    }
}
