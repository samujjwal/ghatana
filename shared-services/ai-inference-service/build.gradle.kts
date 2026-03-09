plugins {
    id("java")
    id("application")
}

description = "AI Inference Service - REST/gRPC API for LLM Gateway and Model Serving"

dependencies {
    // Platform libraries (updated paths)
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:database"))

    // ActiveJ
    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)
    implementation(libs.activej.eventloop)

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Database
    implementation(libs.postgresql)
    implementation(libs.hikaricp)

    // Logging
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j.impl)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

application {
    mainClass.set("com.ghatana.services.aiinference.AIInferenceServiceLauncher")
}

tasks.test {
    useJUnitPlatform()
}

