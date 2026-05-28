plugins {
    id("java-module")
}

group = "com.ghatana.platform.shared-services"
description = "YAPPC Kernel Bridge - KernelExtension that exposes narrow YAPPC evidence ports into kernel context"

dependencies {
    // Kernel SPI (KernelExtension lifecycle and context registration)
    api(project(":platform-kernel:kernel-core"))

    // YAPPC shared module stays internal to the compatibility adapter.
    implementation(project(":products:yappc:core:yappc-shared"))

    compileOnly(libs.spotbugs.annotations)

    testImplementation(libs.bundles.testing.core)
    testImplementation(project(":platform-kernel:kernel-testing"))
    testImplementation(project(":platform:java:testing"))
    testImplementation(project(":platform:java:governance"))
    testImplementation(project(":products:data-cloud:planes:shared-spi"))
    testImplementation(project(":products:yappc:infrastructure:datacloud"))
    testImplementation(project(":products:yappc:libs:java:yappc-domain"))
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
