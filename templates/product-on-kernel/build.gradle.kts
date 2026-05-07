/**
 * ${MODULE_NAME} - Product-on-Kernel Module
 *
 * @doc.type build-script
 * @doc.purpose ${MODULE_PURPOSE}
 * @doc.layer product
 * @doc.pattern KernelModule
 */
plugins {
    id("java-module")
}

group = "${PRODUCT_GROUP}"
version = rootProject.version
description = "${MODULE_DESCRIPTION}"

dependencies {
    // ── Kernel core (module lifecycle, context, extensions) ──────────────────
    api(project(":platform-kernel:kernel-core"))

    // ── Kernel plugin SPI (if this module exposes or consumes plugins) ───────
    api(project(":platform-kernel:kernel-plugin"))

    // ── Platform bridges (include only the capabilities your module needs) ───
    // api(project(":products:data-cloud:extensions:kernel-bridge"))
    // api(project(":products:data-cloud:planes:action:kernel-bridge"))
    // api(project(":products:yappc:kernel-bridge"))

    // ── Platform plugins (include per regulated-domain requirements) ─────────
    // api(project(":platform-plugins:plugin-audit-trail"))
    // api(project(":platform-plugins:plugin-consent"))
    // api(project(":platform-plugins:plugin-compliance"))

    // ── Platform utilities ───────────────────────────────────────────────────
    implementation(project(":platform:java:core"))
    implementation(libs.bundles.logging.core)

    // ── Optional: Lombok, SpotBugs ───────────────────────────────────────────
    compileOnly(libs.bundles.dev.tools)
    annotationProcessor(libs.lombok)

    // ── Tests ────────────────────────────────────────────────────────────────
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
