plugins {
    id("com.ghatana.java-conventions")
    `java-library`
}

dependencies {
    // Depends on core for base classes
    implementation(project(":products:aep:platform-core"))
    implementation(project(":products:aep:platform-registry"))
    
    // Platform libraries
    implementation(project(":platform:java:agent-framework"))
    implementation(project(":platform:java:agent-registry"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:audit"))
    implementation(project(":platform:contracts"))
    
    // ActiveJ for async operations
    implementation(libs.activej.promise)
    
    // Jackson for JSON processing
    implementation(libs.jackson.databind)
    
    // Lombok
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
    // Jakarta Persistence API
    compileOnly(libs.jakarta.persistence.api)
    
    // Hibernate (for @CreationTimestamp, @UpdateTimestamp)
    compileOnly(libs.hibernate.core)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform()
}

// Target: < 10 classes in this module
