/*
 * Aura — Agent Cluster: Intelligence Agent
 *
 * ADAPTIVE agent implementing PersonalIntelligenceAgent using multi-armed bandits
 * and Thompson Sampling for per-user preference learning.
 *
 * Registered in AEP Central Registry as agent type: ADAPTIVE
 */
plugins { id("java-library") }
group = "com.ghatana.aura"
version = rootProject.version
dependencies {
    implementation(project(":products:aura:foundation"))
    implementation(project(":products:aura:domain:profile"))
    implementation(project(":products:aura:domain:recommendation"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:agent-core"))
    implementation(project(":platform:java:observability"))
    // Kernel — event-store for episodic memory sourcing, kernel module lifecycle
    implementation(project(":platform-kernel:kernel-core"))
    implementation(libs.activej.promise)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
