plugins {
    id("java-module")
}

description = "Platform Core - Basic utilities, types, and common patterns"

dependencies {
    api(libs.activej.promise)
    api(libs.micrometer.core)
    api(libs.slf4j.api)
    api(libs.bundles.jackson.json)
    api(libs.jakarta.validation.api)
    api(libs.protobuf.java)
    api(libs.javax.annotation.api)
    api(libs.nimbus.jose.jwt)
    compileOnly(libs.jetbrains.annotations)
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":platform:java:agent-core"))
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
