plugins {
    id("java-library")
}

group = "com.ghatana.platform"
version = "1.0.0-SNAPSHOT"

description = "Platform Security - Authentication, Authorization, Encryption"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Platform dependencies
    api(project(":platform:java:core"))
    api(project(":platform:java:config"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:governance"))
    api(project(":platform:java:http"))
    
    // JWT / Auth / OAuth2 (canonical: Nimbus JOSE+JWT)
    api(libs.nimbus.jose.jwt)
    implementation(libs.nimbus.oauth2.sdk)
    implementation(libs.jbcrypt)
    
    // Encryption
    implementation(libs.bouncycastle.provider)
    
    // Caching (for OAuth2 session management)
    implementation(libs.caffeine)
    
    // Token store adapters (from former auth module)
    implementation(libs.jedis)
    compileOnly(libs.jakarta.persistence.api)
    
    // JSON processing (for token serialization)
    implementation(libs.jackson.databind)
    
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    // ActiveJ for async
    api(libs.activej.promise)
    api(libs.activej.http)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
