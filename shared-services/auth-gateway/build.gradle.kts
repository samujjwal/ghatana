plugins {
    id("java")
    id("application")
}

group = "com.ghatana.services"
version = "2026.3.1-SNAPSHOT"

application {
    mainClass.set("com.ghatana.services.auth.AuthGatewayLauncher")
}

dependencies {
    // Platform libraries (updated paths)
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:config"))      // Absorbed from auth-service
    implementation(project(":platform:java:database"))    // Absorbed from auth-service
    
    // ActiveJ - Use canonical versions from libs.versions.toml
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)
    implementation(libs.activej.http)
    implementation(libs.activej.inject)     // Absorbed from auth-service
    implementation(libs.activej.launcher)   // Absorbed from auth-service
    
    // OAuth2 & JWT (absorbed from auth-service)
    implementation(libs.nimbus.oauth2.sdk)
    implementation(libs.nimbus.jose.jwt)
    
    // Password hashing (absorbed from auth-service)
    implementation(libs.jbcrypt)
    
    // Session caching (absorbed from auth-service)
    implementation(libs.caffeine)
    
    // JSON
    implementation(libs.jackson.databind)
    implementation(libs.jackson.dataformat.yaml)
    
    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j.impl)
    
    // Database connection pool
    implementation(libs.hikaricp)
    implementation(libs.postgresql)

    // Rate limiting
    implementation(libs.guava)
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform()
}
