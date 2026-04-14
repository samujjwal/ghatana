plugins {
    id("java-module")
}

dependencies {
    // Platform database - use the JpaRepository abstraction
    implementation(project(":platform:java:database"))
    
    // Platform governance for tenant context
    implementation(project(":platform:java:governance"))
    
    // Platform core utilities
    implementation(project(":platform:java:core"))
    
    // JPA/Hibernate (implementation only, no Spring)
    implementation(libs.jakarta.persistence.api)
    implementation(libs.hibernate.core)
    
    // Database drivers
    implementation(libs.postgresql)
    
    // Connection pooling
    implementation(libs.hikaricp)
    
    // Migration
    implementation(libs.flyway.core)
    
    // JSON handling
    implementation(libs.jackson.databind)
    
    // Logging
    implementation(libs.slf4j.api)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.h2)
}

tasks.test {
    useJUnitPlatform()
}
