plugins {
    id("java-library")
}

dependencies {
    // Platform modules
    api(project(":platform:java:domain"))
    api(project(":platform:java:core"))
    api(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:event-cloud"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:agent-framework"))
    implementation(project(":platform:java:agent-memory"))  // TODO: Migrate from custom AgentMemory to platform MemoryPlane

    // AEP platform (provides com.ghatana.core.operator.* and com.ghatana.core.pipeline.*)
    // TODO: Migrate core operator/pipeline types to platform:java:workflow
    implementation(project(":products:aep:platform"))

    // ActiveJ
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.annotations)

    // Observability
    implementation(libs.micrometer.core)

    // Logging
    implementation(libs.slf4j.api)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing
    testImplementation(libs.bundles.test.essentials)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.micrometer.core)
}
