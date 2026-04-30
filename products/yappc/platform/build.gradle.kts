plugins {
    id("java-module")
}

group = "com.ghatana.yappc"
version = rootProject.version

dependencies {
    // Platform dependencies (regular libraries, not BOMs)
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:agent-core"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:observability"))

    // YAPPC framework modules
    implementation(project(":products:yappc:core:yappc-infrastructure"))
    implementation(project(":products:yappc:core:ai"))
    implementation(project(":products:yappc:core:agents"))

    // ActiveJ Framework - Use version catalog for consistency
    implementation(libs.activej.common)
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)
    implementation(libs.activej.http)
    implementation(libs.activej.boot)
    implementation(libs.activej.config)
    implementation(libs.activej.launcher)
    // WebSocket support (merged from activej-websocket)
    api(libs.activej.csp)
    api(libs.activej.bytebuf)

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // GraphQL
    implementation(libs.graphql.java)

    // Database
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.jakarta.persistence.api)

    // AI/ML
    implementation(libs.langchain4j.core)

    // Kubernetes
    implementation(libs.fabric8.kubernetes.client)

    // CLI Framework
    implementation(libs.picocli)

    // Terminal / readline
    implementation(libs.jline)

    // JSON Schema Validation
    implementation(libs.networknt.validator)

    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j.impl)

    // gRPC
    implementation(libs.grpc.stub)
    implementation(libs.grpc.netty.shaded)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(project(":products:yappc:core:yappc-domain-impl"))
}
