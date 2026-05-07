plugins {
    id("java-module")
}

group = "com.ghatana.platform.shared-services"
description = "AEP Kernel Bridge - KernelExtension that registers AEP adapter into kernel context"

dependencies {
    // Kernel SPI (ports live in kernel-core; this module provides the implementation and registration)
    api(project(":platform-kernel:kernel-core"))

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
