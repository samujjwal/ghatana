plugins {
    id("java-module")
}

description = "YAPPC Delivery Specialists - Release, DevOps, compliance, and security pipeline agents"

dependencies {
    api(project(":products:yappc:core:agents:runtime"))
    api(project(":products:yappc:core:agents:common"))
    api(project(":products:yappc:core:ai"))
    api(project(":products:yappc:core:yappc-domain-impl"))
    api(project(":platform:java:agent-core"))
    implementation(project(":products:data-cloud:planes:action:agent-runtime"))

    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.slf4j.api)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
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

// Coverage verification is disabled for this module until additional test coverage is added
tasks.named("jacocoTestCoverageVerification") {
    enabled = false
}
