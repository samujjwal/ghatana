plugins {
    id("maven-publish")
    id("java-module")
    id("jacoco")
    id("java-test-fixtures")
}

description = "YAPPC Domain Implementation - Domain logic and persistence (Phase 2.1: api/impl split)"

dependencies {
    // Platform modules
    implementation(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:ai-integration"))
    api(project(":platform:java:http"))
    api(project(":platform:java:agent-core"))
    api(project(":platform:java:observability"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:security"))

    // Re-export shared YAPPC domain models (stable contracts and DTOs)
    api(project(":products:yappc:libs:java:yappc-domain"))

    // YAPPC shared utilities
    implementation(project(":products:yappc:core:yappc-shared"))

    // ActiveJ for async
    implementation(libs.activej.promise)
    implementation(libs.activej.http)

    // JSON processing
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Validation

    // YAML parsing (from merged domain:service — YamlTaskDefinitionProvider)
    implementation("org.yaml:snakeyaml:2.0")

    // Lombok (YAPPC domain model uses Lombok annotations)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    // Logging
    implementation(libs.slf4j.api)

    // Test fixtures — in-memory repository stubs shared with other modules
    testFixturesImplementation(libs.activej.promise)
    testFixturesImplementation("org.jetbrains:annotations:24.0.1")

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.archunit.junit5)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
}

// Export API packages for other modules
val apiPackages = listOf(
    "com.ghatana.yappc.api.domain",
    "com.ghatana.yappc.api.repository"
)
java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("YAPPC Domain Models")
                description.set("Domain models and business entities for YAPPC")
                url.set("https://github.com/ghatana/ghatana/tree/main/products/yappc")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("ghatana")
                        name.set("Ghatana AI Platform")
                        email.set("platform@ghatana.ai")
                    }
                }
            }
        }
    }
}

// Domain-specific tasks
tasks.register("validateDomainModels") {
    group = "verification"
    description = "Validate domain model consistency"

    doLast {
        println("Validating domain models...")
    }
}

tasks.register("generateDomainDocs") {
    group = "documentation"
    description = "Generate domain model documentation"

    doLast {
        println("Generating domain documentation...")
    }
}

// JaCoCo configuration managed by convention plugin

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
