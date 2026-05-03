plugins {
    id("java-module")
}

apply(from = "../gradle/dmos-quality-gates.gradle.kts")

group = "com.ghatana.digitalmarketing"
description = "DMOS Persistence — PostgreSQL repository adapters for production deployment"

dependencies {
    api(project(":products:digital-marketing:dm-application"))
    api(project(":products:digital-marketing:dm-domain"))
    api(project(":products:digital-marketing:dm-core-contracts"))
    api(project(":platform:java:database"))

    compileOnly(libs.spotbugs.annotations)
    implementation(libs.activej.promise)
    implementation(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgresql)

    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.postgresql)
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
                minimum = "0.85".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.75".toBigDecimal()
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
    // Migration validation is enforced via MigrationValidationIT
}
