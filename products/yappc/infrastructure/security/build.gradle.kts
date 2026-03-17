/**
 * infrastructure:security — YAPPC Security scanning infrastructure
 *
 * Provides:
 *   - OsvScannerAdapter (OSV/dependency vulnerability scanning)
 *   - CompositeSecurityScanner (composable scanner aggregating multiple scanners)
 */
plugins {
    id("java-library")
}

group = "com.ghatana.products.yappc.infrastructure"
version = rootProject.version.toString()
base.archivesName.set("yappc-infrastructure-security")

description = "YAPPC Infrastructure: Security scanning adapters"

dependencies {
    // Depends on datacloud adapter for SecurityScanner interface and SecurityReport
    api(project(":products:yappc:infrastructure:datacloud"))

    // ActiveJ async runtime
    implementation(libs.activej.promise)

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
