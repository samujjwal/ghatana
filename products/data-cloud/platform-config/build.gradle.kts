plugins {
    id("java-library")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud Platform Config Module"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))
    api(project(":products:data-cloud:platform-entity"))

    api(platform(libs.jackson.bom))
    api(libs.jackson.annotations)
    api(libs.activej.eventloop)
    api(libs.activej.promise)
    api(libs.jakarta.validation.api)

    implementation(project(":platform:java:config"))

    implementation(libs.activej.inject)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.slf4j.api)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.jetbrains.annotations)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)

    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

tasks.test {
    useJUnitPlatform()
}