# Cross-Product Token Exchange

## Overview

The Auth Gateway provides a cross-product token exchange mechanism that allows product-specific JWT tokens to be exchanged for platform-wide tokens. This enables users to authenticate once and access multiple products within the platform.

## Token Exchange Endpoint

**POST** `/auth/exchange`

**Request Headers:**
```
Authorization: Bearer <product-jwt>
Content-Type: application/json
```

**Response:**
```json
{
  "platformToken": "...",
  "expiresIn": 900
}
```

## Implementation by Product

### YAPPC

YAPPC implements the token exchange flow after successful login:

1. User logs in with username/password
2. YAPPC obtains a product-specific JWT from the backend
3. YAPPC calls `/auth/exchange` to obtain a platform token
4. Platform token is stored in session storage alongside the product token
5. Platform token is used for cross-product API calls

**Configuration:**
- Environment variable: `VITE_AUTH_GATEWAY_URL` (default: `http://localhost:8081`)

**Implementation Details:**
- `AuthService.exchangePlatformToken()` method handles the exchange
- Platform token is stored in `AuthSession.platformToken`
- Exchange is called automatically after successful login
- Fails gracefully if the exchange fails (logs warning but continues)

### AEP

AEP uses a different authentication model:

1. User provides a platform-issued JWT directly
2. AEP stores the platform token as the auth token
3. AEP optionally requests an AEP-specific session token for repeated API calls
4. No token exchange is needed since the user already has a platform token

**Note:** AEP's login flow is designed for platform-issued tokens, so it doesn't need the token exchange mechanism.

## Platform Token Usage

Platform tokens carry the following claims:
- Original subject (user ID)
- Roles
- Email
- Tenant ID
- `tokenType: "PLATFORM"` claim to distinguish from product-scoped tokens

Platform tokens are short-lived (default 15 minutes) and should be refreshed when needed.

## Security Considerations

- Platform tokens are issued only after validating the product token
- Platform tokens are short-lived to minimize risk
- Product tokens must be valid and unexpired
- Rate limiting applies to the exchange endpoint

## Configuration

### Auth Gateway

The Auth Gateway is configured via environment variables:

- `JWT_SECRET`: Secret for signing tokens
- `JWT_ISSUER`: Token issuer
- `JWT_EXPIRATION_SECONDS`: Token expiration time
- `PLATFORM_TOKEN_EXPIRATION_SECONDS`: Platform token expiration time (default: 900)

### Frontend Configuration

Frontend applications need to configure the Auth Gateway URL:

```bash
export VITE_AUTH_GATEWAY_URL=http://localhost:8081
```

## Error Handling

The token exchange can fail for the following reasons:

- Invalid or expired product token
- Rate limiting exceeded
- Auth Gateway unavailable
- Network errors

Frontend applications should handle these gracefully and continue with product-scoped authentication if the exchange fails.

## Testing

### Integration Test for Platform Token Exchange

```typescript
// Test that platform token is obtained after login
test('should obtain platform token after login', async () => {
  const result = await authService.login({ email, password });
  expect(result.success).toBe(true);
  
  const platformToken = authService.getPlatformToken();
  expect(platformToken).toBeTruthy();
});
```

### Integration Test for Cross-Product Calls

```typescript
// Test that platform token is used for cross-product calls
test('should use platform token for cross-product API calls', async () => {
  const platformToken = authService.getPlatformToken();
  const response = await fetch('http://other-product/api/resource', {
    headers: {
      'Authorization': `Bearer ${platformToken}`,
    },
  });
  expect(response.ok).toBe(true);
});
```

## Future Enhancements

- Automatic platform token refresh
- Token exchange for session tokens
- Cross-product logout propagation
- Audit logging for token exchange events
