/*
 * AEP Event-Cloud Plugin Module - Build Configuration
 *
 * The canonical bridge between AEP and Data-Cloud. AEP routes ALL event
 * processing stores, registries, buffers, pipes, and channels through this
 * plugin. Data-Cloud is mandatory for AEP (embedded or as a service).
 *
 * @doc.type module
 * @doc.purpose Event-Cloud plugin bridging AEP to Data-Cloud
 * @doc.layer product
 * @doc.pattern Plugin, Adapter, Bridge
 */

plugins {
    id("com.ghatana.java-conventions")
    `java-library`
}

dependencies {
    // ── Data-Cloud (mandatory backing store) ────────────────────────────────
    api(project(":products:data-cloud:spi"))         // EventLogStore, EntityStore, TenantContext

    // ── AEP contracts ───────────────────────────────────────────────────────
    api(project(":products:aep:aep-operator-contracts"))  // AEP EventCloud facade, EventCloudConnector SPI

    // ── Platform infrastructure ─────────────────────────────────────────────
    api(project(":platform:java:plugin"))            // Plugin, PluginContext, PluginMetadata
    api(project(":platform:java:core"))              // Offset, TenantId
    api(project(":platform:java:domain"))            // Domain types
    implementation(project(":platform:java:observability"))  // Metrics

    // ── ActiveJ ─────────────────────────────────────────────────────────────
    api(libs.activej.promise)
    api(libs.activej.eventloop)

    // ── Jackson ─────────────────────────────────────────────────────────────
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // ── Logging ─────────────────────────────────────────────────────────────
    implementation(libs.slf4j.api)

    // ── Annotations ─────────────────────────────────────────────────────────
    compileOnly(libs.jetbrains.annotations)

    // ── Testing ─────────────────────────────────────────────────────────────
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.activej.test)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 4
}
