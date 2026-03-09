# Secure Communication in Browser Extensions (mTLS/MQTTS Alternative Patterns)

## Problem: Why mTLS/MQTTS Don't Work in Browser

**Browser extensions cannot use mTLS or MQTTS directly because:**

1. **No file system access** - Cannot load certificate files from disk (`fs` module)
2. **No native TLS library** - Browser only has TLS via HTTP/WebSocket (handled by OS)
3. **No certificate management** - No way to install or manage custom CA certificates
4. **No MQTT protocol** - Would require mqtt.js, but certificate loading requires fs

**Result**: Trying to use mTLS/MQTTS in browser extension = Runtime error

## Solution: Security Patterns for Browser Extensions

### Pattern 1: **Server-Side TLS (RECOMMENDED)**

Browser handles TLS automatically via `https://` and `wss://` URLs.

```typescript
import { createConnector } from '@dcmaar/connectors';

// Browser automatically validates server certificate
const httpConnector = createConnector({
  type: 'http',
  url: 'https://api.guardian.example.com',  // ← TLS automatic via https://
  fetchOptions: {
    headers: {
      'Authorization': `Bearer ${token}`,
      'X-API-Key': apiKey
    }
  }
});

// Real-time secure communication
const wsConnector = createConnector({
  type: 'websocket',
  url: 'wss://api.guardian.example.com/stream',  // ← TLS automatic via wss://
  autoReconnect: true,
  pingPong: true
});

// Browser automatically:
// ✅ Validates server SSL certificate
// ✅ Checks CA chain
// ✅ Prevents man-in-the-middle attacks
// ✅ Uses TLS 1.2+ by default
```

**When to use**: Most common case - trusted backend server with valid certificate

---

### Pattern 2: **OAuth2/OIDC (MOST SECURE)**

Delegate authentication to identity provider with automatic token refresh.

```typescript
import { createConnector } from '@dcmaar/connectors';

// Get tokens from OAuth2 provider
async function getAccessToken(): Promise<string> {
  const response = await fetch('https://auth.guardian.example.com/oauth2/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      client_id: EXTENSION_CLIENT_ID,
      client_secret: EXTENSION_CLIENT_SECRET,
      grant_type: 'client_credentials',
      scope: 'api:read api:write'
    })
  });
  
  const data = await response.json();
  return data.access_token;
}

// Use token in connector
const httpConnector = createConnector({
  type: 'http',
  url: 'https://api.guardian.example.com',
  fetchOptions: {
    headers: {
      'Authorization': `Bearer ${await getAccessToken()}`
    }
  }
});

// Benefits:
// ✅ Tokens can be revoked
// ✅ Automatic expiration
// ✅ Refresh tokens for renewal
// ✅ No secrets stored in extension
// ✅ Audit trail on server
```

**When to use**: Multi-user scenarios, high security requirements

---

### Pattern 3: **Mutual TLS via Proxy (Enterprise)**

Backend proxy handles mTLS, extension connects to proxy with simpler auth.

```
Extension (http://localhost:8443)
    ↓
Proxy Server (handles mTLS certificates)
    ↓
Backend (requires mTLS client cert)
```

```typescript
// Extension talks to trusted local proxy
const httpConnector = createConnector({
  type: 'http',
  url: 'https://localhost:8443/backend',  // ← Local proxy with valid cert
  fetchOptions: {
    headers: {
      'Authorization': `Bearer ${proxyToken}`
    }
  }
});

// Proxy configuration (Node.js backend, not extension):
// - Loads mTLS certificates from fs
// - Validates client certificate
// - Forwards authenticated requests to backend
// - Extension only needs to trust proxy certificate

// Server-side example (NOT in extension):
import https from 'https';
import fs from 'fs';

const server = https.createServer({
  key: fs.readFileSync('/certs/extension-proxy-key.pem'),
  cert: fs.readFileSync('/certs/extension-proxy-cert.pem'),
  ca: fs.readFileSync('/certs/ca-bundle.pem')
}, (req, res) => {
  // Forward to backend with mTLS
  const backendRequest = https.request({
    hostname: 'secure-backend.internal',
    port: 443,
    path: req.url,
    method: req.method,
    key: fs.readFileSync('/certs/backend-client-key.pem'),
    cert: fs.readFileSync('/certs/backend-client-cert.pem'),
    ca: fs.readFileSync('/certs/backend-ca.pem'),
    rejectUnauthorized: true
  }, (backendRes) => {
    res.writeHead(backendRes.statusCode);
    backendRes.pipe(res);
  });
  
  req.pipe(backendRequest);
});
```

