plugins {
    id("java-library")
}

group = "com.ghatana.products.yappc"
version = "1.0.0-SNAPSHOT"

description = "YAPPC SPI - Unified Plugin SPI & Client API (merged: yappc-plugin-spi + yappc-client-api)"

dependencies {
    // Framework API for legacy plugin support
    compileOnly(project(":products:yappc:core:framework"))

    // Platform plugin SPI
    api(project(":platform:java:plugin"))

    // ActiveJ dependencies
    api(libs.activej.eventloop)
    api(libs.activej.promise)
    implementation(libs.activej.common)
    implementation(libs.activej.inject)

    // Logging
    implementation(libs.slf4j.api)

    // Utilities
    implementation(libs.guava)

    // Testing
    testImplementation(project(":platform:java:plugin"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation(libs.activej.test)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1G"
}

tasks.javadoc {
    options.encoding = "UTF-8"
}
