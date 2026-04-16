plugins {
    id("java-application")
}

description = "Security Gateway standalone launcher"

application {
    mainClass.set("com.ghatana.securitygateway.launcher.SecurityGatewayLauncher")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":products:security-gateway:platform:java"))
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:observability"))

    implementation(libs.activej.http)
    implementation(libs.activej.eventloop)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.jackson.datatype.jdk8)
    implementation(libs.slf4j.api)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.log4j.core)

    testImplementation(project(":platform:java:testing"))
    testImplementation(libs.junit.jupiter)
}

tasks.withType<Tar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.withType<Zip> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}