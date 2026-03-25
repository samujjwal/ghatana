plugins {
    id("java-library")
}

description = "YAPPC Architecture Specialists - Design patterns and architecture analysis agents"

dependencies {
    api(project(":products:yappc:core:agents:runtime"))
    api(project(":products:yappc:core:agents:common"))
    api(project(":products:yappc:core:agents:code-specialists"))
    api(project(":products:yappc:core:ai"))
    api(project(":products:yappc:core:yappc-domain-impl"))
    api(project(":platform:java:agent-core"))
    
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
