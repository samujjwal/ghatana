/**
 * Platform Plugins Module
 *
 * Aggregates all storage/compute plugin implementations:
 * - Kafka EventLog Store
 * - Redis hot-tier
 * - S3/Iceberg cold-tier
 * - Knowledge Graph (Gremlin/TinkerGraph)
 * - Vector Search (LangChain4j)
 * - ClickHouse/OpenSearch Analytics
 *
 * Extracted plugin runtime surface for Data Cloud.
 *
 * Coverage is enforced on the pure, in-memory plugin logic that currently has
 * deterministic unit-test harnesses. External adapter packages (Kafka, Trino,
 * S3, Iceberg, Redis, enterprise workflows) stay under compile/spotbugs/javadoc
 * enforcement until dedicated integration-test coverage lands.
 */
plugins {
    id("java-library")
    id("jacoco")
    alias(libs.plugins.spotbugs)
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud Storage Plugins — extracted from platform-launcher (Phase 1)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    api(project(":products:data-cloud:spi"))
    api(project(":products:data-cloud:platform-entity"))
    api(project(":products:data-cloud:platform-event"))
    api(project(":products:data-cloud:platform-config"))

    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:plugin"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:config"))

    api(libs.activej.promise)
    api(libs.activej.eventloop)
    api(platform(libs.jackson.bom))
    api(libs.jackson.databind)
    api(libs.micrometer.core)

    // Plugin-specific storage dependencies
    implementation(libs.kafka.clients)
    implementation(libs.jedis)
    implementation(libs.lettuce.core)
    implementation(libs.caffeine)
    implementation(platform(libs.aws.sdk.bom))
    implementation(libs.aws.s3)
    implementation(libs.hadoop.common)
    implementation(libs.iceberg.core)
    implementation(libs.iceberg.data)
    implementation(libs.iceberg.parquet)
    implementation(libs.parquet.avro)
    implementation(libs.jgrapht.core)
    implementation(libs.hikaricp)
    implementation(libs.rocksdb)
    implementation(libs.clickhouse.client)
    implementation(libs.disruptor)
    runtimeOnly(libs.clickhouse.http.client)
    implementation(libs.opensearch.java)
    implementation(libs.opensearch.rest.client)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.spotbugs.annotations)
    compileOnly(libs.trino.plugin.toolkit)
    compileOnly(libs.trino.spi)

    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.archunit.junit5)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
    environment("DOCKER_HOST", "unix:///var/run/docker.sock")
    environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    environment("TESTCONTAINERS_HOST_OVERRIDE", "host.docker.internal")
}

jacoco {
    toolVersion = "0.8.11"
}

val jacocoCoveredClasses = sourceSets.main.get().output.asFileTree.matching {
    include(
        "**/validation/**",
        "**/vector/VectorRecord.class"
    )
    exclude(
        "**/*\$Builder.class",
        "**/package-info.class",
        "**/generated/**"
    )
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
    classDirectories.setFrom(jacocoCoveredClasses)
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.200".toBigDecimal()
            }
        }
    }
    classDirectories.setFrom(jacocoCoveredClasses)
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

spotbugs {
    toolVersion.set("4.8.6")
    ignoreFailures.set(false)
    effort.set(com.github.spotbugs.snom.Effort.MAX)
    reportLevel.set(com.github.spotbugs.snom.Confidence.MEDIUM)
    excludeFilter.set(rootProject.file("config/spotbugs/spotbugs-exclude.xml"))
}

tasks.withType<com.github.spotbugs.snom.SpotBugsTask>().configureEach {
    reports.create("html") { required.set(true) }
    reports.create("xml") { required.set(true) }
}
