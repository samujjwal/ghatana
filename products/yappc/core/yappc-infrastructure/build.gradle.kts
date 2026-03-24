plugins {
    id("com.ghatana.java-conventions")
    id("com.ghatana.java-library-conventions")
}

description = "YAPPC Consolidated Infrastructure Module"

dependencies {
    // Platform dependencies
    implementation(platform("com.ghatana:platform-bom"))
    
    // Platform infrastructure components
    implementation("com.ghatana.platform:storage-core")
    implementation("com.ghatana.platform:messaging-core")
    implementation("com.ghatana.platform:monitoring-core")
    
    // YAPPC shared utilities
    implementation(projects.yappcShared)
    
    // Database
    implementation("org.postgresql:postgresql")
    implementation("com.zaxxer:HikariCP")
    
    // Redis
    implementation("redis.clients:jedis")
    
    // Messaging
    implementation("org.apache.kafka:kafka-clients")
    
    // Monitoring
    implementation("io.micrometer:micrometer-core")
    implementation("io.micrometer:micrometer-registry-prometheus")
    
    // Configuration
    implementation("com.typesafe:config")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:kafka")
    testImplementation("org.testcontainers:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
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
