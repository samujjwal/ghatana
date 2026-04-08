/**
 * Quality Convention Plugin
 *
 * @doc.type convention-plugin
 * @doc.purpose Applies consistent quality tool configuration across all modules
 * @doc.layer build
 *
 * This plugin configures:
 * - Checkstyle with version from catalog
 * - PMD with version from catalog
 * - SpotBugs with version from catalog
 * - JaCoCo with version from catalog
 */
plugins {
    checkstyle
    pmd
    jacoco
    com.diffplug.spotless
}

// Checkstyle configuration
configure<CheckstyleExtension> {
    toolVersion = "10.21.4"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    configProperties = mapOf(
        "suppressionFile" to rootProject.file("config/checkstyle/suppressions.xml").absolutePath
    )
    isIgnoreFailures = false
    isShowViolations = true
}

// PMD configuration
configure<PmdExtension> {
    toolVersion = "7.11.0"
    ruleSetFiles = files(rootProject.file("config/pmd/ruleset.xml"))
    ruleSets = emptyList()
    isIgnoreFailures = false
    isConsoleOutput = true
}

// JaCoCo configuration
configure<JacocoPluginExtension> {
    toolVersion = "0.8.14"
}

// JaCoCo agent configuration for test JVMs
configurations.create("jacocoAgent")

dependencies {
    add("jacocoAgent", "org.jacoco:org.jacoco.agent:0.8.14")
    add("jacocoAnt", "org.jacoco:org.jacoco.ant:0.8.14")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.withType<Test>())
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

// Spotless configuration
configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    java {
        target("src/**/*.java")
    }
    
    format("misc") {
        target("**/*.gradle", "**/.gitignore")
    }
    
    format("xml") {
        target("**/*.xml", "**/*.xsd")
    }
    
}
