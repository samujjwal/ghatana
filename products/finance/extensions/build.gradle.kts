/*
 * Finance Extensions Module - Build Configuration
 *
 * Provides finance-specific kernel extensions for authentication, compliance,
 * and other cross-cutting concerns integrated with the kernel framework.
 */

plugins {
    id("java-library")
}

dependencies {
    // Kernel framework (provides KernelExtension, KernelContext)
    implementation(project(":platform:java:kernel"))

    // Kernel authentication module
    implementation(project(":platform:java:kernel:modules:authentication"))

    // Finance domain rules
    implementation(project(":products:finance:domains:rules"))

    // Logging
    implementation(libs.slf4j.api)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform()
}
