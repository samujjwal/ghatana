plugins {
    base
}

group = "com.ghatana.flashit"
version = "0.1.0-SNAPSHOT"

// Aggregate build task for all flashit components
tasks.register("buildAll") {
    group = "build"
    description = "Builds all flashit components (Java agent, TypeScript packages)"
    dependsOn(":agent:build")
}
