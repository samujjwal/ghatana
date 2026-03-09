package com.ghatana.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Checkstyle

class CheckstyleConventionsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.withPlugin('java') {
            project.pluginManager.apply('checkstyle')

            project.checkstyle {
                toolVersion = project.libs.versions.checkstyle.get()
                configFile = project.rootProject.file('config/checkstyle/checkstyle.xml')
                configProperties = [
                    'checkstyle.cache.file': "${project.buildDir}/checkstyle.cache",
                    'checkstyle.suppressions.file': project.rootProject.file('config/checkstyle/suppressions.xml')
                ]
                ignoreFailures = false
                showViolations = true
            }

            project.tasks.withType(Checkstyle) {
                reports {
                    xml.required = true
                    html.required = true
                }
            }
        }
    }
}
