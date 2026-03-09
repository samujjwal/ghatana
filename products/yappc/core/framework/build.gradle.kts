import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.api.tasks.javadoc.Javadoc

plugins {
    id("java-library")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

description = "YAPPC Framework - Unified framework (merged: framework-api + framework-core)"

dependencies {
    // Import Jackson BOM for consistent versions
    implementation(platform(libs.jackson.bom))

    // Core framework APIs (from framework-api)
    api("com.fasterxml.jackson.core:jackson-databind")
    api("com.fasterxml.jackson.core:jackson-annotations")
    api(libs.slf4j.api)
    api(libs.jakarta.annotation.api)
    api(libs.activej.promise)
    api(libs.activej.inject)
    api(project(":platform:java:plugin"))
    compileOnly("org.jetbrains:annotations:24.0.1")

    // Core framework impl (from framework-core)
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:runtime"))
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation(libs.activej.common)
    implementation(libs.activej.config)
    implementation(libs.guava)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Test dependencies (merged)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
}

tasks.matching { it.name == "jacocoTestCoverageVerification" }.configureEach {
    enabled = true
}

tasks.withType<Javadoc>().configureEach {
    val opts = options as StandardJavadocDocletOptions
    opts.encoding = "UTF-8"
    opts.addStringOption("Xdoclint:none", "-quiet")
    listOf("doc.type", "doc.purpose", "doc.layer", "doc.pattern").forEach { tag ->
        opts.addStringOption("tag", "$tag:a:$tag")
    }
}
