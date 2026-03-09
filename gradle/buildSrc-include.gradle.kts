// Shared buildSrc logic inclusion
// This file allows product-level settings to reference root buildSrc

// Apply plugin repositories
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}
