plugins {
    base
}

group = "com.ghatana.flashit"
version = "2026.3.1-SNAPSHOT"

// Aggregate build task for all flashit components
tasks.register("buildAll") {
    group = "build"
    description = "Builds all flashit components (Java agent, TypeScript packages)"
    dependsOn(":agent:build")
}
