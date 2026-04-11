plugins {
    id("java-library")
}

group = "com.ghatana.platform"
version = rootProject.version

description = "Platform Security - Authentication, Authorization, Encryption"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // Core platform dependencies — exposed as API since consumers need these types
    api(project(":platform:java:core"))
    api(project(":platform:java:config"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:database"))  // Canonical Repository interface
    // NOTE: governance and http as implementation to avoid circular dependency
    implementation(project(":platform:java:governance"))
    implementation(project(":platform:java:http"))
    
    // JWT / Auth / OAuth2 (canonical: Nimbus JOSE+JWT)
    api(libs.nimbus.jose.jwt)
    implementation("com.nimbusds:oauth2-oidc-sdk:11.20.1")
    implementation("org.mindrot:jbcrypt:0.4")
    
    // Encryption
    implementation(libs.bouncycastle.provider)
    
    // Caching (for OAuth2 session management)
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    
    // Token store adapters (from former auth module)
    implementation(libs.jedis)
    compileOnly("jakarta.persistence:jakarta.persistence-api:3.1.0")
    
    // JSON processing (for token serialization)
    implementation(libs.jackson.databind)
    
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
    
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
    testImplementation("com.github.tomakehurst:wiremock-jre8:3.0.1")
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
