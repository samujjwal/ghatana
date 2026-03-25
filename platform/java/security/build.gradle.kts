plugins {
    id("java-library")
}

group = "com.ghatana.platform"
version = "2026.3.1-SNAPSHOT"

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
    // governance and http are internal implementation details:
    // - governance: only used by RBACFilter and PolicyService internals
    // - http: only used by PermissionEnforcerFilter (an internal servlet filter)
    // Keeping these as `implementation` prevents downstream consumers from
    // accidentally taking transitive governance/http dependencies through security.
    implementation(project(":platform:java:governance"))
    implementation(project(":platform:java:http"))
    
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
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
