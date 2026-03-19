/**
 * Compliance Domain Module - Regulatory Compliance
 */
plugins {
    id("java-library")
}

group = "com.ghatana.products.finance.domains"
version = "1.0.0"
description = "Compliance Domain - Regulatory Compliance System"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":platform:java:kernel"))
    api(project(":products:finance:domains:oms"))
    api(project(":products:finance:domains:ems"))
    api(libs.activej.promise)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
