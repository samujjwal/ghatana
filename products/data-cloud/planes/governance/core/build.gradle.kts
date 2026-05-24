plugins {
    id("java-module")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud Platform Governance — PII masking, field redaction, audit logging, and retention classification"

dependencies {
    api(project(":platform:java:core"))
    implementation(project(":products:data-cloud:planes:data:entity"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}
