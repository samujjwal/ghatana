plugins {
    id("java-module")
}

dependencies {
    // Platform cache abstractions (in database module)
    implementation(project(":platform:java:database"))
    
    // Platform observability
    implementation(project(":platform:java:observability"))
    
    // Redis client
    implementation(libs.lettuce.core)
    
    // JSON serialization
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    
    // Caffeine for local cache (as used in AEP)
    implementation(libs.caffeine)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.core)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}
