plugins {
    id("java-module")
}

group = "com.ghatana.platform.shared-services"
description = "Data-Cloud Kernel Bridge - KernelExtension that registers Data-Cloud adapter into kernel context"

dependencies {
    // Kernel SPI (ports live in kernel-core; this module provides the implementation and registration)
    api(project(":platform-kernel:kernel-core"))

    // Data-Cloud SPI - the concrete client implementations are wired from here
    api(project(":products:data-cloud:spi"))

    compileOnly(libs.spotbugs.annotations)

    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform-kernel:kernel-testing"))
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
