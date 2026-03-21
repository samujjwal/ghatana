plugins {
    id("java-library")
}

group = "com.ghatana.datacloud"
version = "2026.3.1-SNAPSHOT"

description = "Data Cloud Platform - Analytics, Query Engine & Reporting bounded context"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":products:data-cloud:platform-entity"))
    api(project(":products:data-cloud:platform-event"))
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))

    api(libs.activej.promise)
    api(libs.activej.eventloop)

    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)

    implementation(libs.clickhouse.client)
    runtimeOnly(libs.clickhouse.http.client)
    implementation(libs.opensearch.java)
    implementation(libs.opensearch.rest.client)
    implementation(libs.jsqlparser)

    compileOnly(libs.trino.spi)
    compileOnly(libs.trino.plugin.toolkit)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}
