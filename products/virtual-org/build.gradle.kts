plugins {
    id("java-module")
}

group = "com.ghatana.virtualorg"
version = rootProject.version


dependencies {
    // ActiveJ for async support
    implementation(libs.bundles.activej.core)

    // Platform libraries
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:domain"))

    // Logging
    implementation(libs.bundles.logging.core)

    // Testing
    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform:java:testing"))
}


description = "Virtual-Org - Virtual Organization Framework"
