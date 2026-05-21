plugins {
    id("java-module")
    id("application")
}

application {
    mainClass.set("com.ghatana.services.studio.StudioWorkflowService")
}

dependencies {
    implementation(libs.bundles.activej)
    implementation(libs.bundles.jackson)
    implementation(platform(libs.slf4j.bom))
    implementation("org.slf4j:slf4j-api")
    implementation("org.slf4j:slf4j-simple")
    
    // Platform modules
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:security"))
    
    testImplementation(libs.bundles.test)
    testImplementation(libs.activej.test)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}
