package com.ghatana.buildlogic

import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.FileTree
import org.gradle.api.plugins.JavaApplication
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.PmdExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.io.File

internal object ConventionSupport {
    fun enableJavadoc(project: Project): Boolean =
        project.findProperty("enableJavadoc")?.toString()?.toBoolean() ?: false

    fun withSourcesJar(project: Project): Boolean =
        project.findProperty("withSourcesJar")?.toString()?.toBoolean() ?: false

    fun withCoverage(project: Project): Boolean =
        System.getenv("CI") != null || project.hasProperty("coverage")

    fun sharedConfigRoot(project: Project): File =
        generateSequence(project.rootProject.rootDir) { it.parentFile }
            .firstOrNull { candidate ->
                File(candidate, "config/checkstyle/checkstyle.xml").exists() &&
                    File(candidate, "config/pmd/minimal-ruleset.xml").exists()
            }
            ?: project.rootProject.rootDir

    fun sharedConfigFile(project: Project, path: String): File = File(sharedConfigRoot(project), path)

    fun configureJavaExtension(project: Project, alwaysSourcesJar: Boolean) {
        val enableJavadoc = enableJavadoc(project)
        val publishSourcesJar = alwaysSourcesJar || withSourcesJar(project)
        project.extensions.configure(JavaPluginExtension::class.java) {
            if (!toolchain.languageVersion.isPresent) {
                toolchain.languageVersion.set(JavaLanguageVersion.of(21))
            }
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
            if (publishSourcesJar) {
                withSourcesJar()
            }
            if (enableJavadoc) {
                withJavadocJar()
            }
        }
    }

    fun configureJavaCompilation(project: Project) {
        project.tasks.withType(JavaCompile::class.java).configureEach {
            options.encoding = "UTF-8"
            options.isIncremental = true
            options.compilerArgs.addAll(
                listOf(
                    "-parameters",
                    "-Xlint:unchecked",
                    "-Xlint:deprecation",
                    "-Xlint:-processing",
                    "-Xlint:-serial"
                )
            )
        }
        project.tasks.withType(Javadoc::class.java).configureEach {
            options.encoding = "UTF-8"
            (options as? StandardJavadocDocletOptions)?.apply {
                addStringOption("Xdoclint:none", "-quiet")
                addBooleanOption("html5", true)
            }
            isEnabled = enableJavadoc(project)
        }
    }

