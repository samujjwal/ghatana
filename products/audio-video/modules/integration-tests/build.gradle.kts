plugins {
    id("java-module")
}

dependencies {
    testImplementation(project(":products:audio-video:modules:speech:stt-service"))
    testImplementation(project(":products:audio-video:modules:speech:tts-service"))
    testImplementation(project(":products:audio-video:modules:vision:vision-service"))
    testImplementation(project(":products:audio-video:modules:intelligence:multimodal-service"))
    testImplementation(project(":products:audio-video:modules:infrastructure:persistence"))
    testImplementation(project(":products:audio-video:libs:common"))
    testImplementation(project(":products:audio-video:libs:java:common"))

    testImplementation(project(":platform:java:testing"))

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)

    testImplementation(libs.grpc.netty.shaded)
    testImplementation(libs.grpc.protobuf)
    testImplementation(libs.grpc.stub)
    testImplementation("io.grpc:grpc-inprocess:${libs.versions.grpc.get()}")
    testImplementation("io.grpc:grpc-testing:${libs.versions.grpc.get()}")
    testImplementation(libs.protobuf.java)
}

tasks.test {
    useJUnitPlatform()

    // Keep integration-style names visible to CI even under the default test task.
    include("**/*Test.class", "**/*IT.class", "**/*E2ETest.class")
}


