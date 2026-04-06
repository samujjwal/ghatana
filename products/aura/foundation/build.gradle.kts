/*
 * Aura — Foundation Cluster
 * Core domain types, base interfaces, and platform contracts.
 */
plugins { id("java-library") }
group = "com.ghatana.aura"
version = rootProject.version
dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:contracts"))
    // Kernel — observability hooks, IAM contracts, resilience-patterns
    api(project(":platform-kernel:kernel-core"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
