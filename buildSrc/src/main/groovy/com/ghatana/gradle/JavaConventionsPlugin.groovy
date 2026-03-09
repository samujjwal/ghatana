package com.ghatana.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.jvm.toolchain.JavaLanguageVersion

class JavaConventionsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        project.plugins.withType(JavaPlugin) {
            project.extensions.configure(JavaPluginExtension) { JavaPluginExtension javaExt ->
                if (!javaExt.toolchain.languageVersion.present) {
                    javaExt.toolchain.languageVersion.set(JavaLanguageVersion.of(21))
                }
            }

            project.tasks.withType(JavaCompile).configureEach { JavaCompile jc ->
                jc.options.encoding = 'UTF-8'
                jc.options.compilerArgs << '-parameters'
                jc.options.compilerArgs << '-Xlint:unchecked'
                jc.options.compilerArgs << '-Xlint:deprecation'
            }

            project.tasks.withType(Javadoc).configureEach { Javadoc javadoc ->
                // Allow per-project opt-out: projects that want Javadoc can set
                // -PenableJavadoc=true on the command line or set project.ext.enableJavadoc = true
                // in their build.gradle. By default we keep Javadoc disabled because
                // generated sources commonly contain malformed tags that break the
                // javadoc tool.
                boolean enableJavadoc = false
                if (project.hasProperty('enableJavadoc')) {
                    try {
                        enableJavadoc = project.property('enableJavadoc').toString().toBoolean()
                    } catch (Exception ignored) {
                        // ignore parsing errors and fall back to false
                    }
                }

                if (enableJavadoc) {
                    javadoc.options.encoding = 'UTF-8'
                    // Attempt to be lenient but keep javadoc enabled as requested by the project
                    javadoc.options.addStringOption('Xdoclint:none', '-quiet')
                    // Exclude generated sources even when Javadoc is enabled to avoid errors
                    try {
                        def mainSources = project.sourceSets.main.allJava
                        javadoc.source = mainSources.matching { pattern ->
                            pattern.exclude '**/generated/**'
                            pattern.exclude '**/build/generated/**'
                        }
                    } catch(Exception e) {
                        // If sourceSets isn't available yet or API differs, ignore and fall back
                    }
                    project.logger.lifecycle("Javadoc enabled for project ${project.path} (enableJavadoc=true). Generated sources are excluded from javadoc.")
                } else {
                    // Disable Javadoc by default for projects with generated sources
                    javadoc.options.encoding = 'UTF-8'
                    javadoc.options.addStringOption('Xdoclint:none', '-quiet')
                    try {
                        def mainSources = project.sourceSets.main.allJava
                        javadoc.source = mainSources.matching { pattern ->
                            pattern.exclude '**/generated/**'
                            pattern.exclude '**/build/generated/**'
                        }
                    } catch(Exception e) {
                        // If sourceSets isn't available yet or API differs, ignore and fall back
                    }
                    javadoc.enabled = false
                    project.logger.lifecycle("Javadoc disabled for project ${project.path} by conventions plugin; set -PenableJavadoc=true to re-enable")
                }
            }

            project.tasks.withType(Test).configureEach { Test t ->
                // JUnit Platform + tag filtering handled by IntegrationTestProfilePlugin.
                // Only set logging defaults here.
                t.testLogging.events 'passed', 'skipped', 'failed'
            }
        }
    }
}
