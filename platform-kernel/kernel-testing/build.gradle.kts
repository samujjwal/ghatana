/**
 * Platform Kernel Testing Module
 *
 * @doc.type build-script
 * @doc.purpose Test infrastructure for kernel modules
 * @doc.layer platform
 */
plugins {
    `java-library`
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Platform Kernel Testing - test utilities and base classes"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Kernel Core
    api(project(":kernel-core"))

    // ActiveJ test support
    api("io.activej:activej-promise:6.0-rc2")
    api("io.activej:activej-eventloop:6.0-rc2")

    // JUnit
    api("org.junit.jupiter:junit-jupiter:5.10.2")
    api("org.junit.jupiter:junit-jupiter-api:5.10.2")
    api("org.junit.jupiter:junit-jupiter-engine:5.10.2")

    // Assertions
    api("org.assertj:assertj-core:3.25.3")

    // Mocking
    api("org.mockito:mockito-core:5.11.0")
    api("org.mockito:mockito-junit-jupiter:5.11.0")

    // Logging
    api("org.slf4j:slf4j-api")

    // Test runtime
    runtimeOnly("org.junit.platform:junit-platform-launcher")
}
