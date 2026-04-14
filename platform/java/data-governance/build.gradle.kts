plugins {
    id("java-module")
}

group = "com.ghatana.platform"
version = rootProject.version

description = "Platform Data Governance — Consent, PII classification, purpose-limitation, data minimization"


dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:governance"))

    api(libs.activej.promise)

    implementation(libs.jackson.databind)
    implementation(project(":platform:java:database"))
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    // Test
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.testing.containers)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
