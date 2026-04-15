package com.ghatana.buildlogic

import com.google.protobuf.gradle.ProtobufExtension
import com.google.protobuf.gradle.id
import org.gradle.api.Plugin
import org.gradle.api.Project

class ProtobufModuleConventionPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project.pluginManager) {
            apply("java-library")
            apply("idea")
            apply("jacoco")
            apply("checkstyle")
            apply("pmd")
            apply("com.diffplug.spotless")
            apply("com.google.protobuf")
        }

        ConventionSupport.configureJavaExtension(project, alwaysSourcesJar = true)
        ConventionSupport.configureJavaCompilation(project)
        ConventionSupport.configureTests(project, aggressiveJvmTuning = false)
        ConventionSupport.configureJacoco(project, finalizedByTests = false)
        ConventionSupport.configureCheckstyle(project, includeSuppressions = false)
        ConventionSupport.configurePmd(project, consoleOutput = false)
        ConventionSupport.configureSpotless(project)
        ConventionSupport.configureJarManifest(project)

        project.extensions.configure(ProtobufExtension::class.java) {
            protoc {
                artifact = "com.google.protobuf:protoc:4.34.1"
            }
            plugins {
                id("grpc") {
                    artifact = "io.grpc:protoc-gen-grpc-java:1.79.0"
                }
            }
            generateProtoTasks {
                all().configureEach {
                    plugins {
                        id("grpc")
                    }
                }
            }
        }

        project.dependencies.apply {
            ConventionSupport.addLombok(this, includeTestProcessors = false)
            add("api", "com.google.protobuf:protobuf-java:4.34.1")
            add("api", "io.grpc:grpc-protobuf:1.79.0")
            add("api", "io.grpc:grpc-stub:1.79.0")
            add("api", "io.grpc:grpc-netty-shaded:1.79.0")
            ConventionSupport.addStandardTestDependencies(this, includeMockito = false, includeLauncher = false)
        }
    }
}