/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

plugins {
    id("java-library")
}

dependencies {
    // Platform core dependencies
    implementation(project(":platform:java:core"))
    implementation(project(":platform:java:domain"))
    implementation(project(":platform:java:database"))
    implementation(project(":platform:java:http"))
    implementation(project(":platform:java:observability"))
    implementation(project(":platform:java:testing"))
    implementation(project(":platform:java:config"))
    implementation(project(":platform:java:workflow"))
    implementation(project(":platform:java:plugin"))
    implementation(project(":platform:java:event-cloud"))
    implementation(project(":platform:java:audit"))

    // Kernel modules
    implementation(project(":platform:java:kernel:modules:authentication"))
    implementation(project(":platform:java:kernel:modules:config"))
    implementation(project(":platform:java:kernel:modules:event-store"))
    implementation(project(":platform:java:kernel:modules:audit"))
    implementation(project(":platform:java:kernel:modules:resilience"))

    // Finance core
    implementation(project(":products:finance"))
    implementation(project(":products:finance:domains:oms"))
    implementation(project(":products:finance:domains:ems"))
    implementation(project(":products:finance:domains:pms"))
    implementation(project(":products:finance:domains:risk"))
    implementation(project(":products:finance:domains:compliance"))
    implementation(project(":products:finance:domains:rules"))
    implementation(project(":products:finance:domains:corporate-actions"))
    implementation(project(":products:finance:domains:market-data"))
    implementation(project(":products:finance:domains:post-trade"))
    implementation(project(":products:finance:domains:pricing"))
    implementation(project(":products:finance:domains:reconciliation"))
    implementation(project(":products:finance:domains:reference-data"))
    implementation(project(":products:finance:domains:regulatory-reporting"))
    implementation(project(":products:finance:domains:sanctions"))
    implementation(project(":products:finance:domains:surveillance"))

    // ActiveJ Promise
    implementation("io.activej:activej-promise")

    // Jackson for JSON
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // Micrometer for metrics
    implementation("io.micrometer:micrometer-core")

    // PostgreSQL
    implementation("org.postgresql:postgresql")
    implementation("com.zaxxer:HikariCP")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
    testImplementation("io.activej:activej-test:6.0-rc2")
}
