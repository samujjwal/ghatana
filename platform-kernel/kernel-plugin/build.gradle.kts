/**
 * Platform Kernel Plugin Module
 *
 * @doc.type build-script
 * @doc.purpose Plugin framework - lifecycle, SPI, and management
 * @doc.layer platform
 */
plugins {
    `java-library`
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Platform Kernel Plugin - plugin framework and lifecycle management"

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

    // ActiveJ for async operations
    api("io.activej:activej-promise:6.0-rc2")
    api("io.activej:activej-common:6.0-rc2")

    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")

    // Annotations
    compileOnly("org.jetbrains:annotations:24.1.0")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
    testCompileOnly("org.projectlombok:lombok:1.18.32")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.32")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("org.mockito:mockito-core:5.11.0")
}

tasks.test {
    useJUnitPlatform()
}