    fun configureTests(project: Project, aggressiveJvmTuning: Boolean) {
        project.tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
                showCauses = true
                showStackTraces = true
                showStandardStreams = false
            }
            if (aggressiveJvmTuning) {
                jvmArgs("-Dapi.version=1.44", "-XX:+UseZGC", "-XX:+ZGenerational")
                maxHeapSize = "1536m"
                maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2)
                    .coerceAtLeast(1)
                    .coerceAtMost(4)
            }
        }
    }

    fun configureJacoco(project: Project, finalizedByTests: Boolean) {
        project.extensions.configure(JacocoPluginExtension::class.java) {
            toolVersion = "0.8.14"
        }
        if (finalizedByTests && withCoverage(project)) {
            project.tasks.withType(Test::class.java).configureEach {
                finalizedBy(project.tasks.named("jacocoTestReport"))
            }
        }
        project.tasks.named("jacocoTestReport", JacocoReport::class.java).configure {
            mustRunAfter(project.tasks.withType(Test::class.java))
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
        }
    }

    fun configureCheckstyle(project: Project, includeSuppressions: Boolean) {
        project.extensions.configure(CheckstyleExtension::class.java) {
            toolVersion = "10.21.4"
            configFile = sharedConfigFile(project, "config/checkstyle/checkstyle.xml")
            if (includeSuppressions) {
                configProperties = mapOf(
                    "suppressionFile" to sharedConfigFile(project, "config/checkstyle/suppressions.xml").absolutePath
                )
            }
            isIgnoreFailures = false
        }
    }

    fun configurePmd(project: Project, consoleOutput: Boolean) {
        project.extensions.configure(PmdExtension::class.java) {
            toolVersion = "7.11.0"
            if (File(sharedConfigFile(project, "config/pmd/minimal-ruleset.xml").absolutePath).exists()) {
                ruleSetFiles = project.files(sharedConfigFile(project, "config/pmd/minimal-ruleset.xml"))
            }
            ruleSets = emptyList()
            isIgnoreFailures = false
            isConsoleOutput = consoleOutput
        }
        project.tasks.withType(org.gradle.api.plugins.quality.Pmd::class.java).configureEach {
            val rulesetFile = if (name.contains("Test", ignoreCase = true)) {
                sharedConfigFile(project, "config/pmd/test-ruleset.xml")
            } else {
                sharedConfigFile(project, "config/pmd/minimal-ruleset.xml")
            }
            val sourceDirectory = if (name.contains("Test", ignoreCase = true)) {
                "src/test/java"
            } else {
                "src/main/java"
            }
            ruleSetFiles = project.files(rulesetFile)
            ruleSets = emptyList()
            source = javaSourceTree(project, sourceDirectory)
        }
    }

    private fun javaSourceTree(project: Project, sourceDirectory: String): FileTree =
        project.fileTree(sourceDirectory) {
            exclude("**/generated/**")
            exclude("**/build/generated/**")
            exclude("**/*Grpc.java")
            exclude("**/*Proto.java")
            exclude("**/*_Grpc*.java")
            exclude("**/grpc/**")
            exclude("**/proto/**")
        }

    fun configureSpotless(project: Project) {
        project.extensions.configure(SpotlessExtension::class.java) {
            java {
                target("src/**/*.java")
                removeUnusedImports()
                trimTrailingWhitespace()
                endWithNewline()
            }
            format("misc") {
                target("*.gradle", "*.gradle.kts", ".gitignore")
                targetExclude("**/node_modules/**", "**/build/**", "**/.gradle/**")
                trimTrailingWhitespace()
                endWithNewline()
            }
            isEnforceCheck = System.getenv("CI") != null || project.hasProperty("enforceFormatting")
        }
    }

    fun configureJarManifest(project: Project) {
        project.tasks.withType(Jar::class.java).configureEach {
            manifest.attributes(
                mapOf(
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to project.version,
                    "Built-JDK" to JavaVersion.current(),
                    "Created-By" to "Gradle ${project.gradle.gradleVersion}"
                )
            )
        }
        project.tasks.withType(AbstractArchiveTask::class.java).configureEach {
            duplicatesStrategy = org.gradle.api.file.DuplicatesStrategy.EXCLUDE
        }
    }

    fun addLombok(dependencies: DependencyHandler, includeTestProcessors: Boolean) {
        val lombokVersion = "1.18.36"
        dependencies.add("compileOnly", "org.projectlombok:lombok:$lombokVersion")
        dependencies.add("annotationProcessor", "org.projectlombok:lombok:$lombokVersion")
        if (includeTestProcessors) {
            dependencies.add("testCompileOnly", "org.projectlombok:lombok:$lombokVersion")
            dependencies.add("testAnnotationProcessor", "org.projectlombok:lombok:$lombokVersion")
        }
    }

    fun addStandardTestDependencies(dependencies: DependencyHandler, includeMockito: Boolean, includeLauncher: Boolean) {
        dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter:5.11.4")
        dependencies.add("testImplementation", "org.assertj:assertj-core:3.27.3")
        if (includeMockito) {
            dependencies.add("testImplementation", "org.mockito:mockito-core:5.16.1")
        }
        dependencies.add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:5.11.4")
        if (includeLauncher) {
            dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.11.4")
            dependencies.add("testRuntimeOnly", "org.apache.logging.log4j:log4j-slf4j-impl:2.24.3")
            dependencies.add("testRuntimeOnly", "org.apache.logging.log4j:log4j-core:2.24.3")
        }
    }

    fun configureSharedDependencyGuard(project: Project) {
        project.configurations.configureEach {
            resolutionStrategy.eachDependency {
                if (requested.group == project.rootProject.name && requested.name.startsWith("shared-")) {
                    throw GradleException(
                        "Dependency on deprecated module '${requested.name}' is forbidden. " +
                            "Migrate: shared:metrics -> platform:java:observability, " +
                            "shared:exception -> platform:java:core, " +
                            "shared:test-utils -> platform:java:testing"
                    )
                }
            }
        }
    }

    fun configureApplication(project: Project) {
        project.extensions.configure(JavaApplication::class.java) {
            mainClass.set("com.ghatana.${project.name}.Application")
        }
    }
}