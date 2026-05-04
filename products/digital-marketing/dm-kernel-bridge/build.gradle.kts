plugins {
    id("java-module")
}

apply(from = "../gradle/dmos-quality-gates.gradle.kts")

group = "com.ghatana.digitalmarketing"
description = "DMOS Kernel Bridge — production AbstractKernelBridge extension for DMOS operations"

dependencies {
    api(project(":products:digital-marketing:dm-core-contracts"))
    api(project(":products:digital-marketing:dm-domain-packs"))
    api(project(":platform-kernel:kernel-core"))
    api(project(":platform-kernel:kernel-plugin"))
    api(project(":platform-plugins:plugin-compliance"))
    api(project(":platform-plugins:plugin-consent"))
    api(project(":platform-plugins:plugin-human-approval"))
    api(project(":platform-plugins:plugin-risk-management"))
    api(project(":platform-plugins:plugin-audit-trail"))
    api(project(":platform-plugins:plugin-notification"))

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
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit { counter = "LINE"; value = "COVEREDRATIO"; minimum = "0.95".toBigDecimal() }
            limit { counter = "BRANCH"; value = "COVEREDRATIO"; minimum = "0.90".toBigDecimal() }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
