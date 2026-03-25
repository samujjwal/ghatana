plugins {
    id("java-library")
}

description = "YAPPC Delivery Specialists - Release, DevOps, compliance, and security pipeline agents"

dependencies {
    api(project(":products:yappc:core:agents:runtime"))
    api(project(":products:yappc:core:agents:common"))
    api(project(":products:yappc:core:ai"))
    api(project(":products:yappc:core:yappc-domain-impl"))
    api(project(":platform:java:agent-core"))
    implementation(project(":products:aep:aep-agent-runtime"))

    implementation(libs.activej.promise)
    implementation(libs.activej.eventloop)
    implementation(libs.slf4j.api)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
