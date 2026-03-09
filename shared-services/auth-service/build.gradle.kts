plugins {
    id("java-library")
}

description = "Authentication Service - Centralized OAuth2/OIDC authentication"

dependencies {
    // Platform security
    api(project(":platform:java:security"))
    api(project(":platform:java:config"))
    api(project(":platform:java:http"))
    
    // OAuth2 & JWT (canonical: Nimbus JOSE+JWT)
    implementation(libs.nimbus.oauth2.sdk)
    implementation(libs.nimbus.jose.jwt)
    
    // Password hashing
    implementation(libs.jbcrypt)
    
    // Session caching
    implementation(libs.caffeine)
    
    // ActiveJ for async HTTP
    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)
    implementation(libs.activej.launcher)
    
    // Database
    implementation(project(":platform:java:database"))
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
}
