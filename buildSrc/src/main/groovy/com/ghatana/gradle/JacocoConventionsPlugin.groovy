package com.ghatana.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class JacocoConventionsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.withPlugin('java') {
            project.pluginManager.apply('jacoco')

            project.jacoco {
                toolVersion = project.libs.versions.jacoco.get()
            }

            project.tasks.withType(org.gradle.testing.jacoco.tasks.JacocoReport).configureEach { report ->
                report.reports {
                    xml.required = true
                    html.required = true
                }
            }

            project.test {
                useJUnitPlatform()
                finalizedBy project.tasks.named('jacocoTestReport')
            }

            project.tasks.named('jacocoTestReport') {
                dependsOn project.tasks.named('test')
                reports {
                    xml.required = true
                    html.required = true
                }

                // Exclude generated code by default.
                // Avoid referencing the task's classDirectories provider (it can cause nested resolution or recursion).
                // Instead use the project's main output classes directories and apply excludes immediately.
                def excludes = ['**/generated/**', '**/test/**', '**/proto/**']
                try {
                    def classesDirs = project.sourceSets.main.output.classesDirs.files
                    def filtered = classesDirs.collect { dir -> project.fileTree(dir: dir, exclude: excludes) }
                    classDirectories.setFrom(project.files(filtered))
                } catch (Exception e) {
                    project.logger.debug('Could not resolve sourceSets.main.output.classesDirs when configuring jacocoTestReport: ' + e.message)
                }
            }
        }
    }
}