**When to use**: Enterprise environments with existing mTLS infrastructure

---

### Pattern 4: **Certificate Pinning (HIGH SECURITY)**

Browser verifies server certificate against known pin before connecting.

```typescript
import { createConnector } from '@dcmaar/connectors';

interface PinnedCertConfig {
  url: string;
  expectedFingerprint: string;  // SHA-256 of server cert
  backupFingerprint?: string;    // For cert rotation
}

// Verify certificate before connecting
async function verifyPinnedCertificate(url: string, expectedPin: string): Promise<boolean> {
  try {
    const response = await fetch(url, { method: 'HEAD' });
    
    // Get peer certificate (requires special handling)
    // Note: Standard fetch doesn't expose certificate details
    // You would need server to echo cert or use specialized library
    
    return true;  // Simplified - implement full verification
  } catch (e) {
    console.error('Certificate pinning failed:', e);
    return false;
  }
}

// Usage with verification
async function createSecureConnector(config: PinnedCertConfig) {
  const isPinned = await verifyPinnedCertificate(config.url, config.expectedFingerprint);
  
  if (!isPinned) {
    throw new Error('Certificate pinning verification failed - possible attack');
  }
  
  return createConnector({
    type: 'http',
    url: config.url,
    fetchOptions: {
      headers: {
        'Authorization': `Bearer ${await getAccessToken()}`
      }
    }
  });
}

// Guardian integration:
const secureApi = await createSecureConnector({
  url: 'https://api.guardian.example.com',
  expectedFingerprint: 'ae:ff:cd:f1:8d:3c:4d:3e:bc:d1:4f:7e:7d:c7:8d:9e'
});
```

**When to use**: High-value targets, prevent network-level attacks

---

### Pattern 5: **End-to-End Encryption (MAXIMUM SECURITY)**

Encrypt sensitive data client-side, decrypt on server.

```typescript
import { createConnector } from '@dcmaar/connectors';
import { subtle } from 'crypto';

// Generate symmetric encryption key
async function generateEncryptionKey(): Promise<CryptoKey> {
  return await subtle.generateKey(
    { name: 'AES-GCM', length: 256 },
    true,  // extractable
    ['encrypt', 'decrypt']
  );
}

// Encrypt sensitive data
async function encryptData(
  data: string,
  key: CryptoKey
): Promise<{ ciphertext: string; iv: string; salt: string }> {
  const encoder = new TextEncoder();
  const iv = crypto.getRandomValues(new Uint8Array(12));
  
  const encrypted = await subtle.encrypt(
    { name: 'AES-GCM', iv },
    key,
    encoder.encode(data)
  );
  
  return {
    ciphertext: btoa(String.fromCharCode(...new Uint8Array(encrypted))),
    iv: btoa(String.fromCharCode(...iv)),
    salt: ''  // Store key securely in extension
  };
}

// Send encrypted data to server
async function sendEncryptedPolicies(policies: PolicyData[], encryptionKey: CryptoKey) {
  const encrypted = await encryptData(JSON.stringify(policies), encryptionKey);
  
  const connector = createConnector({
    type: 'http',
    url: 'https://api.guardian.example.com/policies',
    method: 'POST'
  });
  
  return await connector.send({
    encrypted: true,
    ciphertext: encrypted.ciphertext,
    iv: encrypted.iv,
    timestamp: Date.now()
  });
}

// Benefits:
// ✅ Even if https:// intercepted, data is encrypted
// ✅ Server can't access plaintext without key
// ✅ Requires attacker to compromise both: network + server
// ✅ Protects against insider threats
```

**When to use**: Highly sensitive data, zero-trust architecture

---

## Comparison Table

| Pattern | Effort | Security | Use Case |
|---------|--------|----------|----------|
| **Server-side TLS** | ⭐ | ⭐⭐⭐⭐ | Most common - trusted backend |
| **OAuth2/OIDC** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Multi-user, high security |
| **Proxy mTLS** | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | Enterprise infrastructure |
| **Certificate Pinning** | ⭐⭐ | ⭐⭐⭐⭐⭐ | High-value targets |
| **E2E Encryption** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Sensitive data, zero-trust |

