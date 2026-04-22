plugins {
    id("java-module")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud cross-module integration test suite"

dependencies {
    testImplementation(project(":products:data-cloud:launcher"))
    testImplementation(project(":products:data-cloud:platform-launcher"))
    testImplementation(project(":products:data-cloud:platform-entity"))
    testImplementation(project(":products:data-cloud:spi"))
    testImplementation(project(":platform:java:domain"))
    testImplementation(project(":platform:java:testing"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)

    // Testcontainers for real provider integration tests
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.redis)
    testImplementation(libs.testcontainers.clickhouse)

    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform {
        excludeTags("performance", "integration")
    }
}

tasks.named("jacocoTestReport") {
    enabled = false
}

tasks.named("jacocoTestCoverageVerification") {
    enabled = false
}