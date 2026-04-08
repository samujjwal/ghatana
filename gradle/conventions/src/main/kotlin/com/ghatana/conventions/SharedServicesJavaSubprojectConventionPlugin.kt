package com.ghatana.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test

/**
 * Convention plugin that replaces the subprojects block in shared-services/build.gradle.kts.
 * This plugin applies common Java configuration to shared-services subprojects.
 */
class SharedServicesJavaSubprojectConventionPlugin : Plugin<Project> {
    
    override fun apply(project: Project) {
        // Set group and version
        project.group = project.rootProject.group
        project.version = project.rootProject.version
        
        // Configure Java toolchain for java plugin
        project.plugins.withId("java") {
            project.extensions.configure(JavaPluginExtension::class.java) {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(21))
                }
            }
        }
        
        // Configure Java toolchain for java-library plugin
        project.plugins.withId("java-library") {
            project.extensions.configure(JavaPluginExtension::class.java) {
                toolchain {
                    languageVersion.set(JavaLanguageVersion.of(21))
                }
            }
        }
        
        // Configure test tasks
        project.tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }
    }
}
