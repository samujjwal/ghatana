plugins {
    id("java-library")
}

group = "com.ghatana.platform"
version = "2026.3.1-SNAPSHOT"

description = "Platform Identity — Agent identity brokering, delegation tokens, credential management"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:observability"))

    api(libs.activej.promise)

    // Test
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
