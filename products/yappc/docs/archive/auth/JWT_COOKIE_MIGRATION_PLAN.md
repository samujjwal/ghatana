# JWT Cookie Migration Plan (P1-1)

## Overview

Migrate JWT access token storage from localStorage to httpOnly secure cookies for improved security.

## Current State

**Frontend (localStorage):**
- `localStorage.getItem('auth_token')` used in 10+ locations
- Direct token access in API clients and page components
- Vulnerable to XSS attacks

**Backend (Java):**
- JWT tokens issued via `LifecycleLoginController`
- No cookie support currently

## Migration Strategy

### Phase 1: Backend Changes (Java)

**1. Add Cookie Support to LifecycleLoginController**

```java
// In LifecycleLoginController.java
// Add Set-Cookie header for access token on login response
response.setHeader("Set-Cookie", 
    String.format("access_token=%s; Path=/; HttpOnly; Secure; SameSite=Strict; Max-Age=%d",
        accessToken, 900)); // 15 minutes
```

**2. Add Cookie Middleware Support**

- Create cookie parser utility in platform:java:http
- Extract access token from Cookie header in auth middleware
- Fallback to Authorization header for backward compatibility

### Phase 2: Frontend Changes (TypeScript)

**1. Create Cookie Utility**

```typescript
// frontend/web/src/lib/cookie-utils.ts
export const getAccessToken = (): string | undefined => {
  // Read from httpOnly cookie (server-side)
  return document.cookie
    .split('; ')
    .find(row => row.startsWith('access_token='))
    ?.split('=')[1];
};
```

**2. Update Auth Session Provider**

```typescript
// frontend/web/src/providers/auth-session.ts
// Replace localStorage access with cookie utility
// Keep localStorage for refresh token (or migrate to httpOnly as well)
```

**3. Update API Clients**

- Replace all `localStorage.getItem('auth_token')` with cookie utility
- Update CanvasAPIClient, agentService, and all page components
- Add fallback to Authorization header for development

### Phase 3: Testing

**1. Unit Tests**
- Test cookie extraction logic
- Test token refresh flow with cookies

**2. Integration Tests**
- Test login flow sets cookie correctly
- Test API calls use cookie authentication
- Test logout clears cookie

**3. E2E Tests**
- Update Playwright tests to handle cookie authentication
- Verify XSS protection (cookie not accessible via JS)

### Phase 4: Rollout

**1. Feature Flag**
- Enable cookie authentication via environment variable
- Allow gradual rollout with fallback to header auth

**2. Migration Period**
- Support both cookie and header authentication
- Monitor for issues
- Remove header auth after validation

## Implementation Order

1. **Backend First**: Add cookie support to Java service
2. **Frontend Utility**: Create cookie reading utility
3. **Frontend Migration**: Update all localStorage usages
4. **Testing**: Comprehensive test coverage
5. **Rollout**: Feature-flagged deployment

## Dependencies

- Platform module: `platform:java:http` (for cookie support)
- Frontend: No new dependencies (use native document.cookie)

## Risks

- **Breaking Changes**: Ensure backward compatibility with Authorization header
- **CSRF Protection**: Add CSRF token support if needed
- **Testing Complexity**: E2E tests need cookie handling updates

## Timeline Estimate

- Backend changes: 2-3 hours
- Frontend utility: 1 hour
- Frontend migration: 4-6 hours (10+ locations)
- Testing: 3-4 hours
- Total: 10-14 hours

## Notes

- Refresh tokens should also migrate to httpOnly cookies for complete security
- Consider adding SameSite=Lax for better CSRF protection
- Document cookie configuration for production deployment
