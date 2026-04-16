# platform:java:security

Canonical package: `com.ghatana.platform.security.*`

## Purpose

`platform:java:security` provides shared authentication, authorization, encryption, rate-limiting, token-management, and session abstractions for Java services across the Ghatana platform.

## Dependencies

- `platform:java:core`, `platform:java:config`, `platform:java:domain`, `platform:java:observability`, and `platform:java:database`
- Optional compile-time integration with `platform:java:governance`
- Nimbus JOSE/JWT, OAuth2 OIDC SDK, jBCrypt, and Bouncy Castle for auth and crypto flows
- ActiveJ promise and HTTP support for async security services

## Usage

Add the module as a dependency:

```kotlin
dependencies {
    implementation(project(":platform:java:security"))
}
```

Use the shared security ports and services from product-owned adapters instead of embedding product-specific auth infrastructure into reusable platform code.

## Public API Surface

- Security context, configuration, and utility support under `com.ghatana.platform.security.*`
- Auth, JWT, OAuth2, API key, RBAC, ABAC, rate-limit, crypto, and session packages
- Shared security ports and service abstractions for product integrations