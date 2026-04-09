plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
    id("com.ghatana.testing-conventions")
}

group = "com.ghatana.platform"
description = "Platform Core - Basic utilities, types, and common patterns"

dependencies {
    api(libs.activej.promise)
    api(libs.micrometer.core)
    api(libs.slf4j.api)
    api(libs.bundles.jackson.json)
    api("jakarta.validation:jakarta.validation-api:3.0.2")
    api(libs.protobuf.java)
    api("javax.annotation:javax.annotation-api:1.3.2")
    api(libs.nimbus.jose.jwt)
    compileOnly(libs.jetbrains.annotations)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.archunit.junit5)
}

tasks.processResources {
    val platformVersion = rootProject.version.toString()
    filesMatching("META-INF/platform.properties") {
        expand(
            "platformVersion" to platformVersion,
            "sdkVersion" to platformVersion,
            "instrumentationVersion" to platformVersion
        )
    }
}
