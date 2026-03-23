/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

plugins {
    id("java-library")
}

group = "com.ghatana.products"
version = "1.0.0"
description = "PHR — Personal Health Records product module"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Kernel platform — provides KernelContext, KernelModule, KernelExtension,
    // DataCloudKernelAdapter, ClassificationDescriptor, etc.
    api(project(":platform:java:kernel"))

    // Platform libraries
    api(project(":platform:java:security"))
    api(project(":platform:java:database"))

    // ActiveJ
    api(libs.activej.promise)
    implementation(libs.activej.eventloop)

    // Observability
    implementation(libs.micrometer.core)

    // Testing
    testImplementation(project(":platform:java:kernel"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
