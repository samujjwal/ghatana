/**
 * @doc.type module
 * @doc.purpose All Software-Org department implementations in a single Gradle module
 * @doc.layer product
 * @doc.pattern Consolidated Module
 */
plugins {
    id("java-module")
}

group = "com.ghatana.softwareorg"
version = rootProject.version

description = "All department implementations for software-org: compliance, devops, engineering, finance, hr, marketing, product, qa, sales, support"

dependencies {
    // Domain models (includes Virtual-Org framework)
    implementation(project(":products:software-org:engine:modules:domain-model"))

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

tasks.test {
    useJUnitPlatform()
}
