# Browser Extension Security: The Complete Answer

## The Bottom Line

**Browser extensions CANNOT use mTLS or MQTTS directly** because they cannot access the file system to load certificates. However, **you don't need them**—browsers provide automatic, hardware-backed TLS at the transport layer.

## Why mTLS/MQTTS Don't Work

| Requirement | Browser | Node.js Backend |
|-------------|---------|-----------------|
| Load certificate files | ❌ No fs module | ✅ Has fs module |
| Access tls/crypto modules | ❌ Not available | ✅ Built-in |
| Manage certificates | ❌ Not possible | ✅ Easy |
| Use MQTT protocol | ❌ No mqtt client | ✅ mqtt.js available |

**Result**: Any attempt to use mTLS/MQTTS in browser extension will fail at runtime with "Cannot load certificate" or "Cannot find module" errors.

---

## The Solution: 5 Patterns for Secure Communication

### 1. **Server-Side TLS** ✅ RECOMMENDED

Browser automatically validates server certificate via HTTPS/WSS.

```typescript
// ✅ This just works - no configuration needed
const connector = createConnector({
  type: 'http',
  url: 'https://api.guardian.example.com'  // Browser handles TLS
});
```

**Why it's secure:**
- Browser validates certificate chain
- Prevents man-in-the-middle attacks
- Built into browser - can't be disabled
- Hardware-backed on modern OSes

**When to use:** 95% of cases - this is the standard

---

### 2. **OAuth2/OIDC** ✅ FOR MULTI-USER

Add token-based authentication on top of HTTPS.

```typescript
// Get token from OAuth2 provider
const token = await getOAuth2Token();

const connector = createConnector({
  type: 'http',
  url: 'https://api.guardian.example.com',
  fetchOptions: {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  }
});
```

**Why it's secure:**
- Tokens expire automatically
- Can be revoked server-side
- Multiple tokens per user
- Audit trail on server

**When to use:** Multi-tenant, require user management

---

### 3. **Proxy Pattern** ✅ FOR ENTERPRISE mTLS

Backend proxy handles certificates, extension uses simple token auth.

```
Extension (token) → Proxy (mTLS) → Secure Backend
```

**Why it's secure:**
- Extension doesn't need certificates
- Proxy handles all mTLS complexity
- Separation of concerns
- Works with existing infrastructure

**When to use:** Enterprise with mandatory mTLS backend

---

### 4. **Certificate Pinning** ✅ FOR HIGH-VALUE TARGETS

Verify server certificate matches known public key hash.

```typescript
// Verify certificate before each request
const isPinned = await verifyCertificatePin(
  'https://api.guardian.example.com',
  'sha256/AAAAAAA...'
);

if (!isPinned) throw new Error('Certificate compromised');
```

**Why it's secure:**
- Prevents compromised CA attacks
- Protects high-value targets
- Can be breached only by attacker with CA access

**When to use:** Government, finance, security-critical

---

### 5. **End-to-End Encryption** ✅ FOR ULTRA-SENSITIVE DATA

Encrypt on client, decrypt only on server.

```typescript
const encryptedPayload = await encryptData(
  sensitiveData,
  clientEncryptionKey
);

// Send encrypted - server can't read it
await connector.send({ encrypted: encryptedPayload });
```

**Why it's secure:**
- Server has no key to decrypt
- Only client and server know plaintext
- Protects against insider threats
- Works even if backend compromised

**When to use:** Highly sensitive data, zero-trust architecture

---

## Quick Comparison

| Pattern | Effort | Security | Cost | Use Case |
|---------|--------|----------|------|----------|
| Server-side TLS | ⭐ | ⭐⭐⭐⭐ | None | Standard APIs |
| OAuth2 | ⭐⭐ | ⭐⭐⭐⭐⭐ | Low | Multi-user |
| Proxy | ⭐⭐⭐ | ⭐⭐⭐⭐ | Medium | Enterprise |
| Pinning | ⭐⭐ | ⭐⭐⭐⭐⭐ | Low | High-value |
| E2E Encrypt | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Medium | Sensitive |

---

## Guardian Extension: Recommended Setup

### Phase 1: Secure Foundation (NOW)

```typescript
// Use https:// + Bearer token via OAuth2
const apiConnector = createConnector({
  type: 'http',
  url: 'https://api.guardian.example.com',
  retryPolicy: { maxRetries: 3, backoffMs: 1000 },
  circuitBreaker: { threshold: 5, timeout: 60000 },
  fetchOptions: {
    headers: {
      'Authorization': `Bearer ${oauth2Token}`
    }
  }
});
```

**Security:**
- ✅ Automatic TLS via HTTPS
- ✅ Token-based auth
- ✅ Automatic retry
- ✅ Resilience via circuit breaker
- **Threat model**: Prevents basic attacks

### Phase 2: Enhanced (OPTIONAL)

```typescript
// Add certificate pinning for high-security customers
const pinVerified = await verifyCertificatePin(
  'api.guardian.example.com',
  KNOWN_CERTIFICATE_PINS
);

if (!pinVerified) {
  throw new Error('Certificate validation failed');
}
```

**Security:**
- ✅ Everything from Phase 1
- ✅ Prevents CA compromise
- **Threat model**: Defeats compromised certificate authority

### Phase 3: Ultra-Secure (FUTURE)

```typescript
// Add E2E encryption for sensitive policy data
const encrypted = await encryptPolicies(
  policies,
  clientKey
);

await apiConnector.send({
  encrypted,
  timestamp: Date.now()
});
```

**Security:**
- ✅ Everything from Phase 2
- ✅ Server can't read policy data
- **Threat model**: Defeats backend compromise

---

## What Backend Developers Should Do

### ✅ DO: Handle mTLS Backend-Side

