plugins {
    id("java-module")
}

apply(from = "../gradle/dmos-quality-gates.gradle.kts")

group = "com.ghatana.digitalmarketing"
description = "DMOS Application — application service layer implementing campaign, workspace, and audience use cases"

dependencies {
    api(project(":products:digital-marketing:dm-core-contracts"))
    api(project(":products:digital-marketing:dm-domain"))
    api(project(":products:digital-marketing:dm-domain-packs"))
    api(project(":products:digital-marketing:dm-kernel-bridge"))
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform:java:core"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:security"))
    api(project(":platform-plugins:plugin-compliance"))

    compileOnly(libs.spotbugs.annotations)
    implementation(libs.activej.promise)

    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform-kernel:kernel-testing"))
    testImplementation(project(":platform:java:testing"))
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

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            // DMOS-P2-001: Raise coverage thresholds gradually
            // Critical application logic requires high coverage
            // In CI, enforce 100% coverage on changed files via diff-based coverage tools
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.97".toBigDecimal() // Raised from 0.95
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.92".toBigDecimal() // Raised from 0.82
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
