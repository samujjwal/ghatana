/*
 * Aura — Integration Cluster: Knowledge Graph
 *
 * Graph persistence adapter for the Aura personal knowledge graph.
 * Provides the port/adapter boundary between the domain model and
 * the Neo4j (or equivalent) graph database, entity relationship management,
 * and graph query execution.
 */
plugins { id("java-library") }
group = "com.ghatana.aura"
version = rootProject.version
dependencies {
    implementation(project(":products:aura:foundation"))
    implementation(project(":products:aura:domain:profile"))
    implementation(project(":products:aura:domain:catalog"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:observability"))
    implementation(libs.activej.promise)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
