# mTLS/MQTTS in Browser Extensions - Quick Reference

## ❌ Can't Use mTLS/MQTTS Directly

Browser extensions **CANNOT** use:
- ❌ `fs` module (no file system access)
- ❌ `tls` module (not available in browser)
- ❌ `mqtt` library (requires fs for certificates)
- ❌ Custom certificates (no way to install them)

**Error you'll get:**
```
Cannot find module 'fs'
Cannot find module 'tls'
Cannot find module 'mqtt'
Cannot load certificate file
```

---

## ✅ Use These Instead

### For HTTPS/REST APIs

```typescript
const connector = createConnector({
  type: 'http',
  url: 'https://api.example.com'
});
```

**Why:**
- Browser handles TLS automatically
- No certificate files needed
- Hardware-backed security
- No additional configuration

---

### For Real-Time Data

```typescript
const connector = createConnector({
  type: 'websocket',
  url: 'wss://api.example.com/stream'
});
```

**Why:**
- WebSocket Secure (WSS) = TLS + WebSocket
- Browser handles certificate validation
- Perfect for real-time updates
- Automatic reconnection available

---

### If Backend REQUIRES mTLS Client Certs

Use **Proxy Pattern**:

```
Browser Extension
    ↓ (https + token)
Proxy Server (Node.js)
    ↓ (mTLS with cert)
Secure Backend
```

**Proxy Configuration:**
```typescript
// In your Node.js proxy (NOT in extension)
import https from 'https';
import fs from 'fs';

const clientAgent = new https.Agent({
  key: fs.readFileSync('/secrets/client-key.pem'),
  cert: fs.readFileSync('/secrets/client-cert.pem'),
  ca: fs.readFileSync('/secrets/ca.pem'),
  rejectUnauthorized: true
});

// Proxy receives token from extension
// Proxy uses mTLS to backend
```

---

## Security Levels (Pick One)

### Level 1: Basic (Recommended)
```typescript
createConnector({
  type: 'http',
  url: 'https://api.example.com'
})
```
✅ TLS automatic
✅ Easy to implement
⚠️ Requires valid SSL certificate

### Level 2: Authenticated
```typescript
createConnector({
  type: 'http',
  url: 'https://api.example.com',
  fetchOptions: {
    headers: {
      'Authorization': `Bearer ${token}`
    }
  }
})
```
✅ Everything from Level 1
✅ Token-based auth
✅ Multi-user support

### Level 3: Resilient
```typescript
createConnector({
  type: 'http',
  url: 'https://api.example.com',
  retryPolicy: {
    maxRetries: 3,
    backoffMs: 1000
  },
  circuitBreaker: {
    threshold: 5
  }
})
```
✅ Everything from Level 2
✅ Automatic retry
✅ Circuit breaker protection

### Level 4: Pinned
```typescript
const isPinned = await verifyCertificatePin(
  'https://api.example.com',
  KNOWN_PIN
);
if (!isPinned) throw new Error('Cert compromised');

// Then create connector
```
✅ Everything from Level 3
✅ Prevents CA compromise
✅ Protects high-value targets

### Level 5: E2E Encrypted
```typescript
const encrypted = await encryptData(data, key);
await connector.send({ encrypted });
```
✅ Everything from Level 4
✅ Server can't read data
✅ Zero-trust model

---

## Guardian Extension: Recommended Setup

```typescript
import { createConnector } from '@dcmaar/connectors';

// Initialize in service worker
const apiConnector = createConnector({
  type: 'http',
  url: 'https://api.guardian.example.com',
  
  // Resilience
  retryPolicy: {
    maxRetries: 3,
    backoffMs: 1000
  },
  circuitBreaker: {
    threshold: 5,
    timeout: 60000
  },
  
  // Authentication
  fetchOptions: {
    headers: {
      'Authorization': `Bearer ${await getOAuth2Token()}`,
      'X-Extension-Id': chrome.runtime.id,
      'X-Extension-Version': chrome.runtime.getManifest().version
    }
  }
});

// Use for policy sync
const policies = await apiConnector.send({
  method: 'GET',
  endpoint: '/policies'
});

// Real-time updates via WebSocket
const wsConnector = createConnector({
  type: 'websocket',
  url: 'wss://api.guardian.example.com/stream',
  autoReconnect: true,
  pingPong: true
});

wsConnector.on('message', (update) => {
  if (update.type === 'policy_updated') {
    syncPolicies();
  }
});
```

