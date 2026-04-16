# platform:java:database

Canonical packages: `com.ghatana.core.database.*`, `com.ghatana.platform.database.*`, `com.ghatana.platform.cache.*`

## Purpose

`platform:java:database` provides shared database abstractions and infrastructure for Java services, including connection pooling, persistence support, Flyway-backed schema migration, Redis integration, and common caching patterns.

## Dependencies

- `platform:java:core` for shared runtime types
- `platform:java:observability` for database and cache metrics
- HikariCP, Jakarta Persistence, and Hibernate for database access patterns
- Flyway for schema migration support
- Jedis and Lettuce for Redis-backed integrations

## Usage

Add the module as a dependency:

```kotlin
dependencies {
    implementation(project(":platform:java:database"))
}
```

Use the shared migration and test support from the module when bootstrapping persistent services:

```java
FlywayMigration migration = FlywayMigration.builder()
    .locations("classpath:db/migration")
    .build();
```

## Public API Surface

- Shared database and cache infrastructure under `com.ghatana.core.database.*`
- Platform database and test support under `com.ghatana.platform.database.*`
- Distributed and cache-oriented support under `com.ghatana.platform.cache.*`