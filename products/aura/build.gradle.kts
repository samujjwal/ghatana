/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 *
 * Aura — Personal AI Intelligence Platform
 * Root module aggregating all Aura domain clusters.
 */

plugins {
    `java-library`
}

description = "Aura — Personal AI Intelligence Platform"

subprojects {
    group = "com.ghatana.aura"
    version = rootProject.version
}
