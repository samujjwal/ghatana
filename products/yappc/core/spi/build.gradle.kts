plugins {
    id("java-library")
}

group = "com.ghatana.products.yappc"
version = "2026.3.1-SNAPSHOT"

description = "YAPPC SPI - Unified Plugin SPI & Client API (merged: yappc-plugin-spi + yappc-client-api)"

dependencies {
    // Legacy plugin migration adapters are disabled (2026-03-24)
    // See: adapter/LegacyPluginAdapter.java.disabled, migration/PluginMigrationUtil.java.disabled
    // TODO: Remove when legacy plugin migration is complete

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
