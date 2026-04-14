plugins {
    id("java-module")
    `maven-publish`
}

dependencies {
    // Platform modules
    implementation(project(":products:data-cloud:platform-launcher"))
    implementation(project(":products:data-cloud:platform-api"))
    implementation(project(":products:data-cloud:platform-plugins"))

    // Core platform dependencies
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:governance"))
    implementation(project(":platform:java:audit"))

    // AI platform integration — model registry, feature store, observability (all merged into ai-integration)
    implementation(project(":platform:java:ai-integration"))

    // HikariCP for AI service DataSource creation in standalone launcher
    implementation(libs.hikaricp)

    // gRPC transport (runtime) — needed to start the gRPC server
    implementation(libs.grpc.netty.shaded)

    // ActiveJ framework
    implementation(libs.activej.launcher)
    implementation(libs.activej.http)
    implementation(libs.activej.inject)
    implementation(libs.activej.config)
    implementation(libs.bundles.activej.core)
    implementation(libs.activej.promise)
    implementation(libs.activej.csp)
    implementation(libs.activej.bytebuf)

    // Jackson for JSON
    implementation(libs.jackson.core)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.core)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")

    // Testcontainers for integration testing
    testImplementation("org.testcontainers:testcontainers:1.19.7")
    testImplementation("org.testcontainers:postgresql:1.19.7")
    testImplementation("org.testcontainers:kafka:1.19.7")
    testImplementation("org.testcontainers:localstack:1.19.7")
    testImplementation("org.testcontainers:junit-jupiter:1.19.7")
}

tasks.test {
    // useJUnitPlatform() already applied by java-module; keep maxParallelForks override
    maxParallelForks = 1
}

// HTTP endpoint test suites re-enabled as part of DATA_CLOUD_REMEDIATION_IMPLEMENTATION_PLAN Phase 3.
// All handlers have been verified and loadBody().getResult() NPE patterns resolved.
