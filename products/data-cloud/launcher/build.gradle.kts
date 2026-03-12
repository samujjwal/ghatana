plugins {
    `java-library`
    `maven-publish`
}

dependencies {
    // Platform module
    implementation(project(":products:data-cloud:platform"))
    
    // Core platform dependencies
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:governance"))

    // gRPC transport (runtime) — needed to start the gRPC server
    implementation(libs.grpc.netty.shaded)
    
    // ActiveJ framework
    implementation(libs.activej.launcher)
    implementation(libs.activej.http)
    implementation(libs.activej.inject)
    implementation(libs.activej.config)
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)
    implementation(libs.activej.csp)
    implementation(libs.activej.bytebuf)
    
    // Jackson for JSON
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    
    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.core)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
