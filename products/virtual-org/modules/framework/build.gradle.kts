plugins {
    id("java-module")
}

dependencies {
    // Platform modules
    api(project(":platform:java:domain"))
    api(project(":platform:java:core"))
    api(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:agent-core"))
    implementation(project(":products:aep:aep-agent-runtime"))  // Migrated from agent-memory: MemoryPlane

    // AEP contracts (provides com.ghatana.core.operator.* and com.ghatana.core.pipeline.*)
    implementation(project(":products:aep:aep-operator-contracts"))

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
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.micrometer.core)
}
