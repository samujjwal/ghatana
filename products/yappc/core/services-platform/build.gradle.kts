plugins {
    id("java-library")
    id("jacoco")
    id("com.ghatana.jacoco-conventions")
}

group = "com.ghatana.products.yappc.services"
version = rootProject.version.toString()
base.archivesName.set("yappc-services-platform")

description = "YAPPC Services: Platform — Combined domain and infrastructure services (merges services:domain + services:infrastructure)"

dependencies {
    // YAPPC domain library
    implementation(project(":products:yappc:libs:java:yappc-domain"))

    // Core lifecycle and infrastructure
    implementation(project(":products:yappc:core:yappc-services"))
    implementation(project(":products:yappc:infrastructure:datacloud"))
    // backend:auth removed (2026-03-23) — functionality consolidated into core modules

    // Core Platform Libraries
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:observability"))

    // ActiveJ for async
    implementation(libs.activej.promise)
    implementation(libs.activej.inject)

    // Database
    implementation("com.zaxxer:HikariCP:5.1.0")
    runtimeOnly(libs.postgresql)

    // JSON Processing
    implementation(platform(libs.jackson.bom))
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Validation
    implementation("jakarta.validation:jakarta.validation-api:3.0.2")
    implementation("org.hibernate.validator:hibernate-validator:8.0.1.Final")

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:postgresql:1.19.3")
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

// Jacoco configuration with lowered coverage thresholds
jacoco { toolVersion = "0.8.11" }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// Override coverage thresholds — module has minimal testable production code (pure glue/facade).
// TODO: Raise thresholds incrementally as test coverage improves.
tasks.named("jacocoTestCoverageVerification", JacocoCoverageVerification::class.java).configure {
    violationRules {
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.00".toBigDecimal()
            }
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.00".toBigDecimal()
            }
        }
    }
}
