# platform:java:http

Canonical package: `com.ghatana.platform.http.*`

## Purpose

`platform:java:http` provides shared HTTP client and server utilities for Java services, including request routing, response building, health endpoints, security filters, and standardized outbound client construction.

## Dependencies

- `platform:java:core` for common platform types
- `platform:java:runtime`, `platform:java:security`, and `platform:java:governance` for runtime filters and request-context support
- ActiveJ HTTP for server-side infrastructure
- OkHttp, Guava, Caffeine, and Jackson for client and handler utilities

## Usage

Add the module as a dependency:

```kotlin
dependencies {
    implementation(project(":platform:java:http"))
}
```

Use the shared builders and servlet support instead of constructing raw HTTP responses or ad hoc clients:

```java
return ResponseBuilder.ok().json(responseBody).build();
```

## Public API Surface

- HTTP server and client utilities under `com.ghatana.platform.http.*`
- Shared response-building and health-check helpers
- Standardized filter and client-factory patterns for platform and product services