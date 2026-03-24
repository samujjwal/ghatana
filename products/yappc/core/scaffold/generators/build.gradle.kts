plugins {
    id("java-library")
}

description = "YAPPC Scaffold Generators - Language-specific code generators"

dependencies {
    api(project(":core:scaffold:engine"))
    api(project(":core:scaffold:api"))
    
    implementation(platform(project(":platform:java")))
    implementation("io.activej:activej-promise")
    
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
}
