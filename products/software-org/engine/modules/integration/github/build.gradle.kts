plugins {
    id("java-module")
}

dependencies {
    api(project(":products:software-org:engine:modules:integration:plugins"))
    implementation(project(":platform:java:observability"))
    testImplementation(project(":platform:java:testing"))
}

description = "GitHub Integration for Software-Org"
