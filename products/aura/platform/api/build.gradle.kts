/*
 * Aura — Platform Cluster: API
 *
 * REST / GraphQL HTTP layer for the Aura personal intelligence platform.
 * Exposes consumer-facing endpoints for recommendations, knowledge graph queries,
 * and long-horizon task management.
 */
plugins { id("java-library") }
group = "com.ghatana.aura"
version = rootProject.version
dependencies {
    implementation(project(":products:aura:foundation"))
    implementation(project(":products:aura:domain:recommendation"))
    implementation(project(":products:aura:agents:intelligence-agent"))
    implementation(project(":products:aura:agents:task-agent"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:security"))
    implementation(project(":platform:java:observability"))
    implementation(libs.activej.promise)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
