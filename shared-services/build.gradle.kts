plugins {
    id("java-platform")
}

group = "com.ghatana.services"
version = rootProject.version

description = "Shared Services - Cross-product microservices"

// Apply standard conventions to all subprojects
subprojects {
    group = rootProject.group
    version = rootProject.version
    
    // Convention plugins are applied automatically from root build.gradle.kts
    // No manual configuration needed here
}

