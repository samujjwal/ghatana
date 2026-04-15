package com.ghatana.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class JavaApplicationConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project.pluginManager) {
            apply("java-library")
            apply("idea")
            apply("application")
            apply("jacoco")
            apply("checkstyle")
            apply("pmd")
            apply("com.diffplug.spotless")
        }

        ConventionSupport.configureJavaExtension(project, alwaysSourcesJar = true)
        ConventionSupport.configureJavaCompilation(project)
        ConventionSupport.configureTests(project, aggressiveJvmTuning = false)
        ConventionSupport.configureJacoco(project, finalizedByTests = false)
        ConventionSupport.configureCheckstyle(project, includeSuppressions = false)
        ConventionSupport.configurePmd(project, consoleOutput = true)
        ConventionSupport.configureSpotless(project)
        ConventionSupport.configureJarManifest(project)
        ConventionSupport.configureApplication(project)

        project.dependencies.apply {
            ConventionSupport.addLombok(this, includeTestProcessors = false)
            ConventionSupport.addStandardTestDependencies(this, includeMockito = false, includeLauncher = false)
        }
    }
}