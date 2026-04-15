plugins {
    id("java-application")
}

description = "EventCloud tailing service for real-time feature ingestion (migrated from shared-services per ADR-013)"

dependencies {
    // Platform libraries (updated paths)

    // AI model inference and feature transformation pipeline
    implementation(project(":platform:java:ai-integration"))

    // EventLogStore SPI — required to subscribe to platform event streams
    implementation(project(":products:data-cloud:spi"))

    // WarmTierEventLogStore is used for the postgres ingest mode (postgres backend).
    // NOTE (FINDING-DC-M2): This transitive dependency on platform-launcher brings in
    // heavyweight storage plugins (Kafka, S3, Redis, etc.) that are not needed here.
    // Mitigation: Once the platform-launcher split (DC-H2 Phase 2) is complete and
    // WarmTierEventLogStore is extracted to a dedicated module, this dep should be
    // replaced with the lighter-weight extracted module.
    implementation(project(":products:data-cloud:platform-launcher"))

    // Metrics, tracing, and structured logging
    implementation(project(":platform:java:observability"))

    // Core utilities: EventloopTestBase, ConfigLoader, etc.
    implementation(project(":platform:java:core"))

    // Shared domain types (TenantContext, etc.)
    implementation(project(":platform:java:domain"))

    // Connection pool for PostgreSQL (production FeatureStoreService)
    implementation(libs.hikaricp)

    // ActiveJ runtime
    implementation(libs.bundles.activej.core)
    implementation(libs.activej.promise)
    implementation(libs.activej.datastream)
    implementation(libs.activej.inject)

    // Jackson for JSON
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    // P3.3.1: YAML-configurable feature transform spec
    implementation(libs.jackson.dataformat.yaml)

    // Logging (Log4j2 + SLF4J)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j.impl)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

application {
    mainClass.set("com.ghatana.services.featurestore.FeatureStoreIngestLauncher")
}

tasks.test {
    // useJUnitPlatform() already applied by java-application; keep finalizedBy for unconditional JaCoCo
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude(
                "**/proto/**",
                "**/*Proto.class",
                "**/generated/**"
            )
        }
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                // Config + exception hierarchy fully tested; raised from 0.050 (2026-04-02)
                minimum = "0.150".toBigDecimal()
            }
        }
        rule {
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.100".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(
        sourceSets.main.get().output.asFileTree.matching {
            exclude(
                "**/proto/**",
                "**/*Proto.class",
                "**/generated/**",
                // Exclude launcher bootstrapping (DI glue code)
                "**/*Launcher.class"
            )
        }
    )
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
