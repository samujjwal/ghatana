plugins {
    id("java-library")
}

group = "com.ghatana.datacloud"
version = "2026.3.1-SNAPSHOT"

description = "Data Cloud Platform - Event Sourcing & Streaming bounded context"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":products:data-cloud:spi"))
    api(project(":platform:java:core"))
    api(project(":platform:java:event-cloud"))

    api(libs.activej.promise)
    api(libs.activej.eventloop)

    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)

    implementation(libs.kafka.clients)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}