```typescript
// Node.js backend - handles client certificates
import https from 'https';
import fs from 'fs';

const server = https.createServer({
  key: fs.readFileSync('/secrets/server-key.pem'),
  cert: fs.readFileSync('/secrets/server-cert.pem'),
  ca: fs.readFileSync('/secrets/ca-bundle.pem'),
  rejectUnauthorized: true
}, (req, res) => {
  // Extension talks to this server via HTTPS
  // Browser automatically validates server certificate
});
```

### ✅ DO: Use Proxy for Client Certificates

```typescript
// If backend REQUIRES mTLS client certificate:
// Put a proxy between extension and backend

// Proxy configuration (Node.js)
const clientAgent = new https.Agent({
  key: fs.readFileSync('/secrets/client-key.pem'),
  cert: fs.readFileSync('/secrets/client-cert.pem'),
  ca: fs.readFileSync('/secrets/backend-ca.pem')
});

// Proxy receives simple bearer token from extension
// Proxy sends mTLS client cert to backend
```

### ❌ DON'T: Ask Extension to Load Certificates

Extension cannot:
- Load PEM files
- Read fs module
- Use tls module
- Manage certificates

---

## Common Mistakes to Avoid

### ❌ Mistake 1: Trying to use mqtt.js

```typescript
// ❌ WRONG - mqtt.js requires fs to load certificates
import mqtt from 'mqtt';
const client = mqtt.connect('mqtts://broker.example.com');
// Runtime error: Cannot find module 'fs'
```

**✅ Solution**: Use HTTP/WebSocket for data instead

```typescript
// ✅ CORRECT - Use HTTP connector
const connector = createConnector({
  type: 'http',
  url: 'https://api.example.com/data'
});
```

---

### ❌ Mistake 2: Using MQTT broker directly

```typescript
// ❌ WRONG - MQTT protocol not available in browser
const broker = 'mqtts://broker.example.com:8883';
// No MQTT client available in browser
```

**✅ Solution**: Use backend as MQTT consumer

```
Extension (HTTP)
    ↓
Backend HTTP API
    ↓
Backend MQTT client → MQTT broker
```

---

### ❌ Mistake 3: Disabling certificate validation

```typescript
// ❌ WRONG - Disables security
const connector = createConnector({
  type: 'http',
  url: 'https://api.example.com',
  fetchOptions: {
    headers: {
      // NEVER disable cert verification in browser
    }
  }
});

// Browser won't allow this anyway!
```

**✅ Solution**: Use valid certificates

```typescript
// ✅ CORRECT - Let browser validate automatically
const connector = createConnector({
  type: 'http',
  url: 'https://api-with-valid-cert.example.com'
});
```

---

### ❌ Mistake 4: Storing secrets in extension code

```typescript
// ❌ WRONG - Secrets visible in extension code
const API_KEY = 'sk_live_abc123def456';

const connector = createConnector({
  type: 'http',
  url: 'https://api.example.com',
  fetchOptions: {
    headers: {
      'X-API-Key': API_KEY  // EXPOSED
    }
  }
});
```

**✅ Solution**: Get token from secure endpoint

```typescript
// ✅ CORRECT - Get token at runtime
async function getToken() {
  const response = await fetch('https://auth.example.com/token', {
    method: 'POST',
    body: JSON.stringify({
      client_id: EXTENSION_CLIENT_ID,
      client_secret: EXTENSION_CLIENT_SECRET
    })
  });
  return (await response.json()).accessToken;
}

const connector = createConnector({
  type: 'http',
  url: 'https://api.example.com',
  fetchOptions: {
    headers: {
      'Authorization': `Bearer ${await getToken()}`
    }
  }
});
```

---

## Documentation Created

1. **[SECURE_COMMUNICATION_BROWSER.md](./SECURE_COMMUNICATION_BROWSER.md)**
   - Why mTLS doesn't work
   - 5 secure communication patterns
   - Code examples for each
   - Guardian integration guide

2. **[SECURE_BROWSER_IMPLEMENTATION.md](./SECURE_BROWSER_IMPLEMENTATION.md)**
   - Practical implementation examples
   - Backend proxy setup
   - Certificate pinning
   - E2E encryption
   - Security best practices

3. **[SECURE_COMMUNICATION_SETUP.md](../guardian/SECURE_COMMUNICATION_SETUP.md)**
   - Guardian extension service worker setup
   - Policy synchronization
   - Real-time WebSocket updates
   - Token management
   - Testing guide

---

## Summary for Guardian

### What We're Protecting

```
Extension ←→ Backend Server
     ↑          ↑
  Browser     HTTPS
  Validates   Validates
  Cert        Extension
```

### How It Works

1. **Extension makes HTTPS request** → Browser validates server certificate
2. **Server validates extension token** → Backend checks OAuth2 token
3. **Both authenticated** → Communication is secure

### What You Get

✅ Enterprise-grade security  
✅ No certificate management in extension  
✅ Automatic TLS via browser  
✅ Token-based authentication  
✅ Audit trail on server  
✅ Easy to test and maintain  

### No mTLS Needed

The browser provides TLS automatically at the transport layer:
- Client-side (browser): Validates server certificate
- Server-side (backend): Validates client token
- Result: Mutual authentication without mTLS

---

## Next Steps

1. ✅ Guardian uses `https://` endpoints (automatic TLS) ← **Done**
2. ✅ Implement OAuth2 token refresh in service worker ← **Documented**
3. ✅ Add retry policy and circuit breaker ← **Already in connectors**
4. ⬜ Test policy sync latency
5. ⬜ Monitor token refresh failures
6. ⬜ Document for customers

**No mTLS configuration needed!** 🎉

For questions or enterprise requirements, refer to the comprehensive documentation files.
