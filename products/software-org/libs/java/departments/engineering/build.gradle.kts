plugins {
    java
}

group = "com.ghatana.softwareorg"
version = "1.0.0-SNAPSHOT"

dependencies {
    // Software-Org Domain Models (includes Virtual-Org framework)
    implementation(project(":products:software-org:engine:modules:domain-model"))

    // Organization event contracts

    // AEP integration
    implementation(project(":products:aep:platform"))

    // Core abstractions
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:observability"))

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
}

description = "Engineering Department module for software-org"

