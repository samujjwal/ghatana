plugins {
    id("java-library")
}

group = "com.ghatana.core"
version = "1.0.0-SNAPSHOT"

dependencies {
    // Platform dependencies
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:governance"))
    api(project(":platform:java:connectors"))
    
    // EventCloud abstraction (platform-only, no product dependency)
    api(project(":platform:java:event-cloud"))

    // ActiveJ Promise support (type references OK per architecture)
    api(libs.activej.promise)
    implementation(libs.activej.common)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
