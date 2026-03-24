plugins {
    id("com.ghatana.java-conventions")
    id("com.ghatana.java-library-conventions")
}

description = "YAPPC Consolidated Services Module"

dependencies {
    // Platform dependencies
    implementation(platform("com.ghatana:platform-bom"))
    
    // Platform services
    implementation("com.ghatana.platform:service-core")
    implementation("com.ghatana.platform:workflow-core")
    
    // YAPPC domain
    implementation(projects.yappcDomain)
    
    // YAPPC agents
    implementation(projects.yappcAgents)
    
    // YAPPC infrastructure
    implementation(projects.yappcInfrastructure)
    
    // YAPPC shared utilities
    implementation(projects.yappcShared)
    
    // Validation
    implementation("org.hibernate.validator:hibernate-validator")
    implementation("jakarta.validation:jakarta.validation-api")
    
    // JSON processing
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    
    // Async processing
    implementation("io.activej:activej-promise")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
}

tasks.test {
    useJUnitPlatform()
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
