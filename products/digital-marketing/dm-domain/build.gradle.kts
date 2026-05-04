plugins {
    id("java-module")
}

apply(from = "../gradle/dmos-quality-gates.gradle.kts")

group = "com.ghatana.digitalmarketing"
description = "DMOS Domain — domain entities, value objects, repositories, and domain events"

dependencies {
    api(project(":products:digital-marketing:dm-core-contracts"))
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform:java:core"))

    compileOnly(libs.spotbugs.annotations)

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
            // Critical domain logic requires high coverage
            // In CI, enforce 100% coverage on changed files via diff-based coverage tools
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.93".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.83".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
