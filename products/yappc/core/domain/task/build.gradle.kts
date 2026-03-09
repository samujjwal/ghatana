// YAPPC Task Domain Models
plugins {
    id("java-library")
}

dependencies {
    // ActiveJ for async operations
    implementation(libs.activej.promise)
    
    // Annotations
    compileOnly("org.jetbrains:annotations:24.0.1")
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

description = "YAPPC Task Domain Models"
