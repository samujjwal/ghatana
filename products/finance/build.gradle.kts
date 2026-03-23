/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */

plugins {
    id("java-library")
}

dependencies {
    // Platform core dependencies
    api(project(":platform:java:core"))
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
    implementation(project(":platform:java:agent-core"))

    // Kernel modules
    implementation(project(":platform:java:kernel-capabilities"))  // Merged: kernel/modules/* (auth + config + event-store + audit + resilience + observability + secrets)

    // Finance Domains - Core
    implementation(project(":products:finance:domains:oms"))
    implementation(project(":products:finance:domains:ems"))
    implementation(project(":products:finance:domains:pms"))
    implementation(project(":products:finance:domains:risk"))
    implementation(project(":products:finance:domains:compliance"))
    implementation(project(":products:finance:domains:rules"))

    // Finance Domains - New (Migration Phase 2)
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
    testImplementation(project(":platform:java:testing"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
    testImplementation("org.mockito:mockito-junit-jupiter")
    testImplementation("io.activej:activej-test:6.0-rc2")
}
