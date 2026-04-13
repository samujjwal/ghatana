plugins {
    id("java-module")
}

group = "com.ghatana.platform"
version = rootProject.version

description = "Platform Identity — Agent identity brokering, delegation tokens, credential management"


dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:observability"))

    api(libs.activej.promise)

    // Test
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
