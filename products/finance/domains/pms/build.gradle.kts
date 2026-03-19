/**
 * PMS Domain Module - Portfolio Management System
 */
plugins {
    id("java-library")
}

group = "com.ghatana.products.finance.domains"
version = "1.0.0"
description = "PMS Domain - Portfolio Management System"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":platform:java:kernel"))
    api(project(":products:finance:domains:oms"))
    api(libs.activej.promise)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