**Security:**
- ✅ Automatic HTTPS/WSS (browser handles TLS)
- ✅ OAuth2 token auth
- ✅ Automatic retry on network failure
- ✅ Circuit breaker protection
- ✅ Real-time updates
- **No mTLS files needed**

---

## Comparison: What Works Where

| Technology | Browser | Node.js Backend |
|------------|---------|-----------------|
| HTTP/HTTPS | ✅ Works | ✅ Works |
| WebSocket/WSS | ✅ Works | ✅ Works |
| OAuth2/OIDC | ✅ Works | ✅ Works |
| TLS Certificates | ❌ No | ✅ Yes |
| mTLS (client certs) | ❌ No | ✅ Yes |
| MQTT/MQTTS | ❌ No | ✅ Yes |
| File System | ❌ No | ✅ Yes |
| Certificate Pinning | ✅ Possible | ✅ Easy |
| E2E Encryption | ✅ Yes | ✅ Yes |

---

## DO's and DON'Ts

### ✅ DO
```typescript
// Use https://
const url = 'https://api.example.com';

// Use wss://
const url = 'wss://api.example.com/stream';

// Use OAuth2 tokens
const token = await getOAuth2Token();

// Store tokens securely
chrome.storage.local.set({ token });

// Use circuit breaker
circuitBreaker: { threshold: 5 }

// Use retry policy
retryPolicy: { maxRetries: 3 }

// Validate responses
if (!response.policies) throw new Error('Invalid');
```

### ❌ DON'T
```typescript
// Use http://
const url = 'http://api.example.com';

// Use MQTT directly
import mqtt from 'mqtt';  // Won't work

// Load certificate files
const cert = require('./cert.pem');  // Won't work

// Hardcode secrets
const API_KEY = 'secret123';

// Disable certificate validation
// (Browser won't allow it anyway)

// Store certificates in extension
const clientCert = fs.readFileSync('client.pem');

// Trust self-signed certs
// (Browser rejects automatically)
```

---

## Real-World Guardian Example

### Problem
Backend requires TLS and wants real-time policy updates.

### Solution

```typescript
// Service Worker Setup
const policies = await createConnector({
  type: 'http',
  url: 'https://api.guardian.example.com/policies',
  fetchOptions: {
    headers: {
      'Authorization': `Bearer ${oauth2Token}`
    }
  }
}).send({});

// Real-time updates
const ws = await createConnector({
  type: 'websocket',
  url: 'wss://api.guardian.example.com/updates',
  autoReconnect: true
}).connect();

ws.on('message', (msg) => {
  if (msg.type === 'policies_changed') {
    syncPolicies();
  }
});

// Content Script: Check Policy
chrome.runtime.sendMessage({
  type: 'CHECK_POLICY',
  url: 'https://example.com'
}, (response) => {
  if (response.blocked) {
    document.body.innerHTML = '⛔ Access Blocked';
  }
});
```

**Result:**
- ✅ Secure HTTPS/WSS (automatic TLS)
- ✅ Token authentication
- ✅ Real-time updates
- ✅ No certificate management
- ✅ Enterprise-grade security

---

## Key Takeaway

| Want | Browser | How |
|------|---------|-----|
| TLS Encryption | ✅ Automatic | Use `https://` and `wss://` |
| mTLS Certs | ❌ Not possible | Use proxy pattern or OAuth2 |
| MQTT | ❌ Not possible | Use HTTP/WebSocket instead |
| Security | ✅ Yes | Browser handles TLS automatically |

**Browser provides enterprise-grade TLS automatically—no configuration needed!** 🔐

---

## See Also

- [Secure Communication Overview](./SECURE_COMMUNICATION_SUMMARY.md)
- [5 Patterns for Secure Communication](./SECURE_COMMUNICATION_BROWSER.md)
- [Implementation Guide](./SECURE_BROWSER_IMPLEMENTATION.md)
- [Guardian Setup Guide](../guardian/SECURE_COMMUNICATION_SETUP.md)
- [Connector API](./docs/usage/)
