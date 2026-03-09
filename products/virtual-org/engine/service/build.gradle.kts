plugins {
    id("java-library")
    application
    alias(libs.plugins.protobuf)
}

application {
    mainClass.set("com.ghatana.virtualorg.service.VirtualOrgServiceMain")
}

dependencies {
    // Sibling modules
    implementation(project(":products:virtual-org:modules:framework"))
    implementation(project(":products:virtual-org:modules:integration"))
    implementation(project(":products:virtual-org:modules:workflow"))

    // Platform modules
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:event-cloud"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:agent-framework"))
    implementation(project(":platform:java:agent-memory"))  // TODO: Migrate from custom AgentMemory to platform MemoryPlane

    // Platform contracts (proto-generated types)
    implementation(project(":platform:contracts"))

    // ActiveJ
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)
    implementation(libs.activej.http)

    // LangChain4J
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.open.ai)
    implementation(libs.langchain4j.anthropic)

    // Jackson
    implementation(libs.jackson.databind)
    implementation(libs.jackson.annotations)
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.datatype.jsr310)

    // Protobuf
    implementation(libs.protobuf.java)
    implementation(libs.protobuf.java.util)

    // gRPC
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.netty.shaded)

    // Security (JWT - canonical: Nimbus JOSE+JWT)
    implementation(libs.nimbus.jose.jwt)

    // Redis
    implementation(libs.jedis)

    // Git
    implementation(libs.jgit)

    // Config
    implementation(libs.typesafe.config)

    // Observability
    implementation(libs.micrometer.core)
    implementation(libs.micrometer.registry.otlp)
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.exporter.otlp)

    // Logging
    implementation(libs.slf4j.api)
    runtimeOnly(libs.log4j.slf4j.impl)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // OkHttp
    implementation(libs.okhttp)

    // Testing
    testImplementation(libs.bundles.test.essentials)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(project(":platform:java:testing"))
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
}

// Resolve duplicate jars in distributions (e.g., workflow from both platform and product modules)
tasks.withType<AbstractArchiveTask>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
