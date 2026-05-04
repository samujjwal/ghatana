plugins {
    id("java-module")
}

apply(from = "../gradle/dmos-quality-gates.gradle.kts")

group = "com.ghatana.digitalmarketing"
description = "DMOS API — HTTP servlet layer exposing DMOS application services"

dependencies {
    api(project(":products:digital-marketing:dm-core-contracts"))
    api(project(":products:digital-marketing:dm-application"))
    api(project(":platform:java:http"))
    api(project(":platform:java:core"))

    // Persistence and kernel bridge for composition root
    implementation(project(":products:digital-marketing:dm-persistence"))
    implementation(project(":products:digital-marketing:dm-kernel-bridge"))
    implementation(project(":products:digital-marketing:dm-infra"))

    // Platform plugins for production-grade implementations
    implementation(project(":platform-plugins:plugin-consent"))
    implementation(project(":platform-plugins:plugin-human-approval"))
    implementation(project(":platform-plugins:plugin-audit-trail"))
    implementation(project(":platform-plugins:plugin-risk-management"))
    implementation(project(":platform-plugins:plugin-notification"))
    implementation(project(":platform-plugins:plugin-compliance"))

    // PostgreSQL JDBC
    implementation(libs.postgresql)

    compileOnly(libs.spotbugs.annotations)
    implementation(libs.activej.promise)
    implementation(libs.jackson.databind)

    testImplementation(libs.bundles.testing.core)
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
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.82".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.79".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
