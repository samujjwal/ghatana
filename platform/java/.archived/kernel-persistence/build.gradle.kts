/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

plugins {
    id("java-library")
}

group = "com.ghatana.platform"
description = "Kernel Persistence - PostgreSQL/JDBC/Redis-backed adapters for kernel services"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    api(project(":platform:java:kernel"))
    api(project(":platform:java:distributed-cache"))

    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.h2)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
