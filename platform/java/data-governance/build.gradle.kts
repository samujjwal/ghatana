plugins {
    id("java-library")
}

group = "com.ghatana.platform"
version = rootProject.version

description = "Platform Data Governance — Consent, PII classification, purpose-limitation, data minimization"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:governance"))

    api(libs.activej.promise)

    implementation(libs.jackson.databind)

    // Test
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
