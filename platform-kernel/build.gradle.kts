/**
 * Platform Kernel - Core kernel framework with plugin system
 *
 * @doc.type build-script
 * @doc.purpose Root build configuration for platform-kernel
 * @doc.layer platform
 */
plugins {
    id("java-platform")
    id("maven-publish")
}

group = "com.ghatana.kernel"
version = "1.0.0"
description = "Ghatana Platform Kernel - Core framework with plugin system"

dependencies {
    constraints {
        api(project(":kernel-core"))
        api(project(":kernel-plugin"))
        api(project(":kernel-persistence"))
        api(project(":kernel-testing"))
    }
}

