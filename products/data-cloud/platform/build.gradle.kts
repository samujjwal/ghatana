plugins {
    id("java-library")
    id("java-test-fixtures")
}

group = "com.ghatana.datacloud"
version = "1.0.0-SNAPSHOT"

description = "Data Cloud Platform - Metadata management and governance"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    // =========================================================================
    // GLOBAL PLATFORM DEPENDENCIES
    // api  = types appear in public method signatures (transitive compile dep)
    // impl = internal usage only (not leaked to downstream consumers)
    // =========================================================================
    api(project(":products:data-cloud:spi"))         // SPI types in public API
    api(project(":platform:java:core"))              // Core types in public API
    api(project(":platform:java:domain"))            // Domain types in public API
    api(project(":platform:java:audit"))             // AuditEvent in public API
    api(project(":platform:java:database"))          // Repository/QuerySpec types in public API
    api(project(":platform:java:observability"))     // Meter types in public API
    implementation(project(":platform:java:http"))   // HTTP handlers are internal impl
    implementation(project(":platform:java:security")) // Auth filters are internal impl
    implementation(project(":platform:java:config")) // Config loading is internal impl
    implementation(project(":platform:java:plugin")) // Plugin registry is internal impl

    // =========================================================================
    // ACTIVEJ
    // =========================================================================
    api(libs.activej.promise)      // Promise<T> appears in all public method signatures
    api(libs.activej.eventloop)    // Eventloop used by public service lifecycle
    implementation(libs.activej.http)   // HTTP routing is internal
    implementation(libs.activej.inject) // DI wiring is internal

    // =========================================================================
    // PERSISTENCE
    // =========================================================================
    api(libs.jakarta.persistence.api)   // @Entity/@Column annotations on public model classes
    implementation(libs.hibernate.core) // ORM runtime is internal impl detail
    implementation(libs.hikaricp)       // Connection pool is internal impl detail
    runtimeOnly(libs.postgresql)        // JDBC driver needed only at runtime
    implementation(libs.flyway.core)    // DB migration is internal
    implementation(libs.flyway.database.postgresql) // Flyway PostgreSQL support is internal

    // =========================================================================
    // SERIALIZATION
    // =========================================================================
    api(platform(libs.jackson.bom))         // BOM: align Jackson versions for all consumers
    api(libs.jackson.databind)              // ObjectMapper used in public API types
    api(libs.jackson.datatype.jsr310)       // JSR-310 module needed transitively
    api(libs.jackson.annotations)           // @JsonProperty etc. on public model classes
    implementation(libs.jackson.dataformat.yaml) // YAML only used internally

    // =========================================================================
    // VALIDATION
    // =========================================================================
    api(libs.jakarta.validation.api)        // @Valid/@NotNull annotations on public types
    implementation(libs.hibernate.validator) // Validator impl is internal

    // =========================================================================
    // REDIS (for distributed cache)
    // =========================================================================
    implementation(libs.lettuce.core) // Redis client is internal impl

    // =========================================================================
    // PLUGIN DEPENDENCIES
    // =========================================================================
    implementation(platform(libs.aws.sdk.bom)) // AWS BOM only needed for internal plugins

    // Storage Plugins
    implementation(libs.aws.s3)         // S3 cold-tier archive
    implementation(libs.aws.glacier)    // Glacier restore
    implementation(libs.iceberg.core)   // Iceberg tiering
    implementation(libs.iceberg.parquet) // Iceberg Parquet
    implementation(libs.iceberg.data)   // Iceberg data
    implementation(libs.hadoop.common)  // Hadoop for Iceberg
    implementation(libs.parquet.avro)   // Parquet format

    // Embedded Storage Backends
    implementation(libs.rocksdb)        // RocksDB JNI for LSM-tree storage
    implementation(libs.sqlite.jdbc)    // SQLite JDBC for lightweight SQL storage
    implementation(libs.h2)             // H2 pure-Java SQL database

    // High-performance patterns
    implementation(libs.disruptor)      // LMAX Disruptor for Redis plugin

    // Streaming Plugins
    implementation(libs.kafka.clients)  // Kafka

    // SQL Parsing (replaces fragile regex-based SQL parsing)
    implementation(libs.jsqlparser)

    // Analytics Plugins
    compileOnly(libs.trino.spi)          // Trino connector (optional)
    compileOnly(libs.trino.plugin.toolkit)

    // Knowledge Graph & Lineage
    implementation(libs.gremlin.core)
    implementation(libs.tinkergraph.gremlin)
    implementation(libs.jgrapht.core)   // For lineage tracking

    // Vector Search
    implementation(libs.langchain4j.embeddings)
    implementation(libs.langchain4j.embeddings.all.minilm.l6.v2)

    // =========================================================================
    // OBSERVABILITY
    // =========================================================================
    api(libs.micrometer.core)                       // MeterRegistry in public service APIs
    implementation(libs.micrometer.registry.prometheus) // Prometheus registry is internal impl

    // =========================================================================
    // gRPC (EventLog and Event Service implementations)
    // =========================================================================
    implementation(project(":platform:contracts"))              // Proto-generated stubs (EventLogServiceGrpc, etc.)
    implementation(libs.grpc.stub)                              // ServiceImplBase abstract class
    implementation(libs.grpc.protobuf)                          // Proto wire marshalling for gRPC
    compileOnly("javax.annotation:javax.annotation-api:1.3.2") // @Generated annotation on proto stubs

    // =========================================================================
    // AI INTEGRATION
    // =========================================================================
    implementation(project(":platform:java:ai-integration"))
    
    // =========================================================================
    // TESTING
    // =========================================================================
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)
    testImplementation(project(":platform:java:testing"))
    testRuntimeOnly(libs.junit.jupiter.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    
    // Test Fixtures (Shared test utilities)
    testFixturesImplementation(libs.junit.jupiter.api)
    testFixturesImplementation(libs.assertj.core)
    
    // =========================================================================
    // LOMBOK
    // =========================================================================
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)
}

tasks.test {
    useJUnitPlatform()
}

// Javadoc: tolerate Lombok-generated symbols that javadoc can't resolve
tasks.named<Javadoc>("javadoc") {
    enabled = true
    isFailOnError = false
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}



