plugins {
    id("java-library")
}

group = "com.ghatana.platform"
version = "1.0.0-SNAPSHOT"

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

    // Test
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
