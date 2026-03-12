plugins {
    id("java-library")
    id("application")
    id("jacoco")
    // id("com.diffplug.spotless") version "6.25.0" - temporarily disabled
}

group = "com.ghatana.products.yappc"
version = rootProject.version

application {
    mainClass.set("com.ghatana.yappc.api.ApiApplication")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Include AEP-specific sources (EventSchemaValidator, EventValidationResult)
// alongside the standard source directories. Use setSrcDirs to avoid duplicates.
sourceSets {
    main {
        java.setSrcDirs(listOf("src/main/java", "aep/src/main/java"))
        resources.setSrcDirs(listOf("src/main/resources", "aep/src/main/resources"))
    }
}



repositories {
    mavenCentral()
}

dependencies {
    // Extracted backend modules
    api(project(":products:yappc:backend:persistence"))
    api(project(":products:yappc:backend:auth"))
    api(project(":products:yappc:backend:deployment"))
    api(project(":products:yappc:backend:websocket"))

    // Lifecycle service (DlqPublisher SPI + operator types)
    implementation(project(":products:yappc:services:lifecycle"))

    // Core Platform Libraries
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:audit"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:agent-framework"))
    implementation(project(":platform:java:agent-memory"))
    
    // AEP Platform Libraries
    implementation(project(":products:aep:platform"))
    
    // Domain model entities
    api(project(":products:yappc:libs:java:yappc-domain"))
    
    // AI module (includes canvas-ai)
    implementation(project(":products:yappc:core:ai"))

    // Framework module (PluginSandbox, PluginAuditStore, etc.)
    implementation(project(":products:yappc:core:framework"))
    
    // Data Cloud Platform (instead of non-existent core)
    implementation(project(":products:data-cloud:platform"))
    
    // JWT Dependencies - uses platform security module's JWT support
    implementation(project(":platform:java:security"))
    // JJWT for local JWT token provider
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")
    
    // ActiveJ Framework - using version catalog
    implementation(libs.activej.boot)
    implementation(libs.activej.http)
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)
    implementation(libs.activej.launcher)
    
    // JSON Processing
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.dataformat.yaml)

    // JSON Schema validation (networknt) — used by EventSchemaValidator (AEP)
    implementation(libs.networknt.validator)

    // YAML Template Engine — used by WorkflowMaterializer (Ph3)
    implementation(project(":platform:java:yaml-template"))
    
    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    
    // Validation
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")

    // DI via ActiveJ inject (standardized - javax.inject removed)
    
    // Docker/Testcontainers for build execution
    implementation(libs.testcontainers.core)
    implementation("com.github.docker-java:docker-java:3.3.0")
    
    // Testcontainers for integration tests
    testImplementation(libs.testcontainers.junit.jupiter)
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // Testcontainers for integration tests
    testImplementation(libs.testcontainers.postgresql)
    
    // Database connectivity
    implementation("com.zaxxer:HikariCP:5.1.0")
    runtimeOnly("org.postgresql:postgresql:42.7.1")
    
    // Database migrations
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    
    // Prometheus metrics
    implementation(libs.micrometer.registry.prometheus)

    // Legacy feature packages (codegen) - used by /api/codegen/* endpoints
    implementation("io.swagger.parser.v3:swagger-parser:2.1.22")
    implementation("com.graphql-java:graphql-java:21.5")
    implementation("com.graphql-java:graphql-java-extended-scalars:21.0")
}

tasks.test {
    useJUnitPlatform()
    
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
    }
    
    // ActiveJ requires this for proper shutdown
    systemProperty("io.activej.eventloop.gracefulShutdownMillis", "100")
}

/*
// Spotless configuration for code formatting - temporarily disabled
spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.19.1")
        importOrder()
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
        
        // Custom license header
        licenseHeader("""
            /*
             * Copyright (c) 2025 Ghatana Technologies
             * YAPPC API Module
             */
        """.trimIndent())
    }
}
*/

// Disabled for now - formatting issues
// tasks.named("compileJava") {
//     dependsOn("spotlessApply")
// }
// Configure distribution task to handle duplicate JARs
tasks.named<Tar>("distTar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.named<Zip>("distZip") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// ============================================================================
// JaCoCo Code Coverage Configuration
// ============================================================================

jacoco {
    toolVersion = "0.8.11"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) {
                exclude(
                    "**/config/**",
                    "**/dto/**",
                    "**/model/**",
                    "**/*Application.class"
                )
            }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    
    violationRules {
        rule {
            limit {
                minimum = 0.10.toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}
