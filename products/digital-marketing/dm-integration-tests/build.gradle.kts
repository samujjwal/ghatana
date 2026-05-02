plugins {
    id("java-module")
}

apply(from = "../gradle/dmos-quality-gates.gradle.kts")

group = "com.ghatana.digitalmarketing"
description = "DMOS Integration Tests — cross-module integration tests for the digital-marketing product"

dependencies {
    testImplementation(project(":products:digital-marketing:dm-core-contracts"))
    testImplementation(project(":products:digital-marketing:dm-domain"))
    testImplementation(project(":products:digital-marketing:dm-domain-packs"))
    testImplementation(project(":products:digital-marketing:dm-kernel-bridge"))
    testImplementation(project(":products:digital-marketing:dm-application"))
    testImplementation(project(":products:digital-marketing:dm-api"))

    testImplementation(project(":platform-kernel:kernel-core"))
    testImplementation(project(":platform-kernel:kernel-testing"))
    testImplementation(project(":platform:java:http"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":platform-plugins:plugin-compliance"))
    testImplementation(project(":platform-plugins:plugin-consent"))
    testImplementation(project(":platform-plugins:plugin-human-approval"))
    testImplementation(project(":platform-plugins:plugin-audit-trail"))

    testImplementation(libs.bundles.testing.core)
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
