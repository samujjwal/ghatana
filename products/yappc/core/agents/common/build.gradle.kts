plugins {
    id("java-module")
}

description = "YAPPC Agent Common - Shared Input/Output classes and interfaces for all agent modules"

dependencies {
    api(project(":products:yappc:core:yappc-domain-impl"))
    api(project(":platform:java:agent-core"))

    implementation(libs.activej.promise)
    implementation(libs.slf4j.api)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    testImplementation("com.tngtech.archunit:archunit-junit5:1.0.1")
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    // useJUnitPlatform() already applied by java-module
    testLogging {
        events("passed", "skipped", "failed")
    }
    finalizedBy(tasks.jacocoTestReport)
}

// jacoco and jacocoTestReport configured by java-module
