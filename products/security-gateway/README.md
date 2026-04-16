# Security Gateway

**Product Owner:** @ghatana/security-team  
**Status:** Active  
**Stack:** Java 21 + ActiveJ

## Purpose

**Security Gateway** is the central authentication, authorization, and security enforcement layer for the ghatana platform. All products route their auth flows through this gateway.

## Responsibilities

| Concern | Component |
|---------|-----------|
| JWT issuance & validation | `JwtTokenProviderImpl` + `JwtProperties` |
| Authentication flows | `AuthenticationServiceImpl`, `AuthHttpHandler` |
| Authorization (RBAC) | `AuthorizationServiceImpl` |
| Token storage | `InMemoryTokenStore` (dev) / `RedisTokenStore` (prod) / `JpaTokenRepository` |
| Webhook signature verification | `WebhookSignatureValidator` |
| Event security | `EnhancedEventSecurityManagerImpl` |
| gRPC policy enforcement | `PolicyEnforcementInterceptor` |
| Rate limiting | `RateLimitProperties` |
| TLS/mTLS config | `TlsProperties` |
| Metrics | `SecurityMetrics`, `RpcMetrics` |
| Email alerts | `EmailAlertHandler` (via `EmailConfig`) |

## Architecture

```
Client  →  AuthHttpHandler  →  AuthenticationService  →  TokenStore (Redis/JPA)
                           →  AuthorizationService   →  Policy cache
                           →  JwtTokenProvider       →  JWT keys

gRPC services  →  PolicyEnforcementInterceptor
EventCloud     →  EnhancedEventSecurityManager
Webhooks       →  WebhookSignatureValidator
```

## Prerequisites

- Java 21
- Redis (for production token store)
- PostgreSQL (for JPA token repository)
- Docker

## Build & Run

```bash
# Build
./gradlew :products:security-gateway:platform:java:build

# Test
./gradlew :products:security-gateway:platform:java:test

# Run locally
./gradlew :products:security-gateway:launcher:run
```

## Configuration

Key configuration classes (all use `@ConfigValue` injection):

- `JwtProperties` — signing algorithm, key paths, expiry durations
- `AuthProperties` — login/logout endpoint paths
- `RateLimitProperties` — per-IP, per-user rate limits
- `TlsProperties` — mTLS cert/key paths
- `CorsProperties` — allowed origins

## Security Conventions

- JWT keys MUST be externalized via environment variable — never hardcoded
- All token stores must implement constant-time comparison for token validation
- Rate limiting is enforced at the HTTP handler level via `RateLimitProperties`
- Email alerts for security events use `EmailAlertHandler`
