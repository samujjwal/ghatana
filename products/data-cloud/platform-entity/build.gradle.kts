plugins {
    id("java-library")
}

group = "com.ghatana.datacloud"
version = "2026.3.1-SNAPSHOT"

description = "Data Cloud Platform Entity Module"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:database"))
    api(project(":platform:java:observability"))

    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(platform(libs.jackson.bom))
    api(libs.jackson.annotations)
    api(libs.jackson.databind)
    api(libs.jackson.datatype.jsr310)
    api(libs.jakarta.persistence.api)
    api(libs.jakarta.validation.api)

    implementation(libs.hibernate.core)
    implementation(libs.hibernate.validator)
    implementation(libs.caffeine)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    compileOnly(libs.spotbugs.annotations)
}

tasks.test {
    useJUnitPlatform()
}