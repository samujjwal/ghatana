plugins {
    id("java-module")
}

group = "com.ghatana.platform"
version = rootProject.version

description = "Platform Security - Authentication, Authorization, Encryption"

dependencies {
    // Core platform dependencies — exposed as API since consumers need these types
    api(project(":platform:java:core"))
    api(project(":platform:java:config"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:database"))  // Canonical Repository interface
    // Governance provides TenantContext, Principal (compileOnly - optional dependency)
    compileOnly(project(":platform:java:governance"))
    // HTTP dependency removed - filters migrated to platform:java:http
    // Security module now provides abstractions only, HTTP module provides filter implementations

    // JWT / Auth / OAuth2 (canonical: Nimbus JOSE+JWT)
    api(libs.nimbus.jose.jwt)
    implementation("com.nimbusds:oauth2-oidc-sdk:11.37.2")
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
    testImplementation(project(":platform:java:governance"))  // For Principal in tests
    testImplementation("com.github.tomakehurst:wiremock-jre8:3.0.1")
}
