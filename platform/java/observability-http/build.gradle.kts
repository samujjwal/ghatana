plugins {
    id("com.ghatana.java-conventions")
    id("java-library")
}

group = "com.ghatana.platform"

dependencies {
    // Bridge between observability and HTTP modules
    api(project(":platform:java:observability"))
    api(project(":platform:java:observability-clickhouse"))
    api(project(":platform:java:http"))

    // Core types
    implementation(project(":platform:java:core"))

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(testFixtures(project(":platform:java:observability")))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}
