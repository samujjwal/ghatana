plugins {
    id("protobuf-module")
}

description = "Platform Contracts - Shared Protobuf definitions and schemas"

// Ensure generated proto sources are included in the main source set
sourceSets.main {
    java.srcDirs("build/generated/source/proto/main/java")
}

dependencies {
    implementation(libs.bundles.jackson.json)
    implementation(libs.bundles.jackson.yaml)
    implementation(libs.codemodel)
    implementation(libs.jsonschema2pojo.core)
    implementation(libs.mustache.compiler)
    implementation(libs.jsoup)
    implementation(libs.bundles.common.utils)
    implementation(libs.protobuf.java.util)
    testImplementation(libs.bundles.testing.core)
    testRuntimeOnly(libs.junit.platform.launcher)
}
