/*
 * Aura — Domain Cluster: Explainability
 * Reason codes, evidence assembly, and transparency output contracts.
 */
plugins { id("java-library") }
group = "com.ghatana.aura"
version = rootProject.version
dependencies {
    implementation(project(":products:aura:foundation"))
    implementation(project(":platform:java:core"))
    // Kernel — audit-trail for immutable decision audit (required by G4 release gate)
    implementation(project(":platform-kernel:kernel-core"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
