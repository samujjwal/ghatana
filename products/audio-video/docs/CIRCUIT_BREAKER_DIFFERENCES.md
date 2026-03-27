# Circuit Breaker Implementation Differences

**Date**: 2026-03-26  
**Document**: AV-007

## Overview

The audio-video codebase contains circuit breaker implementations in three different languages and contexts. This document explains the differences, rationale, and usage guidelines for each.

## Implementations

### 1. Java Server-Side: CircuitBreakerServerInterceptor

**Location**: `products/audio-video/libs/common/src/main/java/com/ghatana/audio/video/common/resilience/CircuitBreakerServerInterceptor.java`

**Purpose**: Protects gRPC services from cascading failures by intercepting incoming requests.

**Behavior**:
- Per-method circuit breakers (each gRPC method has its own state)
- States: CLOSED (normal), OPEN (failing fast), HALF_OPEN (testing recovery)
- Configuration via environment variables:
  - `CIRCUIT_BREAKER_FAILURE_THRESHOLD` (default: 5)
  - `CIRCUIT_BREAKER_RESET_TIMEOUT_MS` (default: 30000)
- Only counts non-business errors (INTERNAL, UNAVAILABLE, etc.) toward failure threshold
- Business errors (INVALID_ARGUMENT, NOT_FOUND, etc.) do NOT trip the breaker

**When to Use**: All gRPC service methods that call external dependencies or perform heavy computation.

**Integration**:
```java
Server server = ServerBuilder.forPort(port)
    .addService(ServerInterceptors.intercept(service, new CircuitBreakerServerInterceptor()))
    .build();
```

---

### 2. TypeScript Client-Side: CircuitBreaker (audio-video-client)

**Location**: `products/audio-video/libs/audio-video-client/src/index.ts`

**Purpose**: Prevents client-side request storms when services are down.

**Behavior**:
- Per-service circuit breakers (STT, TTS, Vision, etc.)
- Same states as Java: CLOSED, OPEN, HALF_OPEN
- Hardcoded thresholds (failureThreshold=5, resetTimeout=30s)
- Works with HTTP status codes (5xx → failure, 4xx → no retry)
- Fails fast with descriptive error message when open

**When to Use**: All HTTP/gRPC-Web client calls from TypeScript UI code.

**Integration** (automatic via AudioVideoClient):
```typescript
const client = new AudioVideoClient(defaultConfigs);
// Circuit breakers pre-configured for all services
```

---

### 3. Rust/Tauri Client-Side: CircuitBreaker

**Location**: `products/audio-video/apps/desktop/src-tauri/src/error_handling.rs`

**Purpose**: Async circuit breaker for Rust-based desktop backend calls.

**Behavior**:
- Async-aware implementation with proper state machine
- Same core states (Closed, Open, HalfOpen)
- Used in conjunction with `RetryConfig` for backoff
- Thread-safe for async/await contexts

**When to Use**: Rust code making calls to Java backend services.

**Integration**:
```rust
let cb = CircuitBreaker::new(5, Duration::from_secs(30));
let result = cb.call(|| retry_with_backoff(operation, config)).await;
```

---

## Key Differences Summary

| Aspect | Java Server | TS Client | Rust Client |
|--------|-------------|-----------|-------------|
| **Granularity** | Per-method | Per-service | Per-operation |
| **Trigger Errors** | gRPC status codes | HTTP status codes | Any error |
| **Business Errors Ignored** | Yes (INVALID_ARGUMENT, etc.) | Yes (4xx) | Configurable |
| **Configuration** | Environment variables | Hardcoded | Constructor params |
| **Reset Timeout** | 30s default | 30s default | Configurable |
| **State Storage** | ConcurrentHashMap | Map | Atomic state |

## Best Practices

1. **Always have circuit breakers on both sides**: Server-side protects the service, client-side prevents request storms.

2. **Set appropriate thresholds**:
   - Fast operations: 3-5 failures
   - Slow operations: 1-3 failures
   - Consider timeout ratio (if timeout is 30s, reset can be shorter)

3. **Monitor state transitions**: Log OPEN/CLOSED transitions for observability.

4. **Test half-open state**: Ensure recovery is properly detected before closing.

5. **Don't nest circuit breakers**: Avoid having multiple breakers in the same call chain.

## Configuration Guidelines

```bash
# Java server - in service startup script
export CIRCUIT_BREAKER_FAILURE_THRESHOLD=5
export CIRCUIT_BREAKER_RESET_TIMEOUT_MS=30000

# TypeScript client - configure in defaultConfigs
const client = new AudioVideoClient({
  stt: { endpoint: 'http://localhost:8081', timeout: 30000, retries: 3 }
});

# Rust client - in Tauri command
let cb = CircuitBreaker::new(5, Duration::from_secs(30));
```

## Testing Circuit Breakers

Java (unit test pattern):
```java
@Test
void circuitBreaker_opensAfterThreshold() {
    CircuitBreaker cb = new CircuitBreaker(3, 1000);
    
    // 3 failures
    for (int i = 0; i < 3; i++) {
        try {
            cb.call(() -> { throw new RuntimeException("fail"); });
        } catch (Exception ignored) {}
    }
    
    assertEquals(State.OPEN, cb.getState());
}
```

TypeScript:
```typescript
test('circuit breaker opens after threshold', async () => {
  const cb = new CircuitBreaker(3, 1000);
  
  for (let i = 0; i < 3; i++) {
    try { await cb.call(async () => { throw new Error('fail'); }); } catch {}
  }
  
  expect(cb.getState()).toBe('OPEN');
});
```

## Migration Notes

When consolidating circuit breaker code (future task):
- Consider extracting to shared library with language bindings
- Standardize on configuration approach (environment vs code)
- Unify metrics and monitoring hooks
- Maintain async compatibility for Rust/TypeScript
