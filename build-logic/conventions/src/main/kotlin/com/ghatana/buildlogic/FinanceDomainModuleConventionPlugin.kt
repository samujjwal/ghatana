package com.ghatana.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

class FinanceDomainModuleConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("java-module")
        project.dependencies.add(
            "api",
            project.dependencies.project(mapOf("path" to ":products:finance:platform-sdk"))
        )
    }
}