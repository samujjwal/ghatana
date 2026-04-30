plugins {
    id("java-module")
}

dependencies {
    // Audio-video persistence entities used by transcription cache serialization
    implementation(project(":products:audio-video:modules:infrastructure:persistence"))

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
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.redis)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
    // Exclude integration tests (ending in IT) as they require Docker/Testcontainers
    exclude("**/*IT.class", "**/*IT$*.class")
}
