plugins {
    id("java-module")
}

description = "YAPPC Facade Layer — external system adapters"

dependencies {
    // Platform modules
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:governance"))

    // YAPPC domain
    implementation(project(":products:yappc:libs:java:yappc-domain"))

    // Data Cloud integration
    implementation(project(":products:data-cloud:planes:shared-spi"))

    // ActiveJ for async
    implementation(libs.activej.promise)

    // Jackson for JSON
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
}
