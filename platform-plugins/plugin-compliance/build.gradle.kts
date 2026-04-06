/**
 * Compliance Plugin
 *
 * @doc.type build-script
 * @doc.purpose Compliance plugin for cross-product regulatory compliance
 * @doc.layer platform
 */
plugins {
    `java-library`
}

group = "com.ghatana.plugin"
version = "1.0.0"
description = "Compliance Plugin - generic compliance rule engine"

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

    // Logging
    api("org.slf4j:slf4j-api:2.0.12")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
}

tasks.test {
    useJUnitPlatform()
}
