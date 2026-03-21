plugins {
    id("java-library")
}

group = "com.ghatana.datacloud"
version = "2026.3.1-SNAPSHOT"

description = "Data Cloud Platform - Entity & Metadata Management bounded context"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":products:data-cloud:spi"))
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:database"))
    api(project(":platform:java:audit"))

    api(libs.activej.promise)
    api(libs.activej.eventloop)

    api(libs.jakarta.persistence.api)
    api(libs.jakarta.validation.api)
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.datatype.jsr310)

    implementation(libs.hibernate.core)
    implementation(libs.hibernate.validator)
    implementation(libs.hikaricp)
    runtimeOnly(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}
