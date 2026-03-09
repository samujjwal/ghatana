package com.ghatana.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class SpotlessConventionsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.pluginManager.withPlugin('com.diffplug.spotless') {
            project.spotless {
                // Java formatter is configured centrally; do not duplicate here

                        format 'misc', {
                            // Exclude .md files to avoid following broken symbolic links
                            target '*.gradle', '.gitignore'
                            trimTrailingWhitespace()
                            indentWithSpaces()
                            endWithNewline()
                        }

                format 'proto', {
                    target '**/*.proto'
                    trimTrailingWhitespace()
                    indentWithSpaces(2)
                    endWithNewline()
                    licenseHeaderFile project.rootProject.file('config/spotless/license-header.proto'), '//'
                }
            }
        }
    }
}