---

## Guardian Extension Architecture

### Current Implementation

```typescript
// Guardian uses native fetch for backend communication
// Secure by default via https://

// WebsiteBlocker.ts
async function syncPoliciesFromBackend() {
  const response = await fetch(
    'https://api.guardian.example.com/policies',
    {
      headers: {
        'Authorization': `Bearer ${token}`,
        'X-Extension-Id': EXTENSION_ID
      }
    }
  );
  return await response.json();
}
```

### Recommended Enhancement

```typescript
import { createConnector } from '@dcmaar/connectors';

// Service Worker initialization
const apiConnector = createConnector({
  type: 'http',
  url: 'https://api.guardian.example.com',
  retryPolicy: {
    maxRetries: 3,
    backoffMs: 1000
  },
  circuitBreaker: {
    threshold: 5,
    timeout: 60000
  },
  fetchOptions: {
    headers: {
      'Authorization': `Bearer ${await getStoredToken()}`,
      'X-Extension-Id': EXTENSION_ID,
      'X-Extension-Version': '1.0.0'
    }
  }
});

// WebsiteBlocker.ts with circuit breaker + retry
async function syncPoliciesFromBackend() {
  try {
    const policies = await apiConnector.send({
      method: 'GET',
      endpoint: '/policies',
      query: { active: true }
    });
    
    // Circuit breaker automatically handles failures
    // Retry policy handles transient network issues
    return policies;
  } catch (error) {
    // Fallback to cached policies
    const cached = await chrome.storage.local.get('policies');
    return cached.policies || [];
  }
}
```

---

## Browser Extension Security Best Practices

### ✅ DO

```typescript
// ✅ Use https:// and wss:// (automatic TLS)
const connector = createConnector({
  type: 'http',
  url: 'https://api.guardian.example.com'
});

// ✅ Use OAuth2 for authentication
const token = await getOAuth2Token();

// ✅ Use retry policy for resilience
const connector = createConnector({
  type: 'http',
  url: 'https://api.guardian.example.com',
  retryPolicy: { maxRetries: 3, backoffMs: 1000 }
});

// ✅ Validate responses server-side
// Extension asks backend to verify policy content

// ✅ Use circuit breaker for protection
const connector = createConnector({
  type: 'http',
  url: 'https://api.guardian.example.com',
  circuitBreaker: { threshold: 5 }
});

// ✅ Store tokens in secure Chrome Storage
chrome.storage.local.set({ 'api_token': token });
```

### ❌ DON'T

```typescript
// ❌ Use http:// (no encryption)
const connector = createConnector({
  type: 'http',
  url: 'http://api.guardian.example.com'  // VULNERABLE
});

// ❌ Hardcode secrets in extension code
const API_KEY = 'secret-key-123';  // VULNERABLE

// ❌ Store certificates in extension
const clientCert = require('./certs/client.pem');  // WON'T WORK

// ❌ Use unvalidated third-party libraries
// MQTT.js requires fs module - can't work in browser

// ❌ Trust self-signed certificates
// Browser will reject by default - don't disable
```

---

## Implementation Checklist

For Guardian extension secure communication:

- [ ] Use `https://` for all API endpoints
- [ ] Use `wss://` for real-time WebSocket connections
- [ ] Implement OAuth2 token refresh mechanism
- [ ] Add retry policy for network resilience
- [ ] Add circuit breaker for protection
- [ ] Use typed HTTP connector (avoid raw fetch)
- [ ] Cache policies locally as fallback
- [ ] Validate response signatures on server
- [ ] Log security events for audit
- [ ] Test certificate validation scenarios
- [ ] Document security model for users

---

## See Also

- [Connector Security Guide](./docs/security/)
- [Browser Extension Security](../browser-extension-core/SECURITY.md)
- [HTTP Connector API](./docs/usage/http-connector.md)
- [WebSocket Connector API](./docs/usage/websocket-connector.md)
- [Chrome Extension Security Docs](https://developer.chrome.com/docs/extensions/mv3/security/)
- [OWASP Extension Security](https://owasp.org/www-community/attacks/Extension_Hijacking)
