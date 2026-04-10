plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
    id("jacoco")
}

description = "YAPPC Consolidated Agents Module"

dependencies {
    // JavaParser for code analysis and migration
    implementation("com.github.javaparser:javaparser-core:3.25.1")

    // Platform agent modules
    implementation(project(":platform:java:agent-core"))
    implementation(project(":products:aep:aep-agent-runtime"))
    
    // AEP registry service
    implementation(project(":products:aep:aep-registry"))
    
    // YAPPC agents aggregator (includes all specialist modules)
    implementation(project(":products:yappc:core:agents"))
    
    // YAPPC domain
    implementation(project(":products:yappc:core:yappc-domain-impl"))
    
    // YAPPC API (for domain classes)
    implementation(project(":products:yappc:core:yappc-api"))
    
    // YAPPC shared utilities
    implementation(project(":products:yappc:core:yappc-shared"))
    
    // YAPPC infrastructure
    implementation(project(":products:yappc:core:yappc-infrastructure"))
    
    // YAML parsing
    implementation(libs.jackson.dataformat.yaml)
    implementation(libs.jackson.databind)
    
    // JSON Schema validation
    implementation(libs.networknt.validator)
    
    // Migration tooling
        
    // Lombok for data classes
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    
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

tasks.withType<ProcessResources> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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

// Jacoco configuration — version from catalog (do not hardcode)
// Apply com.ghatana.testing-conventions for new modules instead of configuring jacoco directly.

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                counter = "BRANCH"
                value   = "COVEREDRATIO"
                minimum = "0.00".toBigDecimal()
            }
            limit {
                counter = "LINE"
                value   = "COVEREDRATIO"
                minimum = "0.00".toBigDecimal()
            }
        }
    }
}
