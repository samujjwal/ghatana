plugins {
    id("java-module")
}

description = "Cross-product contract tests for AEP and YAPPC Data Cloud integrations"

val runtimeInteractionTest by sourceSets.creating
val productInteractionTest by sourceSets.creating

productInteractionTest.compileClasspath += sourceSets.main.get().output
productInteractionTest.runtimeClasspath += sourceSets.main.get().output
runtimeInteractionTest.compileClasspath += sourceSets.main.get().output
runtimeInteractionTest.runtimeClasspath += sourceSets.main.get().output

dependencies {
    implementation(project(":platform-kernel:kernel-core"))
}

dependencies {
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":products:data-cloud:planes:action:orchestrator"))
    testImplementation(project(":products:data-cloud:planes:shared-spi"))
    testImplementation(project(":products:yappc:infrastructure:datacloud"))
    testImplementation(project(":products:yappc:libs:java:yappc-domain"))
    testImplementation(project(":products:yappc:core:yappc-services"))
    testImplementation(project(":platform:java:governance"))
    testImplementation(libs.jackson.databind)
    testImplementation(libs.jackson.datatype.jsr310)
    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.micrometer.core)

    add("productInteractionTestImplementation", project(":platform-kernel:kernel-core"))
    add("productInteractionTestImplementation", project(":platform-kernel:kernel-testing"))
    add("productInteractionTestImplementation", project(":platform:java:testing"))
    add("productInteractionTestImplementation", project(":products:digital-marketing:dm-kernel-bridge"))
    add("productInteractionTestImplementation", project(":products:phr"))
    add("productInteractionTestImplementation", libs.bundles.testing.core)

    add("runtimeInteractionTestImplementation", project(":platform-kernel:kernel-core"))
    add("runtimeInteractionTestImplementation", project(":platform-kernel:kernel-testing"))
    add("runtimeInteractionTestImplementation", project(":platform:java:testing"))
    add("runtimeInteractionTestImplementation", project(":products:digital-marketing:dm-kernel-bridge"))
    add("runtimeInteractionTestImplementation", project(":products:phr"))
    add("runtimeInteractionTestImplementation", libs.bundles.testing.core)
}

tasks.test {
    useJUnitPlatform()
    // ArchUnit builds a full reverse-dependency graph over all ghatana product classes,
    // which requires more heap than the convention's 768m default.
    maxHeapSize = "1536m"
}

// Ensure YAPPC services module is compiled before integration tests
tasks.compileTestJava {
    dependsOn(":products:yappc:core:yappc-services:compileJava")
}

tasks.register<Test>("productInteractionTest") {
    group = "verification"
    description = "Runs focused PHR/DMOS product interaction contract tests"
    testClassesDirs = productInteractionTest.output.classesDirs
    classpath = productInteractionTest.runtimeClasspath
    useJUnitPlatform()
}

tasks.register<Test>("runtimeInteractionTest") {
    group = "verification"
    description = "Runs kernel interaction runtime integration tests"
    testClassesDirs = runtimeInteractionTest.output.classesDirs
    classpath = runtimeInteractionTest.runtimeClasspath
    useJUnitPlatform()
}

tasks.register("interactionReleaseGate") {
    group = "verification"
    description = "Runs product and runtime interaction suites required for release gating"
    dependsOn("productInteractionTest", "runtimeInteractionTest")
}
