plugins {
    id("java-library")
}

group = "com.ghatana.products.yappc"
version = rootProject.version

description = "YAPPC SPI - Unified Plugin SPI & Client API (merged: yappc-plugin-spi + yappc-client-api)"

dependencies {
    // Deprecated compatibility wrapper: use yappc-shared as the single source of truth.
    api(project(":products:yappc:core:yappc-shared"))
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
