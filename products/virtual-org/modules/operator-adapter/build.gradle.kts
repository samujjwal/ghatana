plugins {
    id("java-module")
}

dependencies {
    // Platform modules
    api(project(":platform:java:domain"))
    api(project(":platform:java:core"))
    api(project(":platform:java:workflow"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:agent-core"))

    // Virtual-org modules
    api(project(":products:virtual-org:modules:agent"))

    // ActiveJ for async support
    implementation(libs.activej.promise)

    // Micrometer for metrics
    implementation(libs.micrometer.core)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.micrometer.core)
    testImplementation(project(":platform:java:testing"))
}
