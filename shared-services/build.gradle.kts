plugins {
    id("java-library")
}

group = "com.ghatana.services"
version = "2026.3.1-SNAPSHOT"

description = "Shared Services - Cross-product microservices"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
