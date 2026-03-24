plugins {
    id("java-library")
}

description = "YAPPC Scaffold Engine - Core scaffolding orchestration logic"

dependencies {
    api(project(":core:scaffold:api"))
    api(project(":core:ai"))
    api(project(":core:domain"))
    
    implementation(platform(project(":platform:java")))
    implementation("io.activej:activej-promise")
    
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
}
