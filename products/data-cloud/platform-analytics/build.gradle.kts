plugins {
    id("java-library")
}

group = "com.ghatana.datacloud"
version = "2026.3.1-SNAPSHOT"

description = "Data Cloud Platform Analytics Module"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":products:data-cloud:spi"))
    api(project(":products:data-cloud:platform-entity"))
    api(project(":products:data-cloud:platform-event"))
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:testing"))

    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(libs.jackson.databind)

    implementation(libs.caffeine)
    implementation(libs.jsqlparser)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}