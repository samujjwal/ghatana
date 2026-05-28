plugins {
    id("java-module")
}

group = "com.ghatana.yappc"
version = rootProject.version
description = "YAPPC Infrastructure - AEP Integration"

dependencies {
    implementation(project(":products:yappc:core:yappc-shared"))

    // AEP registry/runtime bindings owned by this adapter module.
    api(project(":products:data-cloud:planes:action:registry"))
    api(project(":products:data-cloud:planes:action:engine"))
    api(project(":products:data-cloud:planes:action:agent-runtime"))
    api(project(":products:data-cloud:planes:action:operator-contracts"))
    api(project(":products:data-cloud:planes:action:orchestrator"))

    implementation(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
