/**
 * Platform Plugins - Shared product-agnostic plugins
 *
 * @doc.type build-script
 * @doc.purpose Root build configuration for platform-plugins
 * @doc.layer platform
 */
plugins {
    id("java-platform")
    id("maven-publish")
}

group = "com.ghatana.plugin"
version = "1.0.0"
description = "Ghatana Platform Plugins - Shared product-agnostic plugins"

dependencies {
    constraints {
        api(project(":plugin-billing-ledger"))
        api(project(":plugin-fraud-detection"))
        api(project(":plugin-compliance"))
        api(project(":plugin-consent"))
        api(project(":plugin-risk-management"))
        api(project(":plugin-audit-trail"))
    }
}
