plugins {
    id("java-library")
}

group = "com.ghatana.flashit"
version = rootProject.version

dependencies {
    // Platform core dependencies
    api(project(":platform:java:core"))
    api(project(":platform:java:kernel"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:config"))
    
    // Libraries
    implementation(libs.slf4j.api)
    implementation(libs.jackson.databind)
}

// Aggregate build task for all flashit components
tasks.register("buildAll") {
    group = "build"
    description = "Builds all flashit components (Java agent, TypeScript packages)"
    dependsOn(":agent:build", "build")
}
