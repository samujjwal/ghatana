# Exception Hierarchy Guidelines

## Overview

This document defines the exception hierarchy and usage guidelines for the Ghatana platform. All exceptions should follow this hierarchy to ensure consistent error handling across the platform.

## Exception Hierarchy

```
BaseException (abstract)
├── PlatformException (runtime, platform-level errors)
│   ├── ConfigurationException (configuration errors)
│   ├── InitializationException (startup errors)
│   └── InternalException (unexpected internal errors)
│
├── ServiceException (service-level errors)
│   ├── ServiceUnavailableException (503)
│   ├── TimeoutException (504)
│   └── CancellationException (operation cancelled)
│
├── ResourceException (resource-related errors)
│   ├── ResourceNotFoundException (404)
│   ├── ConflictException (409)
│   └── ResourceExhaustedException (507)
│
├── AuthenticationException (401 errors)
│   ├── UnauthorizedException (no credentials)
│   └── TokenExpiredException (expired token)
│
├── AuthorizationException (403 errors)
│   ├── InsufficientPermissionsException
│   └── GovernancePolicyException
│
├── ValidationException (422 errors)
│   ├── ValidationFailureException
│   └── InvalidQueryException
│
├── DataAccessException (data layer errors)
│   ├── DatabaseException
│   ├── QueryException
│   └── TransactionException
│
├── EventProcessingException (event-related errors)
│   ├── EventNotFoundException
│   ├── EventValidationException
│   └── EventRoutingException
│
└── RateLimitExceededException (429)
```

## Usage Guidelines

### 1. Choose the Right Exception

**Use PlatformException for:**
- Configuration errors
- Initialization failures
- Internal system errors

**Use ServiceException for:**
- Service unavailability
- Timeout errors
- Cancelled operations

**Use ResourceException for:**
- Resource not found (404)
- Resource conflicts (409)
- Resource exhaustion (507)

**Use AuthenticationException for:**
- Missing credentials (401)
- Invalid credentials (401)
- Expired tokens (401)

**Use AuthorizationException for:**
- Insufficient permissions (403)
- Policy violations (403)

**Use ValidationException for:**
- Input validation errors (422)
- Invalid query parameters (400)

**Use DataAccessException for:**
- Database errors
- Query failures
- Transaction errors

**Use EventProcessingException for:**
- Event not found
- Event validation failures
- Event routing errors

### 2. Exception Creation

All exceptions should:
- Extend the appropriate base exception
- Include an ErrorCode
- Provide a descriptive message
- Include the original cause when wrapping exceptions

Example:
```java
public class MyCustomException extends ServiceException {
    public MyCustomException(String message) {
        super(ErrorCode.SERVICE_ERROR, message);
    }
    
    public MyCustomException(String message, Throwable cause) {
        super(ErrorCode.SERVICE_ERROR, message, cause);
    }
}
```

### 3. HTTP Status Mapping

Exceptions automatically map to HTTP status codes via ErrorCode:

| Exception Type | HTTP Status | ErrorCode Prefix |
|----------------|-------------|------------------|
| AuthenticationException | 401 | AUTH-* |
| AuthorizationException | 403 | AUTH-* |
| ResourceNotFoundException | 404 | RES-404 |
| ConflictException | 409 | RES-409 |
| ValidationException | 422 | GEN-002 |
| RateLimitExceededException | 429 | GEN-429 |
| InternalException | 500 | GEN-* |
| ServiceUnavailableException | 503 | SVC-001 |
| TimeoutException | 504 | KG-9003 |
| ResourceExhaustedException | 507 | STG-002 |

### 4. Error Code Assignment

Error codes follow the format: `PREFIX-NNN`

**Prefixes:**
- `GEN-*`: General errors
- `AUTH-*`: Authentication/authorization errors
- `RES-*`: Resource errors
- `IO-*`: I/O errors
- `NET-*`: Network errors
- `DB-*`: Database errors
- `SVC-*`: Service errors
- `AGT-*`: Agent errors
- `EVT-*`: Event errors
- `PIP-*`: Pipeline errors
- `STG-*`: Storage errors
- `KG-*`: Knowledge graph errors

### 5. Exception Handling Best Practices

**Do:**
- Use specific exceptions for specific errors
- Include context in exception messages
- Preserve the original cause when wrapping
- Log exceptions at appropriate levels
- Convert exceptions at API boundaries

**Don't:**
- Catch generic Exception unless necessary
- Swallow exceptions without logging
- Create new exception types unnecessarily
- Include sensitive data in messages
- Use exceptions for flow control

### 6. Migration Path

**Consolidating Similar Exceptions:**

If you find similar exceptions:
1. Identify the common base exception
2. Check if ErrorCode covers the use case
3. Deprecate duplicate exceptions
4. Update callers to use the consolidated exception

Example:
```java
// Before: Multiple similar exceptions
class UserNotFoundException extends Exception { }
class AgentNotFoundException extends Exception { }
class EventNotFoundException extends Exception { }

// After: Use ResourceNotFoundException with context
throw new ResourceNotFoundException("User not found: " + userId);
throw new ResourceNotFoundException("Agent not found: " + agentId);
throw new ResourceNotFoundException("Event not found: " + eventId);
```

## Deprecation Process

When deprecating an exception:

1. Add `@Deprecated` annotation
2. Add Javadoc explaining replacement
3. Update all usages in codebase
4. Keep deprecated exception for 2 releases
5. Remove in major version update

Example:
```java
/**
 * @deprecated Use {@link ResourceNotFoundException} instead.
 * This exception will be removed in version 2.0.
 */
@Deprecated(since = "1.5", forRemoval = true)
public class UserNotFoundException extends ResourceNotFoundException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
```

## Testing Guidelines

All custom exceptions should have tests covering:
- Exception creation with message
- Exception creation with cause
- ErrorCode mapping
- HTTP status code mapping
- Serialization/deserialization

## Documentation Requirements

Each exception class should document:
- When to use this exception
- What ErrorCode it uses
- What HTTP status it maps to
- Example usage

## Review Checklist

Before creating a new exception, verify:
- [ ] No existing exception covers this use case
- [ ] Exception extends appropriate base class
- [ ] ErrorCode is assigned and documented
- [ ] HTTP status mapping is correct
- [ ] Javadoc is complete
- [ ] Tests are written
- [ ] Usage examples are provided
