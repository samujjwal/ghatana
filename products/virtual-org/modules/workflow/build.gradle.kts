plugins {
    id("java-module")
}

dependencies {
    // ActiveJ
    implementation(libs.activej.promise)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(project(":platform:java:testing"))
}
