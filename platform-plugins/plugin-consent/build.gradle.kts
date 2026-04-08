/**
 * Consent Plugin
 *
 * @doc.type build-script
 * @doc.purpose Consent management plugin for cross-product use
 * @doc.layer platform
 */
plugins {
    `java-library`
    jacoco
}

group = "com.ghatana.plugin"
version = "1.0.0"
description = "Consent Plugin - universal consent management framework"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Kernel and Platform libraries via BOMs
    implementation(platform(project(":platform-kernel:kernel-bom")))
    implementation(platform(project(":platform:java:platform-bom")))

    // Kernel modules
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform-kernel:kernel-plugin"))

    // Plugin-specific dependencies
    api(libs.activej.promise)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// JaCoCo configuration for test coverage measurement
jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
