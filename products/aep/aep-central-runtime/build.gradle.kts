/*
 * AEP Platform Central Runtime Module - Build Configuration
 *
 * Central catalog and runtime services extracted from the retired platform-bundle.
 * Contains the narrow AEP-owned runtime surface consumed by other products.
 *
 * @doc.type module
 * @doc.purpose Central AEP catalog and runtime services
 * @doc.layer product
 * @doc.pattern Module
 */

plugins {
    id("com.ghatana.java-conventions")
    `java-library`
}

dependencies {
    api(project(":products:aep:aep-engine"))  // Uses AepCentralCatalogService
    api(project(":platform:java:agent-core"))
    api(project(":products:aep:aep-operator-contracts"))
    implementation(project(":products:data-cloud:agent-registry"))

    api(libs.activej.promise)

    implementation(libs.slf4j.api)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform:java:testing"))
}

tasks.test {
    useJUnitPlatform()
    maxParallelForks = 4
}