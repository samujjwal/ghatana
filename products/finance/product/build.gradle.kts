/**
 * Finance Product Module
 *
 * Main product module for the Finance product. Contains the FinanceProductModule
 * which serves as the entry point for finance-specific business logic and workflows.
 * This module uses kernel capabilities for generic functionality and implements
 * finance-specific business rules.
 */
plugins {
    id("java-library")
}

group = "com.ghatana.products.finance"
version = "1.0.0"
description = "Finance Product Module - main entry point for finance business logic"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // ─── Finance Core ───────────────────────────────────────────────────────────
    api(project(":products:finance"))
    
    // ─── Kernel Platform ───────────────────────────────────────────────────────
    api(project(":platform:java:kernel"))
    
    // ─── Kernel Modules (Generic Capabilities) ───────────────────────────────────
    api(project(":platform:java:kernel:modules:authentication"))
    api(project(":platform:java:kernel:modules:config"))
    api(project(":platform:java:kernel:modules:event-store"))
    api(project(":platform:java:kernel:modules:audit"))
    api(project(":platform:java:kernel:modules:resilience"))
    
    // ─── Platform Libraries ─────────────────────────────────────────────────────
    api(project(":platform:java:security"))
    api(project(":platform:java:database"))
    api(project(":platform:java:http"))
    api(project(":platform:java:observability"))
    
    // ─── Finance Domains ────────────────────────────────────────────────────────
    api(project(":products:finance:domains:oms"))
    api(project(":products:finance:domains:ems"))
    api(project(":products:finance:domains:pms"))
    api(project(":products:finance:domains:risk"))
    api(project(":products:finance:domains:compliance"))
    api(project(":products:finance:domains:rules"))
    api(project(":products:finance:data-governance"))
    
    // ─── New Finance Domains (Migration Phase 2) ──────────────────────────────────
    api(project(":products:finance:domains:corporate-actions"))
    api(project(":products:finance:domains:market-data"))
    api(project(":products:finance:domains:post-trade"))
    api(project(":products:finance:domains:pricing"))
    api(project(":products:finance:domains:reconciliation"))
    api(project(":products:finance:domains:reference-data"))
    api(project(":products:finance:domains:regulatory-reporting"))
    api(project(":products:finance:domains:sanctions"))
    api(project(":products:finance:domains:surveillance"))
    
    // ─── ActiveJ (Mandatory) ─────────────────────────────────────────────────────
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)
    
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
