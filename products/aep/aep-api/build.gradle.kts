plugins {
    id("com.ghatana.java-conventions")
    `java-library`
}

dependencies {
    // Depends on core and security
    implementation(project(":products:aep:aep-engine"))
    implementation(project(":products:aep:aep-security"))
    implementation(project(":products:aep:aep-registry"))
    
    // ActiveJ for HTTP and async
    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    
    // Jackson for JSON
    implementation(libs.jackson.databind)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}

// Target: < 50 classes in this module (expert interfaces)
