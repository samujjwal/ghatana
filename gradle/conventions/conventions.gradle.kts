subprojects {
    plugins.withId("java") {
        apply(plugin = "jacoco")
        
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        tasks.withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
            options.compilerArgs.add("-parameters")
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
                showStandardStreams = true
            }
            
            extensions.configure<JacocoTaskExtension> {
                destinationFile = file("${buildDir}/jacoco/test.exec")
            }
            
            finalizedBy("jacocoTestReport")
            
            // Enable JaCoCo agent (opt-out via -PdisableJacocoAgent=true)
            val jacocoAgentEnabled = !(project.hasProperty("disableJacocoAgent") || rootProject.hasProperty("disableJacocoAgent"))
            if (jacocoAgentEnabled) {
                // Note: jacocoInstrumentationSettings() removed in Gradle 9.x
                // JaCoCo agent is automatically configured by the jacoco plugin
                
                // Add JVM arguments for JaCoCo (append to any existing args)
                // Use the explicit jacocoAgent configuration (contains the pinned agent JAR)
                jvmArgs(
                    "-javaagent:${project.configurations.getByName("jacocoAgent").asPath}=destfile=${buildDir}/jacoco/test.exec"
                )
            } else {
                project.logger.lifecycle("JaCoCo javaagent disabled for project ${project.path} (set -PdisableJacocoAgent=false to enable)")
            }
        }

        tasks.named<JacocoReport>("jacocoTestReport").configure {
            dependsOn(tasks.withType<Test>())
            // Ensure resources are processed before generating the report to avoid
            // implicit dependency issues detected by Gradle validation
            tasks.findByName("processResources")?.let {
                dependsOn(it)
            }
            reports {
                xml.required.set(true)
                html.required.set(true)
            }
            executionData.setFrom(fileTree(buildDir).include("jacoco/test.exec"))
            sourceDirectories.setFrom(files(project.extensions.getByName("sourceSets").named("main").get().allSource.srcDirs))
            classDirectories.setFrom(files(project.extensions.getByName("sourceSets").named("main").get().output))
            // Note: afterEvaluate removed for Gradle 9.x compatibility
            // Basic class directory filtering is handled by JaCoCo plugin
        }

        // Shared Integration Test convention
        val sourceSets = project.extensions.getByName("sourceSets") as SourceSetContainer
        if (!sourceSets.findByName("integrationTest")) {
            sourceSets.create("integrationTest") {
                java.srcDir(file("src/integration-test/java"))
                resources.srcDir(file("src/integration-test/resources"))
            }
        }

        val configurations = project.extensions.getByName("configurations") as ConfigurationContainer
        if (configurations.findByName("integrationTestImplementation") != null && configurations.findByName("testImplementation") != null) {
            configurations.getByName("integrationTestImplementation").extendsFrom(
                configurations.getByName("testImplementation"),
                configurations.getByName("implementation")
            )
        }
        if (configurations.findByName("integrationTestRuntimeOnly") != null && configurations.findByName("testRuntimeOnly") != null) {
            configurations.getByName("integrationTestRuntimeOnly").extendsFrom(
                configurations.getByName("testRuntimeOnly"),
                configurations.getByName("runtimeOnly")
            )
        }

        tasks.register<Test>("integrationTest") {
            description = "Runs integration tests."
            group = "verification"
            testClassesDirs = sourceSets.named("integrationTest").get().output.classesDirs
            classpath = sourceSets.named("integrationTest").get().runtimeClasspath
            useJUnitPlatform()
            testLogging {
                events("passed", "skipped", "failed")
            }
            shouldRunAfter(tasks.withType<Test>().matching { it.name == "test" })
        }

        tasks.named("check").configure {
            dependsOn(tasks.named("integrationTest"))
        }
    }
}
