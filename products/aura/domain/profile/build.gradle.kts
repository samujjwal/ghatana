/*
 * Aura — Domain Cluster: Profile
 * You Index — declared, inferred, and consent-aware user attribute logic.
 */
plugins { id("java-module") }
group = "com.ghatana.aura"
version = rootProject.version
dependencies {
    implementation(project(":products:aura:foundation"))
    implementation(project(":platform:java:core"))
    // Kernel — IAM for tenant isolation and consent-aware access control
    implementation(project(":platform-kernel:kernel-core"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
