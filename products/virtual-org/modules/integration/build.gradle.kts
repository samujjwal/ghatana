plugins {
    id("java-library")
}

dependencies {
    // Sibling modules
    implementation(project(":products:virtual-org:modules:framework"))
    implementation(project(":products:virtual-org:modules:workflow"))

    // AEP engine (operator framework: AbstractOperator, UnifiedOperator, etc.)
    implementation(project(":products:aep:platform-engine"))

    // Platform modules
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:core"))

    // ActiveJ
    implementation(libs.activej.promise)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.bundles.test.essentials)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(project(":platform:java:testing"))
}
