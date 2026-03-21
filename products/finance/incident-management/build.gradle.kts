/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

plugins {
    id("java-library")
}

dependencies {
    // Platform core dependencies
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:audit"))

    // Kernel modules
    implementation(project(":platform:java:kernel:modules:authentication"))
    implementation(project(":platform:java:kernel:modules:config"))
    implementation(project(":platform:java:kernel:modules:event-store"))
    implementation(project(":platform:java:kernel:modules:audit"))

    // ActiveJ Promise
    implementation(libs.activej.promise)

    // Jackson for JSON
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Micrometer for metrics
    implementation(libs.micrometer.core)

    // PostgreSQL
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
