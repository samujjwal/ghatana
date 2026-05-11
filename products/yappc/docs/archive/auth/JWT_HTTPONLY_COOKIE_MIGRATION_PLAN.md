# JWT to httpOnly Cookie Migration Plan

**Task:** YAPPC-P1-1: Migrate JWT from localStorage to httpOnly cookie
**Priority:** High (Security)
**Status:** Implementation Plan

## Current State

- JWT tokens (accessToken and refreshToken) are stored in localStorage
- Frontend retrieves tokens from localStorage and includes them in Authorization headers
- Security vulnerability: XSS attacks can steal tokens from localStorage

## Target State

- Access token stored in httpOnly, Secure, SameSite cookie
- Refresh token stored in httpOnly, Secure, SameSite cookie
- Frontend no longer stores tokens in localStorage
- Tokens automatically sent with requests via cookies
- No JavaScript access to tokens (mitigates XSS token theft)

## Implementation Steps

### Phase 1: Backend Changes (Node BFF)

**File:** `products/yappc/frontend/apps/api/src/routes/auth.ts`

1. **Update login endpoint** to set httpOnly cookies instead of returning tokens in response body:
   ```typescript
   // Set httpOnly cookies
   reply.setCookie('accessToken', result.tokens.accessToken, {
     httpOnly: true,
     secure: process.env.NODE_ENV === 'production',
     sameSite: 'strict',
     path: '/',
     maxAge: result.tokens.expiresIn * 1000
   });
   
   reply.setCookie('refreshToken', result.tokens.refreshToken, {
     httpOnly: true,
     secure: process.env.NODE_ENV === 'production',
     sameSite: 'strict',
     path: '/api/auth/refresh',
     maxAge: 30 * 24 * 60 * 60 * 1000 // 30 days
   });
   ```

2. **Update refresh endpoint** to read refresh token from cookie and set new access token cookie

3. **Update logout endpoint** to clear cookies:
   ```typescript
   reply.clearCookie('accessToken');
   reply.clearCookie('refreshToken');
   ```

4. **Update authenticateToken middleware** to read from cookie instead of Authorization header (fallback to header for transition period)

### Phase 2: Frontend Changes

**File:** `products/yappc/frontend/web/src/providers/auth-session.ts`

1. **Remove localStorage operations** for tokens:
   - Remove `readStoredSession()` function
   - Remove `persistStoredSession()` function
   - Remove `clearStoredSession()` function
   - Update `getStoredAccessToken()` to return null (tokens in cookies)

2. **Update fetchAuthSession** to rely on cookies instead of localStorage:
   - Remove session reading from localStorage
   - Cookies are automatically sent with requests

**File:** `products/yappc/frontend/web/src/services/auth/AuthService.ts`

1. **Remove localStorage operations**:
   - Remove `localStorage.getItem('auth-session')` in `initializeFromStorage()`
   - Remove `localStorage.setItem('auth-session', ...)` in `saveSession()`
   - Remove `localStorage.removeItem('auth-session')` in `clearSession()`

2. **Update session management**:
   - Session state can be maintained in memory only
   - On page reload, rely on cookie-based authentication

### Phase 3: Test Updates

**Files:** All test files using localStorage for auth

1. Update test helpers to use cookie-based auth
2. Update E2E tests to work with cookie authentication

### Phase 4: Transition Strategy

1. **Dual-mode support** during transition:
   - Backend accepts both cookie and Authorization header
   - Frontend sends both cookie and header during transition
   - Monitor for successful cookie-based auth

2. **Gradual rollout**:
   - Enable cookie-based auth in development first
   - Test thoroughly in staging
   - Roll out to production with monitoring

3. **Fallback plan**:
   - Keep Authorization header support for 2 weeks after cookie rollout
   - Monitor for any client compatibility issues
   - Remove header support after successful transition

## Security Benefits

- XSS attacks cannot steal httpOnly cookies
- CSRF protection via SameSite=strict
- Automatic token transmission via cookies
- No JavaScript access to sensitive tokens

## Risks and Mitigations

| Risk | Mitigation |
|------|-----------|
| Client compatibility | Dual-mode support during transition |
| CSRF attacks | SameSite=strict cookie attribute |
| Cookie size limits | Keep token payloads minimal |
| Cross-subdomain sharing | Configure cookie scope appropriately |

## Testing Checklist

- [ ] Login sets httpOnly cookies correctly
- [ ] Refresh token cookie renewal works
- [ ] Logout clears cookies
- [ ] Protected routes work with cookie auth
- [ ] Session persists across page reloads
- [ ] XSS cannot access cookies
- [ ] CSRF protection works
- [ ] E2E tests pass with cookie auth
- [ ] Manual testing in all environments

## Dependencies

- Fastify cookie plugin: `@fastify/cookie`
- Environment-specific cookie configuration
- TLS/HTTPS in production (required for Secure cookies)

## Estimated Timeline

- Phase 1 (Backend): 2-3 days
- Phase 2 (Frontend): 2-3 days
- Phase 3 (Tests): 1-2 days
- Phase 4 (Transition): 1 week monitoring

**Total:** 2-3 weeks including testing and rollout
