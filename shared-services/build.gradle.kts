import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    id("java-library")
}

group = "com.ghatana.services"
version = rootProject.version

description = "Shared Services - Cross-product microservices"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    plugins.withId("java") {
        extensions.configure(JavaPluginExtension::class.java) {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }
    }

    plugins.withId("java-library") {
        extensions.configure(JavaPluginExtension::class.java) {
            toolchain {
                languageVersion = JavaLanguageVersion.of(21)
            }
        }
    }

    tasks.withType(Test::class.java).configureEach {
        useJUnitPlatform()
    }
}

