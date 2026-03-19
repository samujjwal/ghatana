/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

plugins {
    id("java-library")
}

dependencies {
    // Platform core dependencies
    implementation(platform(":platform:java:core"))
    implementation(":platform:java:core")
    implementation(":platform:java:domain")
    implementation(":platform:java:database")
    implementation(":platform:java:observability")
    implementation(":platform:java:config")
    implementation(":platform:java:workflow")
    implementation(":platform:java:audit")
    
    // Kernel modules
    implementation(":platform:java:kernel:modules:authentication")
    implementation(":platform:java:kernel:modules:config")
    implementation(":platform:java:kernel:modules:event-store")
    implementation(":platform:java:kernel:modules:audit")
    
    // ActiveJ Promise
    implementation("io.activej:activej-promise")
    
    // Jackson for JSON
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Micrometer for metrics
    implementation("io.micrometer:micrometer-core")
    
    // PostgreSQL
    implementation("org.postgresql:postgresql")
    implementation("com.zaxxer:HikariCP")
    
    // HTTP client for OPA
    implementation("io.activej:activej-http")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("io.activej:activej-test")
}
