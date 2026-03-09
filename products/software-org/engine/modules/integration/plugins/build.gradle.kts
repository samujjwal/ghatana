plugins {
    id("com.ghatana.java-conventions")
}

dependencies {
    api(project(":products:software-org:engine:modules:integration"))
    api(libs.activej.inject)
    implementation(project(":platform:java:observability"))
    testImplementation(project(":platform:java:testing"))
}

description = "Software-Org Plugin Registry and SPI"
