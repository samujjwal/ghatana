/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

plugins {
    id("java-module")
}

group = "com.ghatana.products.phr"
version = rootProject.version
description = "PHR Healthcare Domain Module"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Platform Kernel
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform-kernel:kernel-plugin"))

    // Platform Plugins
    api(project(":platform-plugins:plugin-compliance"))
    api(project(":platform-plugins:plugin-consent"))
    api(project(":platform-plugins:plugin-audit-trail"))
    api(project(":platform-plugins:plugin-fraud-detection"))
    api(project(":platform-plugins:plugin-human-approval"))
    api(project(":platform-plugins:plugin-risk-management"))

    // Platform Libraries
    api(project(":platform:java:security"))
    api(project(":platform:java:governance"))
    api(project(":platform:java:database"))
    api(project(":platform:java:audit"))
    api(project(":platform:java:http"))

    // ActiveJ
    api(libs.bundles.activej.core)
    api(libs.bundles.activej.http)

    // Platform Core
    api(project(":platform:java:core"))

    // Observability
    implementation(libs.bundles.observability.core)

    // JSON processing
    implementation(libs.bundles.jackson.json)

    // Database
    implementation(libs.bundles.database.core)

    // Commons utilities
    implementation(libs.bundles.common.utils)

    // Logging
    implementation(libs.bundles.logging.core)

    // Testing
    testImplementation(project(":platform-kernel:kernel-testing"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.bundles.testing.core)
}
