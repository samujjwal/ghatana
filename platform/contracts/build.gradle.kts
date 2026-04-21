plugins {
    id("protobuf-module")
}

description = "Platform Contracts - Shared Protobuf definitions and schemas"

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

sourceSets {
    create("generators") {
        java {
            srcDirs("src/main/java")
            include("com/ghatana/contracts/schema/**/*.java")
        }
        compileClasspath += configurations.getByName("compileClasspath")
        runtimeClasspath += configurations.getByName("runtimeClasspath")
    }
}
