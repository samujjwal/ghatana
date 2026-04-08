package com.ghatana.conventions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.jacoco.JacocoPluginExtension
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.plugins.JavaPluginExtension
import com.github.spotbugs.snom.SpotBugsExtension
import com.github.spotbugs.snom.SpotBugsTask
import com.github.spotbugs.snom.Effort
import com.github.spotbugs.snom.Confidence
import org.gradle.api.plugins.quality.Javadoc

/**
 * Convention plugin that replaces the subprojects block in products/yappc/build.gradle.kts.
 * This plugin applies YAPPC-specific Java configuration to all subprojects.
 */
class YappcJavaSubprojectConventionPlugin : Plugin<Project> {
    
    override fun apply(project: Project) {
        // Skip non-Java projects
        val hasJavaSource = project.file("${project.projectDir}/src/main/java").exists() ||
                          project.file("${project.projectDir}/src/main/kotlin").exists() ||
                          project.file("${project.projectDir}/src/test/java").exists()
        
        if (!hasJavaSource) {
            return
        }
        
        // Apply plugins
        project.plugins.apply(JavaLibraryPlugin::class.java)
        
        // Set group and version
        project.group = "com.ghatana.products.yappc"
        project.version = project.rootProject.version
        
        // Repository configuration is centralized in settings.gradle.kts
        
        // Configure Java extension
        project.extensions.configure(JavaPluginExtension::class.java) {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
            withJavadocJar()
            withSourcesJar()
        }
        
        // Apply JaCoCo plugin
        project.plugins.apply("jacoco")
        
        // Configure JaCoCo
        project.extensions.configure(JacocoPluginExtension::class.java) {
            toolVersion = project.providers.gradleProperty("libs.versions.jacoco").get()
        }
        
        // Configure test tasks
        project.tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
            finalizedBy(project.tasks.named("jacocoTestReport"))
        }
        
        // Configure JaCoCo test report
        project.tasks.named("jacocoTestReport", org.gradle.testing.jacoco.tasks.JacocoReport::class.java).configure {
            dependsOn(project.tasks.named("test"))
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
            classDirectories.setFrom(
                project.fileTree(project.layout.buildDirectory.dir("classes/java/main")) {
                    exclude(
                        "**/package-info.class",
                        "**/*Config.class",
                        "**/*Module.class",
                        "**/*Launcher.class",
                        "**/*Bootstrapper.class",
                        "**/generated/**"
                    )
                }
            )
        }
        
        // Configure JaCoCo coverage verification
        project.tasks.named("jacocoTestCoverageVerification", org.gradle.testing.jacoco.tasks.JacocoCoverageVerification::class.java).configure {
            val lowCoverageModules = setOf(
                ":products:yappc:core:yappc-agents",
                ":products:yappc:core:agents:code-specialists",
                ":products:yappc:core:agents:testing-specialists",
                ":products:yappc:core:scaffold:api",
                ":products:yappc:core:services-platform",
                ":products:yappc:core:yappc-domain-impl",
                ":products:yappc:infrastructure:datacloud"
            )
            val minThreshold = if (project.path in lowCoverageModules) "0.00" else "0.15"
            violationRules {
                rule {
                    limit {
                        counter.set("BRANCH")
                        value.set("COVEREDRATIO")
                        minimum.set(minThreshold.toBigDecimal())
                    }
                    limit {
                        counter.set("LINE")
                        value.set("COVEREDRATIO")
                        minimum.set(minThreshold.toBigDecimal())
                    }
                }
            }
            classDirectories.setFrom(
                project.fileTree(project.layout.buildDirectory.dir("classes/java/main")) {
                    exclude(
                        "**/package-info.class",
                        "**/*Config.class",
                        "**/*Module.class",
                        "**/*Launcher.class",
                        "**/*Bootstrapper.class",
                        "**/generated/**"
                    )
                }
            )
        }
        
        project.tasks.named("check").configure {
            dependsOn(project.tasks.named("jacocoTestCoverageVerification"))
        }
        
        // Configure Java compilation
        project.tasks.withType(JavaCompile::class.java).configureEach {
            sourceCompatibility = "21"
            targetCompatibility = "21"
            options.encoding = "UTF-8"
        }
        
        // Configure Javadoc
        project.tasks.withType(Javadoc::class.java).configureEach {
            (options as org.gradle.external.documentation.dsl.JavadocOptions).apply {
                encoding = "UTF-8"
                addStringOption("Xdoclint:all,-missing", "-quiet")
                tags(
                    "doc.type:a:Type:",
                    "doc.purpose:a:Purpose:",
                    "doc.layer:a:Layer:",
                    "doc.pattern:a:Pattern:",
                    "doc.gaa.lifecycle:a:GAA Lifecycle:",
                    "doc.gaa.memory:a:GAA Memory:",
                    "doc.language:a:Language:",
                    "doc.tool:a:Tool:",
                    "doc.tools:a:Tools:",
                    "doc.status:a:Status:",
                    "doc.promise:a:Promise:"
                )
            }
        }
        
        project.tasks.named("check").configure {
            dependsOn(project.tasks.named("javadoc"))
        }
        
        // SpotBugs configuration for specific projects
        val spotbugsProjects = setOf(
            ":products:yappc:backend:api",
            ":products:yappc:core:services-platform",
            ":products:yappc:core:services-lifecycle"
        )
        
        if (project.path in spotbugsProjects) {
            project.plugins.apply("com.github.spotbugs")
            
            project.extensions.configure(SpotBugsExtension::class.java) {
                effort.set(Effort.MAX)
                reportLevel.set(Confidence.LOW)
                excludeFilter.set(project.rootProject.file("config/spotbugs/spotbugs-exclude.xml"))
            }
            
            project.tasks.withType(SpotBugsTask::class.java).configureEach {
                reports {
                    create("html") {
                        required.set(true)
                    }
                    create("xml") {
                        required.set(false)
                    }
                }
            }
            
            project.tasks.matching { it.name == "check" }.configureEach {
                dependsOn("spotbugsMain")
            }
        }
        
        // Dependency governance constraints
        project.configurations.all {
            exclude(group = "dev.langchain4j", module = "langchain4j")
            exclude(group = "dev.langchain4j", module = "langchain4j-open-ai")
            exclude(group = "dev.langchain4j", module = "langchain4j-anthropic")
            exclude(group = "io.projectreactor", module = "reactor-core")
            exclude(group = "io.reactivex.rxjava3", module = "rxjava")
        }
    }
}
