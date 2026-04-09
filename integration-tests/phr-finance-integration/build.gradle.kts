plugins {
    id("java-library")
}

description = "Cross-domain integration tests for PHR and Finance workflows"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    testImplementation(project(":platform-kernel:kernel-core"))
    testImplementation(project(":platform-plugins:plugin-billing-ledger"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":products:phr"))
    testImplementation(project(":products:finance"))

    testImplementation(libs.bundles.testing.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Fix JaCoCo task dependency
tasks.named("jacocoTestReport") {
    dependsOn("compileJava")
}
