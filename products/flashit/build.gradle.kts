plugins {
    id("java-library")
}

group = "com.ghatana.flashit"
version = rootProject.version

dependencies {
    // Platform core dependencies
    api(project(":platform:java:core"))
    api(project(":platform-kernel:kernel-core"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:config"))
    
    // Libraries
    implementation(libs.slf4j.api)
    implementation(libs.jackson.databind)
}

configure<com.diffplug.gradle.spotless.SpotlessExtension> {
    format("misc") {
        target(
            "build.gradle.kts",
            "settings.gradle.kts",
            ".gitignore",
            "README.md",
            "OWNER.md"
        )
        targetExclude("backend/**", "client/**", "build/**", ".gradle/**")
    }
}

tasks.matching { task ->
    task.name == "spotlessMisc" ||
        task.name == "spotlessMiscCheck" ||
        task.name == "spotlessXml" ||
        task.name == "spotlessXmlCheck"
}.configureEach {
    enabled = false
}

// Aggregate build task for all flashit components
tasks.register("buildAll") {
    group = "build"
    description = "Builds all flashit components (Java agent, TypeScript packages)"
    dependsOn(":agent:build", "build")
}
