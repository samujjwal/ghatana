plugins {
    id("java")
    id("application")
}

group = "com.ghatana.services"
version = rootProject.version

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
    implementation(libs.bundles.activej.http)
    
    // OAuth2 & JWT (absorbed from auth-service)
    implementation(libs.bundles.security.core)
    
    // Password hashing (absorbed from auth-service)
    implementation("org.mindrot:jbcrypt:0.4")
    
    // Session caching (absorbed from auth-service)
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    
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
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
}
