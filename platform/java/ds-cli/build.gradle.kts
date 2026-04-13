/*
 * Design System CLI - picocli-based tool for DTCG token operations,
 * validation, and governance auditing.
 */

plugins {
    id("java-application")
    id("jacoco")
}

description = "Design System CLI - Token build, validate, audit, and governance commands"

group = "com.ghatana.platform"
version = rootProject.version

application {
    mainClass.set("com.ghatana.platform.dscli.DesignSystemCLI")
}

dependencies {
    // CLI framework
    implementation(libs.picocli)
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    // JSON / YAML
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.datatype.jsr310)

    // Logging
    implementation(libs.slf4j.api)

    // Platform deps
    implementation(project(":platform:java:core"))

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly("ch.qos.logback:logback-classic:1.4.11")
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
    }
}
