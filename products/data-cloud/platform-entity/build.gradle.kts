plugins {
    id("java-library")
}

group = "com.ghatana.datacloud"
version = rootProject.version

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
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":products:data-cloud:spi"))
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