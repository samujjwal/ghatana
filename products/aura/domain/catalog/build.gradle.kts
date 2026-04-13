/*
 * Aura — Domain Cluster: Catalog
 * Canonical product, ingredient, shade, and source normalization rules.
 */
plugins { id("java-module") }
group = "com.ghatana.aura"
version = rootProject.version
dependencies {
    implementation(project(":products:aura:foundation"))
    implementation(project(":platform:java:core"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
}
