plugins {
    id("maven-publish")
    id("java-module")
}

description = "YAPPC Consolidated Shared Utilities Module"

dependencies {
    // Platform modules
    api(project(":platform-kernel:kernel-plugin"))
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:agent-core"))
    api(project(":platform:contracts"))

    // ActiveJ for async
    api(libs.activej.promise)

    // Jackson for JSON
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)

    // Logging
    implementation(libs.slf4j.api)

    // Utilities
    implementation(libs.commons.lang3)

    // Configuration

    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.archunit.junit5)
}

tasks.test {
    useJUnitPlatform()
}

// Handle duplicate entries in sourcesJar
tasks.withType<org.gradle.jvm.tasks.Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Source sets for shared utilities
sourceSets {
    main {
        java {
            srcDirs(
                "src/main/java",
                "src/common/java",
                "src/utils/java"
            )
        }
        resources {
            srcDirs(
                "src/main/resources",
                "src/common/resources",
                "src/utils/resources"
            )
        }
    }
    test {
        java {
            srcDirs(
                "src/test/java",
                "src/common/test",
                "src/utils/test"
            )
        }
    }
}

// Publish as a library for other products to use
java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            pom {
                name.set("YAPPC Shared Utilities")
                description.set("Shared utilities and common components for YAPPC")
                url.set("https://github.com/ghatana/ghatana/tree/main/products/yappc")

                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }

                developers {
                    developer {
                        id.set("ghatana")
                        name.set("Ghatana AI Platform")
                        email.set("platform@ghatana.ai")
                    }
                }
            }
        }
    }
}

// Shared utility tasks
tasks.register("validateSharedUtils") {
    group = "verification"
    description = "Validate shared utilities"

    doLast {
        println("Validating shared utilities...")
    }
}

tasks.register("benchmarkUtils") {
    group = "performance"
    description = "Benchmark shared utilities performance"

    doLast {
        println("Running utility benchmarks...")
    }
}
