plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
    id("com.ghatana.testing-conventions")
}

group = "com.ghatana.platform"
description = "Platform HTTP - HTTP client and server utilities"

dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:runtime"))
    api(libs.bundles.activej.http)
    api("com.squareup.okhttp3:okhttp:4.12.0")
    api("com.github.ben-manes.caffeine:caffeine:3.1.8")
    api(libs.guava)
    api(libs.jackson.databind)
    implementation(libs.slf4j.api)
    testImplementation(project(":platform:java:testing"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
