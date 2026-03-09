plugins {
    id("java")
    id("application")
}

group = "com.ghatana.services"
version = "1.0.0-SNAPSHOT"

application {
    mainClass.set("com.ghatana.services.auth.AuthGatewayLauncher")
}

dependencies {
    // Platform libraries (updated paths)
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:security"))
    
    // ActiveJ - Use canonical versions from libs.versions.toml
    implementation(libs.activej.eventloop)
    implementation(libs.activej.promise)
    implementation(libs.activej.http)
    
    // Logging
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j.impl)
    
    // Rate limiting
    implementation(libs.guava)
    
    // Testing
    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
}

tasks.test {
    useJUnitPlatform()
}
