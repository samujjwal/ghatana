plugins {
    id("java-library")
    id("com.ghatana.java-conventions")
}

description = "YAPPC Consolidated Infrastructure Module"

dependencies {
    // Platform modules
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:distributed-cache"))
    implementation(project(":platform:java:observability"))
    
    // YAPPC shared utilities
    implementation(project(":products:yappc:core:yappc-shared"))
    
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
    implementation(libs.typesafe.config)
    
    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.junit.jupiter)
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
