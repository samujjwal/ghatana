plugins {
    id("java")
    id("application")
}

group = "com.ghatana.products.yappc"
version = "1.0.0-SNAPSHOT"

description = "YAPPC Services - Unified services module (merged: ai, api, domain, infrastructure, lifecycle, scaffold)"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

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
    implementation(project(":platform:java:event-cloud"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:governance"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:runtime"))
    implementation(project(":platform:java:plugin"))
    implementation(project(":platform:java:agent-framework"))
    implementation(project(":platform:java:testing"))
    
    // YAPPC modules
    implementation(project(":products:yappc:backend:api"))
    implementation(project(":products:yappc:platform"))
    implementation(project(":products:yappc:libs:java:yappc-domain"))
    
    // YAPPC Core modules
    implementation(project(":products:yappc:core:ai"))
    implementation(project(":products:yappc:core:agents"))
    implementation(project(":products:yappc:core:lifecycle"))
    implementation(project(":products:yappc:core:spi"))
    implementation(project(":products:yappc:core:framework"))
    implementation(project(":products:yappc:core:scaffold:core"))
    implementation(project(":products:yappc:core:scaffold:api"))
    implementation(project(":products:yappc:core:scaffold:packs"))
    implementation(project(":products:yappc:infrastructure:datacloud"))
    
    // Data Cloud
    implementation(project(":products:data-cloud:platform"))
    
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
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.zaxxer:HikariCP:5.1.0")
    
    // GraphQL (aligned with backend:api)
    implementation("com.graphql-java:graphql-java:21.5")
    implementation("com.graphql-java:graphql-java-extended-scalars:21.0")

    // gRPC (from services:api)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.netty)

    // AI/ML (from services:ai)
    implementation("dev.langchain4j:langchain4j:0.25.0")

    // Validation (from services:domain)
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")

    // JSON Schema Validation (from services:scaffold)
    implementation("com.networknt:json-schema-validator:1.3.3")
    
    // Docker (for runtime build execution)
    implementation("com.github.docker-java:docker-java:3.3.4")
    // Testcontainers for integration tests only (not production classpath)
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    
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
    testImplementation(project(":products:yappc:core:lifecycle"))
    testImplementation(project(":products:yappc:infrastructure:datacloud"))
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
