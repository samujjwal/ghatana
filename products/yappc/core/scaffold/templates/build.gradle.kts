plugins {
    id("java-library")
}

description = "YAPPC Scaffold Templates - Template loading, parsing, and rendering (foundational layer)"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

dependencies {
    api(project(":platform:java:domain"))
    api(project(":platform:java:core"))

    api(libs.jackson.databind)
    api(libs.jackson.annotations)
    api(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.slf4j.api)
    implementation(libs.diffutils)
    implementation(libs.handlebars)
    implementation(libs.networknt.validator)
    implementation("org.yaml:snakeyaml")
    implementation(libs.openrewrite.core)
    implementation(libs.openrewrite.java)

    implementation(libs.activej.common)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.mockito.core)
}
