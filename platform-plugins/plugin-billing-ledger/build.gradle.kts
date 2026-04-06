/**
 * Billing Ledger Plugin
 *
 * @doc.type build-script
 * @doc.purpose Double-entry ledger plugin for cross-product billing
 * @doc.layer platform
 */
plugins {
    `java-library`
    jacoco
}

group = "com.ghatana.plugin"
version = "1.0.0"
description = "Billing Ledger Plugin - double-entry ledger system"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Kernel
    api("com.ghatana.kernel:kernel-core")
    api("com.ghatana.kernel:kernel-plugin")

    // ActiveJ
    api("io.activej:activej-promise:6.0-rc2")

    // Jackson
    api("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// JaCoCo configuration for test coverage measurement
jacoco {
    toolVersion = "0.8.13"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
