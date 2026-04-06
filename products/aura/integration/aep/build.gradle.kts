/*
 * Aura — Integration Cluster: AEP
 *
 * AEP event bridge for Aura.
 * Publishes user interaction events, recommendation impressions, and task lifecycle
 * events to the AEP event bus. Subscribes to cross-product signals (e.g. purchase,
 * review, style actions from other Ghatana products).
 */
plugins { id("java-library") }
group = "com.ghatana.aura"
version = rootProject.version
dependencies {
    implementation(project(":products:aura:foundation"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:contracts"))
    // Kernel — event-store for AEP event publishing and schema registry validation
    implementation(project(":platform-kernel:kernel-core"))
    implementation(libs.activej.promise)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
