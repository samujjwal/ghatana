plugins {
    id("java-module")
}

apply(from = "../gradle/dmos-quality-gates.gradle.kts")

group = "com.ghatana.digitalmarketing"
description = "DMOS Google Ads HTTP connector adapters — production-grade OkHttp + Jackson implementations"

dependencies {
    api(project(":products:digital-marketing:dm-application"))

    implementation(libs.activej.promise)
    implementation(libs.okhttp)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    compileOnly(libs.spotbugs.annotations)

    testImplementation(libs.bundles.testing.core)
    testImplementation(libs.mockwebserver)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
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
