plugins {
    id("java-module")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data Cloud Platform Analytics Module"


dependencies {
    api(project(":products:data-cloud:planes:shared-spi"))
    api(project(":products:data-cloud:planes:data:entity"))
    api(project(":products:data-cloud:planes:event:core"))
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:observability"))
    api(project(":platform:java:testing"))

    api(libs.activej.promise)
    api(libs.bundles.activej.core)
    api(libs.jackson.databind)

    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("com.github.jsqlparser:jsqlparser:4.9")

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(libs.h2)
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
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
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal()  // P0: Coverage gate added at 50%
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
