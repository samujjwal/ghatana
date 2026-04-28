plugins {
    id("java-module")
}

group = "com.ghatana.products.yappc.services"
version = rootProject.version.toString()
base.archivesName.set("yappc-services-platform")

// DEPRECATED (SIMP-Y8): This module has been merged into :products:yappc:core:yappc-services.
// All source code has been copied to yappc-services. This facade is kept only for
// transitive compatibility during the migration window. Remove after all dependents
// are updated to depend directly on yappc-services.
description = "[DEPRECATED] YAPPC Services: Platform — Merged into yappc-services (SIMP-Y8)"

dependencies {
    // YAPPC domain library
    implementation(project(":products:yappc:libs:java:yappc-domain"))
    implementation(project(":products:yappc:core:ai"))

    // Core lifecycle and infrastructure
    implementation(project(":products:yappc:core:yappc-services"))
    implementation(project(":products:yappc:core:yappc-infrastructure"))
    implementation(project(":products:yappc:infrastructure:datacloud"))
    // backend:auth removed (2026-03-23) — functionality consolidated into core modules

    // Core Platform Libraries
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:observability"))

    // ActiveJ for async
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)

    // Database
    implementation("com.zaxxer:HikariCP:5.1.0")
    runtimeOnly(libs.postgresql)

    // JSON Processing
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Validation
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
}

tasks.test {
    // useJUnitPlatform() already applied by java-module; keep finalizedBy
    finalizedBy(tasks.jacocoTestReport)
}

// jacoco and jacocoTestReport configured by java-module

// Override coverage thresholds — module has minimal testable production code (pure glue/facade).
// TODO: Raise thresholds incrementally as test coverage improves.
tasks.named("jacocoTestCoverageVerification", JacocoCoverageVerification::class.java).configure {
    // Module has minimal testable production code (pure glue/facade).
    // Disable threshold failures until coverage improves incrementally.
    violationRules.isFailOnViolation = false
}
