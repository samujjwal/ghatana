plugins {
    id("com.ghatana.java-conventions")
    id("com.ghatana.java-library-conventions")
}

description = "YAPPC Consolidated Agents Module"

dependencies {
    // Platform dependencies
    implementation(platform("com.ghatana:platform-bom"))
    
    // Platform agent framework
    implementation("com.ghatana.platform:agent-core")
    implementation("com.ghatana.platform:agent-registry")
    implementation("com.ghatana.platform:agent-framework-api")
    
    // YAPPC domain
    implementation(projects.yappcDomain)
    
    // YAPPC shared utilities
    implementation(projects.yappcShared)
    
    // YAPPC infrastructure
    implementation(projects.yappcInfrastructure)
    
    // YAML parsing
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    
    // JSON Schema validation
    implementation("com.networknt:json-schema-validator")
    
    // Migration tooling
    implementation("com.github.javaparser:javaparser-core")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
    
    // Test fixtures
    testFixturesImplementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")
}

tasks.test {
    useJUnitPlatform()
}

// Java source sets for different agent specialties
sourceSets {
    main {
        java {
            srcDirs(
                "src/main/java",
                "src/architecture/java",
                "src/code/java", 
                "src/data/java",
                "src/security/java",
                "src/testing/java"
            )
        }
        resources {
            srcDirs(
                "src/main/resources",
                "src/architecture/resources",
                "src/code/resources",
                "src/data/resources", 
                "src/security/resources",
                "src/testing/resources"
            )
        }
    }
    test {
        java {
            srcDirs(
                "src/test/java",
                "src/architecture/test",
                "src/code/test",
                "src/data/test",
                "src/security/test", 
                "src/testing/test"
            )
        }
    }
}

// Agent-specific configurations
tasks.register("validateAgentConfigs") {
    group = "verification"
    description = "Validate all YAML agent configurations"
    
    doLast {
        // Validation logic will be implemented
        println("Validating agent configurations...")
    }
}

tasks.register("generateAgentDocs") {
    group = "documentation"
    description = "Generate documentation for all agents"
    
    doLast {
        // Documentation generation logic
        println("Generating agent documentation...")
    }
}

// Make validate run before build
tasks.named("check") {
    dependsOn("validateAgentConfigs")
}
