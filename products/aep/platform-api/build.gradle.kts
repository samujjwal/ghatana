plugins {
    id("com.ghatana.java-conventions")
    `java-library`
}

dependencies {
    // Depends on core and security
    implementation(project(":products:aep:platform-core"))
    implementation(project(":products:aep:platform-security"))
    implementation(project(":products:aep:platform-registry"))
    
    // ActiveJ for HTTP and async
    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    
    // Jackson for JSON
    implementation(libs.jackson.databind)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform()
}

// Target: < 50 classes in this module (expert interfaces)
