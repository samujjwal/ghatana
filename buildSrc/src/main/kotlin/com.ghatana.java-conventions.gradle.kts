import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion

// Applies the Java plugin and configures Java 21 toolchain, encoding, and test logging.

plugins {
    java
}

java {
    if (!toolchain.languageVersion.isPresent) {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-parameters", "-Xlint:unchecked", "-Xlint:deprecation"))
}

tasks.withType<Javadoc>().configureEach {
    val enableJavadoc = project.findProperty("enableJavadoc")?.toString()?.toBoolean() ?: false
    options.encoding = "UTF-8"
    (options as? StandardJavadocDocletOptions)?.addStringOption("Xdoclint:none", "-quiet")

    runCatching {
        setSource(
            project.extensions.getByType(JavaPluginExtension::class.java)
                .sourceSets.getByName("main").allJava
                .matching { exclude("**/generated/**", "**/build/generated/**") }
        )
    }

    if (enableJavadoc) {
        logger.lifecycle("Javadoc enabled for project $path (enableJavadoc=true). Generated sources excluded.")
    } else {
        isEnabled = false
        logger.lifecycle("Javadoc disabled for project $path; set -PenableJavadoc=true to re-enable")
    }
}

tasks.withType<Test>().configureEach {
    testLogging.events("passed", "skipped", "failed")
}

// ── Dependency Guard: Block deprecated shared:* modules ──────────────────────
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == project.rootProject.name
            && requested.name.startsWith("shared-")
        ) {
            throw GradleException(
                "Dependency on deprecated module '${requested.name}' is forbidden.\n" +
                "Migrate: shared:metrics → libs:observability, " +
                "shared:exception → libs:common-utils, " +
                "shared:test-utils → libs:activej-test-utils"
            )
        }
    }
}
