/**
 * Platform Kernel Core Module
 *
 * @doc.type build-script
 * @doc.purpose Kernel platform core - module lifecycle and context abstractions
 * @doc.layer platform
 */
plugins {
    `java-library`
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Platform Kernel Core - module lifecycle and context abstractions"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // ActiveJ (mandatory async framework)
    api("io.activej:activej-promise:6.0-rc2")
    implementation("io.activej:activej-eventloop:6.0-rc2")

    // JSON utilities
    api("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    implementation("commons-codec:commons-codec:1.17.1")

    // Logging
    api("org.slf4j:slf4j-api:2.0.12")

    // Nullability annotations
    compileOnly("org.jetbrains:annotations:24.1.0")

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
    testCompileOnly("org.projectlombok:lombok:1.18.32")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.32")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.assertj:assertj-core:3.25.3")
    testImplementation("org.mockito:mockito-core:5.11.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.11.0")
    testImplementation("org.openjdk.jmh:jmh-core:1.37")
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

tasks.test {
    useJUnitPlatform()
}
