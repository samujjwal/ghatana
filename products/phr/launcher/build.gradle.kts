plugins {
    id("java-application")
}

description = "PHR standalone launcher"

application {
    mainClass.set("com.ghatana.phr.launcher.PhrLauncher")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":products:phr"))
    implementation(project(":platform-kernel:kernel-core"))
    implementation(project(":platform:java:runtime"))
    implementation(project(":platform:java:observability"))

    implementation(libs.activej.eventloop)
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

tasks.named("check") {
    dependsOn(
        ":products:phr:productConformanceCheck",
        ":products:phr:checkApiContractConformance",
    )
}
