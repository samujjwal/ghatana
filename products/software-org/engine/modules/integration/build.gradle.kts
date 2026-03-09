plugins {
    java
}

description = "Software-Org Integration - EventCloud publishing and external integrations"

dependencies {
    // Only depend on core abstractions, NOT on departments (avoids circular dependencies)
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:core"))
    
    // Optional dependencies for integration with external systems
    implementation(project(":products:aep:platform"))

    // Tests
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
}
