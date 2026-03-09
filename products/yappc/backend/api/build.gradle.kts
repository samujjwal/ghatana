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



repositories {
    mavenCentral()
}

dependencies {
    // Extracted backend modules
    api(project(":products:yappc:backend:persistence"))
    api(project(":products:yappc:backend:auth"))
    api(project(":products:yappc:backend:deployment"))
    api(project(":products:yappc:backend:websocket"))

    // Core Platform Libraries
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:audit"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:ai-integration"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:agent-framework"))
    
    // AEP Platform Libraries
    implementation(project(":products:aep:platform"))
    
    // Domain model entities
    api(project(":products:yappc:libs:java:yappc-domain"))
    
    // AI module (includes canvas-ai)
    implementation(project(":products:yappc:core:ai"))
    
    // Data Cloud Platform (instead of non-existent core)
    implementation(project(":products:data-cloud:platform"))
    
    // JWT Dependencies - uses platform security module's JWT support
    implementation(project(":platform:java:security"))
    
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
    
    // LangChain4J - LLM Integration
    implementation(libs.langchain4j)
    implementation(libs.langchain4j.open.ai)
    implementation(libs.langchain4j.anthropic)
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
                minimum = 0.80.toBigDecimal()
            }
        }
        
        rule {
            element = "CLASS"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.60".toBigDecimal()
            }
            excludes = listOf(
                "*.config.*",
                "*.dto.*",
                "*.model.*",
                "*Application"
            )
        }
    }
}

tasks.check {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}
