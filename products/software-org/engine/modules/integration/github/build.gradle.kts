plugins {
    id("com.ghatana.java-conventions")
}

dependencies {
    api(project(":products:software-org:engine:modules:integration:plugins"))
    api(project(":products:aep:platform"))
    implementation(project(":platform:java:observability"))
    testImplementation(project(":platform:java:testing"))
}

description = "GitHub Integration for Software-Org"
