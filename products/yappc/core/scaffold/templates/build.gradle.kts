plugins {
    id("java-library")
}

description = "YAPPC Scaffold Templates - Template loading, parsing, and rendering"

dependencies {
    api(project(":core:scaffold:api"))
    
    implementation(platform(project(":platform:java")))
    implementation("io.activej:activej-promise")
    implementation("org.yaml:snakeyaml")
    
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
}
