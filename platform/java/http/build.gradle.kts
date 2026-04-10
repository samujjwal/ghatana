plugins {
    id("java-module")
}

description = "Platform HTTP - HTTP client and server utilities"

dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:runtime"))
    api(libs.bundles.activej.http)
    api(libs.okhttp.client)
    api(libs.caffeine)
    api(libs.guava)
    api(libs.jackson.databind)
    implementation(libs.slf4j.api)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.mockwebserver)
}
