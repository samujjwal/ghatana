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

tasks.register<Test>("contractCompatibilityTest") {
    group = "verification"
    description = "Runs proto/OpenAPI contract and compatibility tests for platform contracts"
    useJUnitPlatform()
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    shouldRunAfter(tasks.named("test"))

    // Contract suites only: keeps this gate fast and focused.
    include("**/*ContractTest.class")
    include("**/*CompatibilityTest.class")
    include("**/*RoundTripTest.class")
}

tasks.register("contractCompatibilityGate") {
    group = "verification"
    description = "Validates platform contract compatibility (proto + OpenAPI + client/server contracts)"
    dependsOn("contractCompatibilityTest")
}

// Copy product OpenAPI specs to test resources so contract tests can load them from the classpath.
// Follows the same pattern as the auth-gateway and AI-service specs already in test/resources/.
tasks.register<Copy>("copyProductOpenApiSpecs") {
    group = "build"
    description = "Copies product-level OpenAPI specs into test resources for contract test classpath"

    // AEP spec (lives alongside other platform specs)
    from(layout.projectDirectory.file("openapi/aep.yaml")) {
        rename { "aep.yaml" }
    }

    // Data-Cloud spec (lives in the product area)
    from(rootProject.layout.projectDirectory.file("products/data-cloud/api/openapi.yaml")) {
        rename { "data-cloud-openapi.yaml" }
    }

    // YAPPC spec
    from(rootProject.layout.projectDirectory.file("products/yappc/api/yappc-api.openapi.yaml")) {
        rename { "yappc-api.openapi.yaml" }
    }

    into(layout.projectDirectory.dir("src/test/resources"))
    duplicatesStrategy = DuplicatesStrategy.REPLACE
}

tasks.named("processTestResources") {
    dependsOn("copyProductOpenApiSpecs")
}

tasks.named("check") {
    dependsOn("contractCompatibilityGate")
}
