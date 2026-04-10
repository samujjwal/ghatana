plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.platform"
version = rootProject.version

description = """
    AI Integration — Unified AI platform module.
    Includes all previously-separate submodules: registry, observability, feature-store.

    Packages:
      com.ghatana.ai.*                      — LLM clients, embeddings, prompts, vector store
      com.ghatana.aiplatform.registry.*     — Model registry, version control, deployment tracking
      com.ghatana.aiplatform.observability.* — AI metrics, cost tracking, drift detection
      com.ghatana.aiplatform.featurestore.*  — Feature engineering, storage, and serving
""".trimIndent()

dependencies {
    // Core Platform
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:database"))

    // ActiveJ (for OpenAI async client code)
    api(libs.bundles.activej.core)

    // OpenAI client (from ai-experimental)
    implementation("com.openai:openai-java:0.25.0")

    // Database (registry + feature-store)
    implementation(libs.bundles.database.core)

    // Redis (feature-store)
    implementation(libs.bundles.redis)

    // Jackson for JSON
    implementation(libs.bundles.jackson.json)

    // Validation (registry)
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")

    // Logging
    api(libs.bundles.logging.core)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j.impl)

    // Annotations
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}

