plugins {
    java
}

description = "Software-Org Integration - EventCloud publishing and external integrations"

dependencies {
    // Only depend on core abstractions, NOT on departments (avoids circular dependencies)
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:core"))
    
    // Tests
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
}
