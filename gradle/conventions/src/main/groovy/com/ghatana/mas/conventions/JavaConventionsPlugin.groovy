package com.ghatana.mas.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

class JavaConventionsPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.plugins.apply(JavaPlugin)

        project.java {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }

        project.tasks.withType(JavaCompile).configureEach {
            options {
                encoding = 'UTF-8'
                compilerArgs.add("-parameters")
            }
        }

        project.tasks.withType(Test).configureEach {
            useJUnitPlatform()
            testLogging {
                events "passed", "skipped", "failed"
                showStandardStreams = true
            }
        }

        // Note: Hardcoded versions removed - use version catalog (libs.versions.toml)
        // Individual modules should declare dependencies as:
        //   testImplementation libs.junit.jupiter
        //   compileOnly libs.lombok
        //   annotationProcessor libs.lombok
    }
}
