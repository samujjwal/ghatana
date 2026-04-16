plugins {
    id("java-module")
}

group = "com.ghatana.platform"
version = rootProject.version

description = "Platform Cache - Distributed Caching Infrastructure"

dependencies {
    // Platform core
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:observability"))
    
    // Redis client (lettuce for async Redis)
    implementation(libs.lettuce.core)
    
    // Jackson for serialization
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
