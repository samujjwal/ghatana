plugins {
    id("java-library")
}

group = "com.ghatana.services"
version = "1.0.0-SNAPSHOT"

description = "Shared Services - Cross-product microservices"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
