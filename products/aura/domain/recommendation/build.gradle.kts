/*
 * Aura -- Domain Cluster: Recommendation
 * Candidate generation, scoring, confidence, and ranking logic.
 */
plugins { id("java-module") }
group = "com.ghatana.aura"
version = rootProject.version
dependencies {
    implementation(project(":products:aura:foundation"))
    implementation(project(":products:aura:domain:profile"))
    implementation(project(":products:aura:domain:catalog"))
    implementation(project(":platform:java:core"))
    // Kernel — event-store for cross-process events, audit-trail for decision audit
    implementation(project(":platform-kernel:kernel-core"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
