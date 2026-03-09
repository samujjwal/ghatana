plugins {
    id("com.ghatana.java-conventions")
}

dependencies {
    api(project(":products:software-org:engine:modules:integration:plugins"))
    implementation(project(":platform:java:observability"))
    testImplementation(project(":platform:java:testing"))
}

description = "CI/CD Integration for Software-Org"
