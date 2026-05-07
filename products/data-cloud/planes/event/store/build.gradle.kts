plugins {
    id("java-module")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data-Cloud warm-tier event log store — lightweight extraction of WarmTierEventLogStore from platform-launcher (DC-A10)"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // EventLogStore SPI (append, read, tail, subscription contracts)
    api(project(":products:data-cloud:planes:shared-spi"))

    // ActiveJ Promise for non-blocking wrapping of JDBC calls
    api(libs.activej.promise)

    // Jackson for JSON header serialisation in event rows
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.17.2"))
    implementation("com.fasterxml.jackson.core:jackson-databind")

    // JDBC connection pool (optional — callers may supply their own DataSource)
    compileOnly(libs.hikaricp)

    // Structured logging
    implementation(libs.slf4j.api)

    // ── Test ─────────────────────────────────────────────────────────────────
    // Integration tests require a real PostgreSQL container
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.testing.containers)
    testImplementation(libs.hikaricp)
    testRuntimeOnly("org.postgresql:postgresql")
}

tasks.test {
    useJUnitPlatform()

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
