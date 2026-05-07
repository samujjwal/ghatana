plugins {
    id("java-module")
}

dependencies {
    // Sibling modules
    implementation(project(":products:virtual-org:modules:framework"))
    implementation(project(":products:virtual-org:modules:workflow"))

    // AEP engine (operator framework: AbstractOperator, UnifiedOperator, etc.)
    implementation(project(":products:data-cloud:planes:action:engine"))

    // Platform modules
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:core"))

    // ActiveJ
    implementation(libs.activej.promise)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(project(":platform:java:testing"))
}
