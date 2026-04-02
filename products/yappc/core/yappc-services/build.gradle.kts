plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

description = "YAPPC Consolidated Services Module"

dependencies {
    // Platform modules
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:governance"))
    implementation(project(":platform:java:security"))
    
    // YAPPC domain (both core and libs versions)
    implementation(project(":products:yappc:core:yappc-domain-impl"))
    implementation(project(":products:yappc:libs:java:yappc-domain"))
    
    // YAPPC agents runtime (for AepEventPublisher)
    implementation(project(":products:yappc:core:agents:runtime"))
    
    // Data-Cloud SPI (for DataCloudClient)
    // TODO(ADAPTER-SEAM): data-cloud coupling in a domain services module.
    //   Future: introduce DataCloudPort in core; move impl to infrastructure:datacloud
    implementation(project(":products:data-cloud:spi"))
    
    // YAPPC infrastructure
    implementation(project(":products:yappc:core:yappc-infrastructure"))
    
    // YAPPC shared utilities
    implementation(project(":products:yappc:core:yappc-shared"))
    
    // Validation
    implementation(libs.hibernate.validator)
    implementation(libs.jakarta.validation.api)
    
    // JSON processing
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    
    // Async processing
    implementation(libs.activej.promise)
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform()
}

// Handle duplicate entries in JAR tasks
tasks.withType<org.gradle.jvm.tasks.Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Source sets for service components
sourceSets {
    main {
        java {
            srcDirs(
                "src/main/java",
                "src/orchestration/java",
                "src/validation/java",
                "src/transformation/java"
            )
        }
        resources {
            srcDirs(
                "src/main/resources",
                "src/orchestration/resources",
                "src/validation/resources",
                "src/transformation/resources"
            )
        }
    }
    test {
        java {
            srcDirs(
                "src/test/java",
                "src/orchestration/test",
                "src/validation/test",
                "src/transformation/test"
            )
        }
    }
}

// Service-specific tasks
tasks.register("validateServices") {
    group = "verification"
    description = "Validate service configurations"
    
    doLast {
        println("Validating service configurations...")
    }
}

tasks.register("serviceHealthCheck") {
    group = "verification"
    description = "Health check for all services"
    
    doLast {
        println("Running service health checks...")
    }
}
