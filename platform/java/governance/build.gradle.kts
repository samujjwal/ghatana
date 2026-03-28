plugins {
    id("java-library")
}

group = "com.ghatana.platform"
version = rootProject.version

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:contracts"))
    // Platform HTTP — for FilterChain.Filter integration (TenantExtractionFilter)
    compileOnly(project(":platform:java:http"))
    api(libs.activej.promise)
    api(libs.activej.http)
    // gRPC — optional, only needed for TenantGrpcInterceptor
    compileOnly(libs.grpc.api)
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
    testImplementation(libs.archunit)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    // ArchUnit scans the full com.ghatana classpath; needs more heap than the default 512m
    jvmArgs("-Xmx2g")
}
