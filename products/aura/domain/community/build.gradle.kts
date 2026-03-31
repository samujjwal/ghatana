/*
 * Aura — Domain Cluster: Community
 * Review analysis, sentiment, and twin-user signals.
 */
plugins { id("java-library") }
group = "com.ghatana.aura"
version = rootProject.version
dependencies {
    implementation(project(":products:aura:foundation"))
    implementation(project(":platform:java:core"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
