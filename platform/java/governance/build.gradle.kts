plugins {
    id("java-module")
}

group = "com.ghatana.platform"
version = rootProject.version


dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:contracts"))
    // HTTP dependency removed - TenantExtractionFilter migrated to platform:java:http
    api(libs.activej.promise)
    // gRPC — optional, only needed for TenantGrpcInterceptor
    compileOnly("io.grpc:grpc-api:1.79.0")
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.databind)

    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Test
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":platform:java:http"))  // For FilterChain.Filter
    testImplementation(project(":shared-services:auth-gateway"))  // For CredentialStore
    testImplementation(project(":platform:java:agent-core"))  // For MemoryStore
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation("com.tngtech.archunit:archunit:1.3.0")
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    // ArchUnit scans the full com.ghatana classpath; needs more heap than the default 512m
    jvmArgs("-Xmx2g")
}
