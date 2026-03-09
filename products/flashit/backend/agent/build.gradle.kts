plugins {
    java
}

group = "com.ghatana.flashit"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // =========================================================================
    // ActiveJ Framework (core platform — no project deps needed)
    // =========================================================================
    implementation(libs.activej.core)
    implementation(libs.activej.http)
    implementation(libs.activej.inject)
    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.launcher)
    implementation(libs.activej.boot)
    implementation(libs.activej.config)
    implementation(libs.activej.servicegraph)

    // =========================================================================
    // Platform Governance (multi-tenancy)
    // =========================================================================
    implementation(project(":platform:java:governance"))

    // =========================================================================
    // Jackson JSON
    // =========================================================================
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.datatype.jdk8)

    // =========================================================================
    // AI / LLM Integration
    // =========================================================================
    implementation("com.openai:openai-java:${libs.versions.openai.get()}")

    // =========================================================================
    // Logging
    // =========================================================================
    implementation(libs.slf4j.api)
    runtimeOnly(libs.log4j.core)
    runtimeOnly(libs.log4j.api)
    runtimeOnly(libs.log4j.slf4j.impl)

    // =========================================================================
    // Lombok
    // =========================================================================
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // =========================================================================
    // Testing
    // =========================================================================
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.activej.test)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "com.ghatana.flashit.agent.FlashItAgentApplication",
            "Implementation-Title" to "flashit-agent",
            "Implementation-Version" to version
        )
    }
}

// Copy runtime dependencies to build/libs/dependencies/ for Docker packaging
tasks.register<Copy>("copyDependencies") {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("libs/dependencies"))
}

tasks.named("build") {
    dependsOn("copyDependencies")
}
