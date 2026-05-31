plugins {
    id("java-module")
}

dependencies {
    // Platform security
    implementation(project(":platform:java:security"))
    
    // Platform governance for tenant context
    implementation(project(":platform:java:governance"))
    
    // Platform observability for metrics
    implementation(project(":platform:java:observability"))
    
    // Audio-video messaging module
    implementation(project(":products:audio-video:modules:infrastructure:messaging"))
    
    // gRPC for interceptors
    implementation("io.grpc:grpc-api:1.79.0")
    implementation(libs.grpc.stub)
    
    // ActiveJ for HTTP security filter (if needed)
    implementation(libs.activej.http)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}
