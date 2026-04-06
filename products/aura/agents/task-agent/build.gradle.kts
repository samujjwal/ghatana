/*
 * Aura — Agent Cluster: Task Agent
 *
 * PLANNING agent for long-horizon task execution.
 * Decomposes multi-step user goals into executable sub-tasks,
 * tracks progress, and coordinates with specialist agents.
 *
 * Registered in AEP Central Registry as agent type: PLANNING
 */
plugins { id("java-library") }
group = "com.ghatana.aura"
version = rootProject.version
dependencies {
    implementation(project(":products:aura:foundation"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:agent-core"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:observability"))
    // Kernel — resilience-patterns (circuit breaker, timeouts) for long-horizon task stability
    implementation(project(":platform-kernel:kernel-core"))
    implementation(libs.activej.promise)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
