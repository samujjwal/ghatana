/*
 * Aura — Platform Cluster: Config
 *
 * Runtime configuration for the Aura platform:
 * feature flags, model version pinning, A/B test allocation,
 * and per-tenant behavioral overrides.
 */
plugins { id("java-module") }
group = "com.ghatana.aura"
version = rootProject.version
dependencies {
    implementation(project(":products:aura:foundation"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:config"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
