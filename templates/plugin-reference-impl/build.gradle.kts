/**
 * plugin-data-retention — Reference Plugin Implementation
 *
 * PURPOSE: Demonstrates the three canonical plugin patterns:
 *   Pattern 1 — Policy evaluation (deterministic, synchronous rule evaluation)
 *   Pattern 2 — Event-driven processing (async lifecycle event handler)
 *   Pattern 3 — Human approval integration (escalation to plugin-human-approval)
 *
 * Copy this template to platform-plugins/plugin-<your-name>/ and replace
 * "DataRetention" with your plugin's domain concept.
 *
 * @doc.type build-script
 * @doc.purpose Reference plugin implementation — data retention policy example
 * @doc.layer platform
 * @doc.pattern Plugin
 */
plugins {
    id("java-module")
}

group = "com.ghatana.plugin"
version = rootProject.version
description = "Reference plugin implementation: data retention policy (demonstrates all 3 plugin patterns)"

dependencies {
    // ── Kernel SPI — required for all plugins ────────────────────────────────
    api(project(":platform-kernel:kernel-core"))

    // ── Observability base — extend PluginObservability in your impl class ───
    api(project(":platform-plugins:core-observability"))

    // ── Human approval integration (Pattern 3) ───────────────────────────────
    api(project(":platform-plugins:plugin-human-approval"))

    // ── Audit trail integration (recommended for all regulated plugins) ──────
    api(project(":platform-plugins:plugin-audit-trail"))

    // ── Utilities ────────────────────────────────────────────────────────────
    implementation(project(":platform:java:core"))
    implementation(libs.bundles.logging.core)

    // ── Test dependencies ────────────────────────────────────────────────────
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.bundles.test.unit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit)
}
