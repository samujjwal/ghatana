plugins {
    id("protobuf-module")
}

description = "Platform Contracts - Shared Protobuf definitions and schemas"

dependencies {
    implementation(libs.bundles.jackson.json)
    implementation(libs.bundles.jackson.yaml)
    implementation("com.sun.codemodel:codemodel:2.6")
    implementation("org.jsonschema2pojo:jsonschema2pojo-core:1.2.2")
    implementation("com.github.spullara.mustache.java:compiler:0.9.14")
    implementation("org.jsoup:jsoup:1.15.3")
    implementation(libs.bundles.common.utils)
    implementation("com.google.protobuf:protobuf-java-util:4.34.1")
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
