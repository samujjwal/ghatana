plugins {
    id("java-library")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Platform plugin dependency
    implementation(project(":platform:java:plugin"))
    implementation(project(":products:yappc:core:lifecycle"))
    implementation(project(":products:yappc:core:ai"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    // implementation(project(":libs:common-utils")) - path needs verification
    // implementation(project(":libs:validation")) - path needs verification
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.inject)
    implementation(libs.activej.http)
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation("org.commonmark:commonmark:0.21.0")
    implementation("org.apache.pdfbox:pdfbox:3.0.1")
    implementation(libs.slf4j.api)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

description = "YAPPC Knowledge Graph - Consolidated integration module"
