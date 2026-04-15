package com.ghatana.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class JavaModuleConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project.pluginManager) {
            apply("java-library")
            apply("idea")
            apply("jacoco")
            apply("checkstyle")
            apply("pmd")
            apply("com.diffplug.spotless")
        }

        ConventionSupport.configureJavaExtension(project, alwaysSourcesJar = false)
        ConventionSupport.configureJavaCompilation(project)
        ConventionSupport.configureTests(project, aggressiveJvmTuning = true)
        ConventionSupport.configureJacoco(project, finalizedByTests = true)
        ConventionSupport.configureCheckstyle(project, includeSuppressions = true)
        ConventionSupport.configurePmd(project, consoleOutput = true)
        ConventionSupport.configureSpotless(project)
        ConventionSupport.configureJarManifest(project)
        ConventionSupport.configureSharedDependencyGuard(project)

        project.dependencies.apply {
            ConventionSupport.addLombok(this, includeTestProcessors = true)
            ConventionSupport.addStandardTestDependencies(this, includeMockito = true, includeLauncher = true)
        }
    }
}