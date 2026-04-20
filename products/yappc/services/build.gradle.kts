plugins {
    id("java-application")
}

group = "com.ghatana.products.yappc"
version = rootProject.version

description = "YAPPC Services - Integration test aggregator (bounded contexts: domain, infrastructure, lifecycle, scaffold)"

// Override default mainClass from java-application convention
application {
    mainClass.set("com.ghatana.yappc.api.ApiApplication")
}

// Avoid duplicate platform JAR entries in distribution archives
tasks.withType<AbstractArchiveTask>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

dependencies {
    // Platform modules (existing in ghatana-new)
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:audit"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:governance"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:runtime"))
    implementation(project(":platform-kernel:kernel-plugin"))
    implementation(project(":platform:java:agent-core"))

    // YAPPC modules
    // ===== Services bounded-context sub-modules =====
    implementation(project(":products:yappc:core:services-platform"))
    implementation(project(":products:yappc:core:services-lifecycle"))  // was: services:lifecycle (Phase 2: moved to core/)

    // backend:api removed (2026-03-23) — functionality consolidated into core modules
    implementation(project(":products:yappc:platform"))
    implementation(project(":products:yappc:libs:java:yappc-domain"))

    // YAPPC Core modules
    implementation(project(":products:yappc:core:ai"))
    implementation(project(":products:yappc:core:agents"))
    implementation(project(":products:yappc:core:yappc-services"))
    implementation(project(":products:yappc:core:yappc-shared"))
    implementation(project(":products:yappc:core:yappc-infrastructure"))
    implementation(project(":products:yappc:core:scaffold:core"))  // absorbs packs
    implementation(project(":products:yappc:core:scaffold:api"))
    implementation(project(":products:yappc:infrastructure:datacloud"))

    // ActiveJ Framework
    implementation(libs.activej.boot)
    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)
    implementation(libs.activej.launcher) // activej-launchers-http (includes activej-launcher transitively)

    // JSON Processing
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.dataformat.yaml)
    implementation(platform(libs.jackson.bom))

    // Swagger/OpenAPI (aligned with backend:api)
    implementation("io.swagger.parser.v3:swagger-parser:2.1.22")

    // PostgreSQL
    implementation(libs.postgresql)
    implementation("com.zaxxer:HikariCP:5.1.0")

    // GraphQL
    implementation(libs.graphql.java)
    implementation(libs.graphql.extended.scalars)

    // gRPC (from services:api)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.netty.shaded)

    // AI/ML (from services:ai)
    implementation("dev.langchain4j:langchain4j:0.25.0")

    // Validation (from services:domain)
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")

    // JSON Schema Validation
    implementation(libs.networknt.validator)

    // Docker (for runtime build execution)
    implementation(libs.docker.java)

    // Prometheus
    implementation("io.prometheus:simpleclient:0.16.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.2")

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)

    // Inject
    implementation("javax.inject:javax.inject:1")

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":products:yappc:core:yappc-services"))
    testImplementation(project(":products:yappc:infrastructure:datacloud"))
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
// useJUnitPlatform() already applied by java-application
