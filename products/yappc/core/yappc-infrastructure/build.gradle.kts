plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

description = "YAPPC Consolidated Infrastructure Module"

dependencies {
    // Platform modules
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:observability"))

    // YAPPC shared utilities (includes AgentRegistryPort)
    implementation(project(":products:yappc:core:yappc-shared"))

    // AEP Agent Registry — for AepAgentRegistryAdapter only
    implementation(project(":products:aep:aep-registry"))

    // AEP Unified Runtime — for AepAgentRuntimeAdapter (Phase 1.6: aep-engine + aep-agent-runtime + aep-central-runtime)
    implementation(project(":products:aep:aep-engine"))
    implementation(project(":products:aep:aep-agent-runtime"))
    
    // Database
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    
    // Redis
    implementation(libs.jedis)
    
    // Messaging
    implementation(libs.kafka.clients)
    
    // Monitoring
    implementation(libs.micrometer.core)
    implementation(libs.micrometer.registry.prometheus)
    
    // Configuration
        
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Handle duplicate entries in sourcesJar
tasks.withType<org.gradle.jvm.tasks.Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Source sets for infrastructure components
sourceSets {
    main {
        java {
            srcDirs(
                "src/main/java",
                "src/storage/java",
                "src/messaging/java",
                "src/monitoring/java"
            )
        }
        resources {
            srcDirs(
                "src/main/resources",
                "src/storage/resources",
                "src/messaging/resources",
                "src/monitoring/resources"
            )
        }
    }
    test {
        java {
            srcDirs(
                "src/test/java",
                "src/storage/test",
                "src/messaging/test",
                "src/monitoring/test"
            )
        }
    }
}

// Infrastructure-specific tasks
tasks.register("setupInfrastructure") {
    group = "infrastructure"
    description = "Setup infrastructure components"
    
    doLast {
        println("Setting up infrastructure components...")
    }
}

tasks.register("healthCheck") {
    group = "verification"
    description = "Health check for infrastructure components"
    
    doLast {
        println("Running infrastructure health checks...")
    }
}
