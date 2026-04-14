plugins {
    id("java-module")
}

group = "com.ghatana.datacloud"
version = rootProject.version

description = "Data-Cloud SPI - Shared interfaces and types for cross-product integration"


dependencies {
    // Minimal dependencies - only what SPI types need
    api(project(":products:data-cloud:platform-entity"))
    api(project(":platform-kernel:kernel-plugin"))
    api(project(":platform:java:core"))       // Offset type
    api(project(":platform:java:domain"))     // Platform event-store contracts for migration bridge
    api(libs.activej.promise)                  // Promise<T> in EventLogStore

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    compileOnly(libs.spotbugs.annotations)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
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
            limit {
                counter = "INSTRUCTION"
                value = "COVEREDRATIO"
                minimum = "0.08".toBigDecimal()  // P0.3.1: raise to 0.80 after full test suite added
            }
        }
    }
}

tasks.named("check") {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
