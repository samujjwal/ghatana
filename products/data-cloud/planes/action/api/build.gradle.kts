plugins {
    id("java-module")
}

dependencies {
    // Depends on core and security
    implementation(project(":products:data-cloud:planes:action:engine"))
    implementation(project(":products:data-cloud:planes:action:security"))
    implementation(project(":products:data-cloud:planes:action:registry"))

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
