plugins {
    id("java-module")
}

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
