package com.ghatana.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

class SpotBugsConventionsPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        // DISABLED: SpotBugs conventions plugin is temporarily disabled because
        // its afterEvaluate usage conflicts with io.freefair.lombok and me.champeau.jmh plugins.
        // To re-enable, uncomment the code below and fix the afterEvaluate conflict.
        if (true) {
            return
        }
        project.pluginManager.withPlugin('java') {
            project.pluginManager.apply('com.github.spotbugs')

            project.afterEvaluate {
                // Opt-in gate: avoid running SpotBugs by default because older SpotBugs/ASM
                // implementations may not support newer class-file versions. To enable, set
                // -PenableSpotBugs=true on the Gradle command line (CI can opt-in).
                boolean enableSpotBugs = false
                try {
                    enableSpotBugs = project.hasProperty('enableSpotBugs') && project.property('enableSpotBugs')?.toString() == 'true'
                } catch (Exception ignored) {
                }

                if (!enableSpotBugs) {
                    project.logger.lifecycle('SpotBugs disabled for project (set -PenableSpotBugs=true to enable)')
                    // Ensure any spotbugs tasks that might be created are disabled
                    project.tasks.configureEach { t ->
                        if (t.name.toLowerCase().startsWith('spotbugs')) {
                            t.enabled = false
                        }
                    }
                    return
                }
                // Detect Gradle JVM major version
                int runtimeMajor = 0
                try {
                    runtimeMajor = Runtime.version().feature() as Integer
                } catch (Exception ignored) {
                    // fallback: attempt to parse java.version
                    try {
                        def v = System.getProperty('java.version')
                        runtimeMajor = (v?.tokenize('.')?.getAt(0) ?: '0') as Integer
                    } catch (Exception e) {
                        project.logger.debug("Unable to determine Gradle JVM version: ${e.message}")
                    }
                }

                // Determine project target (toolchain) major version if available
                int targetMajor = -1
                try {
                    def javaExt = project.extensions.findByType(JavaPluginExtension)
                    if (javaExt != null && javaExt.toolchain?.languageVersion?.present) {
                        targetMajor = javaExt.toolchain.languageVersion.get().asInt()
                    } else if (project.hasProperty('sourceCompatibility')) {
                        // sourceCompatibility sometimes set as '21' or '17'
                        try {
                            targetMajor = project.sourceCompatibility.toString().replaceAll('[^0-9]', '') as Integer
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception e) {
                    project.logger.debug("Unable to determine project Java target: ${e.message}")
                }

                if (targetMajor > 0 && runtimeMajor > 0 && targetMajor > runtimeMajor) {
                    project.logger.warn("SpotBugs skipped: project target Java ${targetMajor} > Gradle JVM ${runtimeMajor}. To run analysis, run Gradle with a newer JVM or set a lower target.")
                    // Ensure SpotBugs tasks are disabled to avoid ASM unsupported class errors
                    try {
                        def spotClass = Class.forName('com.github.spotbugs.snom.SpotBugsTask')
                        project.tasks.withType(spotClass).configureEach {
                            enabled = false
                        }
                    } catch (ClassNotFoundException e) {
                        project.logger.lifecycle('SpotBugs task class not found on classpath; tasks will remain unconfigured')
                    }

                    // Still add compileOnly annotation dependency (harmless)
                    // Use explicit coordinates to avoid leaking catalog accessor objects into resolved versions
                    project.dependencies {
                        compileOnly "com.github.spotbugs:spotbugs-annotations:${'4.8.6'}"
                        testCompileOnly "com.github.spotbugs:spotbugs-annotations:${'4.8.6'}"
                    }

                    return
                }

                // Configure SpotBugs extension to report but not fail the build
                def resolvedToolVersion = '4.8.6'
                try {
                    // Prefer the VersionCatalogs API which reliably returns version strings
                    def catalogs = project.extensions.findByType(VersionCatalogsExtension)
                    if (catalogs != null) {
                        def libsCatalog = catalogs.named('libs')
                        def ver = libsCatalog.findVersion('spotbugs')
                        if (ver.present) {
                            resolvedToolVersion = ver.get().toString()
                        }
                    } else if (project.hasProperty('libs') && project.libs.versions?.spotbugs != null) {
                        // best-effort fallback: coerce to string only if it looks like a version
                        def maybe = project.libs.versions.spotbugs.toString()
                        if (maybe ==~ /[0-9]+(\.[0-9]+)*/ ) {
                            resolvedToolVersion = maybe
                        }
                    }
                } catch (Exception e) {
                    project.logger.debug("Could not read spotbugs version from catalog: ${e.message}")
                }

                project.spotbugs {
                    toolVersion = resolvedToolVersion
                    ignoreFailures = true  // do not fail the build on violations
                    showProgress = true
                    effort = 'max'
                    reportLevel = 'high'
                }

                try {
                    def spotClass = Class.forName('com.github.spotbugs.snom.SpotBugsTask')
                    project.tasks.withType(spotClass).configureEach {
                        // Ensure task-level ignoreFailures set as well
                        try {
                            it.ignoreFailures = true
                        } catch (Exception ignored) {
                        }

                        reports {
                            html.required = true
                            xml.required = false
                        }
                    }
                } catch (ClassNotFoundException e) {
                    project.logger.lifecycle('SpotBugs task class not found on classpath yet; skipping SpotBugs task report configuration')
                }

                // Add compileOnly annotation dependency using the resolved version
                project.dependencies {
                    compileOnly "com.github.spotbugs:spotbugs-annotations:${resolvedToolVersion}"
                    testCompileOnly "com.github.spotbugs:spotbugs-annotations:${resolvedToolVersion}"
                }
            }
        }
    }
}
