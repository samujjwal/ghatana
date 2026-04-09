plugins {
    id("java-library")
    application
    id("com.ghatana.protobuf-conventions")
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
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:agent-core"))
    implementation(project(":products:aep:aep-agent-runtime"))  // Migrated from agent-memory: MemoryPlane

    // Platform contracts (proto-generated types)
    implementation(project(":platform:contracts"))

    // ActiveJ
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)
    implementation(libs.activej.http)

    // LangChain4J
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j.open.ai)
    implementation("dev.langchain4j:langchain4j-anthropic:0.36.2")
    
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
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")
    
    // Config
    
    // Observability
    implementation(libs.micrometer.core)
    implementation("io.micrometer:micrometer-registry-otlp:1.15.0")
        implementation(libs.opentelemetry.api)
        
    // Logging
    implementation(libs.slf4j.api)
    runtimeOnly(libs.log4j.slf4j.impl)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // OkHttp
    implementation(libs.okhttp)

    // Testing
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(project(":platform:java:testing"))
}


// Resolve duplicate jars in distributions (e.g., workflow from both platform and product modules)
tasks.withType<AbstractArchiveTask>().configureEach {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
