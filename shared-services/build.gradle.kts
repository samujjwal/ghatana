plugins {
    id("java-library")
}

group = "com.ghatana.services"
version = rootProject.version

description = "Shared Services - Cross-product microservices"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
