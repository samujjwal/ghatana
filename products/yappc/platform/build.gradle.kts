plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

group = "com.ghatana.products.yappc"
version = "1.0.0-SNAPSHOT"

dependencies {
    // Platform dependencies (regular libraries, not BOMs)
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:testing"))
    implementation(project(":platform:java:agent-framework"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:observability"))
    
    // YAPPC framework modules
    implementation(project(":products:yappc:core:framework"))
    implementation(project(":products:yappc:core:ai"))
    // TODO: Register :products:yappc:core:agents in settings.gradle.kts before enabling
    // implementation(project(":products:yappc:core:agents"))

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
    implementation("com.graphql-java:graphql-java:21.3")

    // Database
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.postgresql:postgresql:42.7.2")
    implementation("jakarta.persistence:jakarta.persistence-api:3.1.0")

    // AI/ML
    implementation("dev.langchain4j:langchain4j:0.25.0")

    // Kubernetes
    implementation("io.fabric8:kubernetes-client:6.9.2")

    // CLI Framework
    implementation("info.picocli:picocli:4.7.5")
    
    // Terminal / readline
    implementation("org.jline:jline:3.25.1")
    
    // JSON Schema Validation
    implementation("com.networknt:json-schema-validator:1.3.3")
    
    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j.impl)
    
    // gRPC
    implementation(libs.grpc.stub)
    implementation(libs.grpc.netty)

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(project(":products:yappc:core:domain"))
}
