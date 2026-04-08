/*
 * Platform Analytics Module - Build Configuration
 * 
 * Contains anomaly detection, forecasting, pattern analysis, and BI services.
 * Isolated from core to reduce complexity and allow independent scaling.
 */

plugins {
    id("com.ghatana.java-conventions")
    `java-library`
}

dependencies {
    // Depends on core
    implementation(project(":products:aep:aep-engine"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:audit"))
    implementation(project(":platform:contracts"))
    
    // ActiveJ
    implementation(libs.activej.promise)
    
    // Jackson
    implementation(libs.jackson.databind)
    
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Target: < 150 classes
