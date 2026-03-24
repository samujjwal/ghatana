plugins {
    id("com.ghatana.java-conventions")
    id("com.ghatana.java-library-conventions")
}

description = "YAPPC Consolidated API Module"

dependencies {
    // Platform dependencies
    implementation(platform("com.ghatana:platform-bom"))
    
    // Platform API components
    implementation("com.ghatana.platform:rest-core")
    implementation("com.ghatana.platform:graphql-core")
    
    // YAPPC services
    implementation(projects.yappcServices)
    
    // YAPPC shared utilities
    implementation(projects.yappcShared)
    
    // REST API
    implementation("io.activej:activej-http")
    implementation("io.activej:activej-boot")
    
    // GraphQL
    implementation("com.graphql-java:graphql-java")
    implementation("com.graphql-java:graphql-java-spring-boot-starter")
    
    // Documentation
    implementation("org.springdoc:springdoc-openapi-ui")
    
    // Security
    implementation("io.jsonwebtoken:jjwt-api")
    implementation("io.jsonwebtoken:jjwt-impl")
    implementation("io.jsonwebtoken:jjwt-jackson")
    
    // Validation
    implementation("org.hibernate.validator:hibernate-validator")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.mockito:mockito-core")
    testImplementation("io.rest-assured:rest-assured")
}

tasks.test {
    useJUnitPlatform()
}

// Source sets for API components
sourceSets {
    main {
        java {
            srcDirs(
                "src/main/java",
                "src/rest/java",
                "src/graphql/java"
            )
        }
        resources {
            srcDirs(
                "src/main/resources",
                "src/rest/resources",
                "src/graphql/resources"
            )
        }
    }
    test {
        java {
            srcDirs(
                "src/test/java",
                "src/rest/test",
                "src/graphql/test"
            )
        }
    }
}

// API-specific tasks
tasks.register("generateApiDocs") {
    group = "documentation"
    description = "Generate API documentation"
    
    doLast {
        println("Generating API documentation...")
    }
}

tasks.register("validateApiSpecs") {
    group = "verification"
    description = "Validate API specifications"
    
    doLast {
        println("Validating API specifications...")
    }
}

// Make API docs generation part of build
tasks.named("build") {
    dependsOn("generateApiDocs")
}
