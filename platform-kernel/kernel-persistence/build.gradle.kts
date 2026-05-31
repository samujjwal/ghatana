plugins {
    id("java-module")
}

description = "Platform Kernel Persistence - durable storage adapters"

dependencies {
    api(project(":platform-kernel:kernel-core"))
    implementation(project(":platform:java:cache"))
    implementation(project(":platform:java:database"))
    implementation(libs.postgresql)
    implementation(libs.jedis)
    api(libs.activej.promise)
    api(libs.slf4j.api)

    testImplementation(libs.h2)
}
