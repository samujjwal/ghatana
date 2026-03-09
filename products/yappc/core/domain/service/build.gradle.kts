// YAPPC Task Service implementation module
plugins {
    id("java-library")
}

dependencies {
    // Task domain models
    api(project(":products:yappc:core:domain:task"))
    
    // Agent framework and domain
    implementation(project(":products:yappc:core:domain"))
    implementation(project(":platform:java:agent-framework"))
    
    // ActiveJ for async operations
    implementation(libs.activej.promise)
    
    // YAML parsing (SnakeYAML)
    implementation("org.yaml:snakeyaml:2.0")
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Annotations
    compileOnly("org.jetbrains:annotations:24.0.1")
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:runtime"))
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

description = "YAPPC Task Service implementation"
