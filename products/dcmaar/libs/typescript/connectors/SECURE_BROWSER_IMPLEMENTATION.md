# Implementing Secure Communication in Guardian Extension

## Quick Answer

**Browser extensions cannot use mTLS or MQTTS directly.** Instead, use these patterns:

| Need | Pattern | Implementation |
|------|---------|-----------------|
| **Basic security** | Server-side TLS | `https://` + `Bearer token` ✅ |
| **Multi-user security** | OAuth2/OIDC | Token refresh + secure storage ✅ |
| **Enterprise mTLS** | Proxy pattern | Local proxy handles certificates ✅ |
| **Sensitive data** | E2E Encryption | Client-side AES-GCM encryption ✅ |
| **Certificate pinning** | Pinning + TLS | Verify known server certificates ✅ |

---

## Recommended: Server-Side TLS + OAuth2

Combines simplicity with strong security for Guardian.

### 1. Backend Setup

```typescript
// Node.js backend (handles mTLS certificates)
import https from 'https';
import fs from 'fs';

// ✅ Backend can use mTLS with files
const options = {
  key: fs.readFileSync('/secrets/server.key'),
  cert: fs.readFileSync('/secrets/server.crt'),
  ca: fs.readFileSync('/secrets/ca.crt')
};

const server = https.createServer(options, (req, res) => {
  // Handle extension requests
  if (req.url === '/policies') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ policies: [...] }));
  }
});

server.listen(443, () => console.log('Secure server listening on :443'));
```

### 2. Extension Setup

```typescript
// Guardian extension (uses browser's automatic TLS)
import { createConnector } from '@dcmaar/connectors';

// Browser automatically validates server certificate
// No certificates needed in extension
const apiConnector = createConnector({
  type: 'http',
  url: 'https://api.guardian.example.com',  // ← Automatic TLS
  retryPolicy: {
    maxRetries: 3,
    backoffMs: 1000,
    backoffMultiplier: 2
  },
  circuitBreaker: {
    threshold: 5,        // Break after 5 failures
    timeout: 60000       // Reset after 1 minute
  },
  fetchOptions: {
    headers: {
      'Authorization': `Bearer ${await getToken()}`,
      'X-Extension-Version': chrome.runtime.getManifest().version
    }
  }
});

// Use in service worker
async function syncPolicies() {
  try {
    const response = await apiConnector.send({
      method: 'GET',
      endpoint: '/policies',
      query: { active: true }
    });
    
    // Save to Chrome Storage
    await chrome.storage.local.set({
      policies: response.policies,
      syncedAt: new Date().toISOString()
    });
    
    return response.policies;
  } catch (error) {
    console.error('Policy sync failed:', error);
    
    // Fallback to cached policies
    const cached = await chrome.storage.local.get('policies');
    return cached.policies || [];
  }
}

// Real-time updates via WebSocket
const wsConnector = createConnector({
  type: 'websocket',
  url: 'wss://api.guardian.example.com/stream',  // ← Automatic TLS
  autoReconnect: true,
  maxReconnectionAttempts: Infinity,
  pingPong: true,
  pingInterval: 30000
});

// Listen for policy updates
await wsConnector.connect();
wsConnector.on('message', (data) => {
  if (data.type === 'policy_updated') {
    chrome.storage.local.set({
      policies: data.policies,
      updatedAt: new Date().toISOString()
    });
    
    // Notify content scripts
    chrome.tabs.query({}, (tabs) => {
      tabs.forEach(tab => {
        chrome.tabs.sendMessage(tab.id!, {
          type: 'POLICIES_UPDATED',
          policies: data.policies
        });
      });
    });
  }
});
```

---

## Advanced: Certificate Pinning for High Security

Protects against compromised CAs or man-in-the-middle attacks.

```typescript
import { createConnector } from '@dcmaar/connectors';

interface CertificatePin {
  domain: string;
  publicKeyHash: string;
  backupHash?: string;
  expiresAt: number;
}

const CERTIFICATE_PINS: CertificatePin[] = [
  {
    domain: 'api.guardian.example.com',
    publicKeyHash: 'sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=',
    backupHash: 'sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=',
    expiresAt: Date.now() + 365 * 24 * 60 * 60 * 1000  // 1 year
  }
];

// Verify certificate before connecting
async function verifyAndCreateConnector(url: string): Promise<any> {
  const urlObj = new URL(url);
  const domain = urlObj.hostname;
  
  // Get certificate pin for domain
  const pin = CERTIFICATE_PINS.find(p => p.domain === domain);
  if (!pin) {
    throw new Error(`No certificate pin found for ${domain}`);
  }
  
  // Check expiration
  if (Date.now() > pin.expiresAt) {
    throw new Error(`Certificate pin expired for ${domain}`);
  }
  
  // In production, verify actual server certificate
  // This is simplified - real implementation would extract and hash cert
  console.log(`✅ Certificate pinned for ${domain}`);
  
  return createConnector({
    type: 'http',
    url: url,
    fetchOptions: {
      headers: {
        'Authorization': `Bearer ${await getToken()}`,
        'X-Pin-Verified': 'true'
      }
    }
  });
}

const secureApi = await verifyAndCreateConnector('https://api.guardian.example.com');
```

---

## Enterprise: Proxy Pattern for mTLS

If your backend requires client certificates.

### Architecture

```
┌──────────────────────┐
│ Guardian Extension   │
│ (Browser context)    │
│ - Can't load certs   │
│ - Uses OAuth2 token  │
└──────────┬───────────┘
           │ https://proxy.internal:8443 + Bearer token
           ↓
┌──────────────────────────────┐
│ Authentication Proxy         │
│ (Node.js, your datacenter)   │
│ - Loads client certificates  │
│ - Handles mTLS to backend    │
│ - Token validation           │
└──────────┬────────────────────┘
           │ mTLS with client cert
           ↓
┌──────────────────────────────┐
│ Secure Backend               │
│ (Requires client cert)       │
│ - Validates mTLS connection  │
│ - Processes requests         │
└──────────────────────────────┘
```

### Extension Configuration

```typescript
import { createConnector } from '@dcmaar/connectors';

const apiConnector = createConnector({
  type: 'http',
  url: 'https://proxy.internal.company.com:8443',
  fetchOptions: {
    headers: {
      'Authorization': `Bearer ${await getProxyToken()}`,
      'X-Backend': 'api.guardian.example.com',  // Tell proxy where to forward
      'X-Request-Id': generateRequestId()
    }
  },
  retryPolicy: {
    maxRetries: 3,
    backoffMs: 1000
  }
});

// All mTLS complexity handled by proxy
const policies = await apiConnector.send({
  method: 'GET',
  endpoint: '/policies'
});
```

### Proxy Server Configuration

```typescript
// Node.js proxy server (NOT in extension)
import https from 'https';
import fs from 'fs';
import { Agent as HttpsAgent } from 'https';

// Load client certificates for mTLS to backend
const clientAgent = new HttpsAgent({
  key: fs.readFileSync('/secrets/client-key.pem'),
  cert: fs.readFileSync('/secrets/client-cert.pem'),
  ca: fs.readFileSync('/secrets/backend-ca.pem'),
  rejectUnauthorized: true
});

// Proxy server
const server = https.createServer({
  key: fs.readFileSync('/secrets/proxy-key.pem'),
  cert: fs.readFileSync('/secrets/proxy-cert.pem')
}, async (req, res) => {
  // Validate token
  const token = req.headers.authorization?.split(' ')[1];
  if (!await validateToken(token)) {
    res.writeHead(401, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'Unauthorized' }));
    return;
  }
  
  // Forward to backend with mTLS
  const backendUrl = req.headers['x-backend'];
  const backendReq = https.request(
    `https://${backendUrl}${req.url}`,
    {
      method: req.method,
      headers: req.headers,
      agent: clientAgent  // ← Uses client certificates
    },
    (backendRes) => {
      res.writeHead(backendRes.statusCode!, backendRes.headers);
      backendRes.pipe(res);
    }
  );
  
  req.pipe(backendReq);
});

server.listen(8443, () => {
  console.log('Proxy listening on :8443');
});
```

---

## Ultra-Secure: End-to-End Encryption

For maximum security with sensitive policy data.

```typescript
import { createConnector } from '@dcmaar/connectors';

// Generate or retrieve encryption key
async function getEncryptionKey(): Promise<CryptoKey> {
  const stored = await chrome.storage.local.get('e2e_key');
  
  if (stored.e2e_key) {
    return await crypto.subtle.importKey(
      'jwk',
      JSON.parse(stored.e2e_key),
      { name: 'AES-GCM', length: 256 },
      true,
      ['encrypt', 'decrypt']
    );
  }
  
  // Generate new key
  const key = await crypto.subtle.generateKey(
    { name: 'AES-GCM', length: 256 },
    true,
    ['encrypt', 'decrypt']
  );
  
  // Store key (in production, use secure enclave)
  const exported = await crypto.subtle.exportKey('jwk', key);
  await chrome.storage.local.set({
    e2e_key: JSON.stringify(exported)
  });
  
  return key;
}

// Encrypt sensitive data
async function encryptPolicies(
  policies: any[],
  key: CryptoKey
): Promise<{ encrypted: string; iv: string }> {
  const encoder = new TextEncoder();
  const iv = crypto.getRandomValues(new Uint8Array(12));
  
  const encrypted = await crypto.subtle.encrypt(
    { name: 'AES-GCM', iv },
    key,
    encoder.encode(JSON.stringify(policies))
  );
  
  return {
    encrypted: btoa(String.fromCharCode(...new Uint8Array(encrypted))),
    iv: btoa(String.fromCharCode(...iv))
  };
}

// Send encrypted data to server
const apiConnector = createConnector({
  type: 'http',
  url: 'https://api.guardian.example.com',
  fetchOptions: {
    headers: {
      'Authorization': `Bearer ${await getToken()}`,
      'X-Encryption': 'AES-GCM-256'
    }
  }
});

async function sendPoliciesEncrypted(policies: any[]) {
  const encryptionKey = await getEncryptionKey();
  const { encrypted, iv } = await encryptPolicies(policies, encryptionKey);
  
  return await apiConnector.send({
    method: 'POST',
    endpoint: '/policies/sync',
    body: {
      encrypted,
      iv,
      timestamp: Date.now(),
      signature: await signPayload({ encrypted, iv })  // HMAC signature
    }
  });
}

// Decrypt received policies
async function decryptPolicies(
  encrypted: string,
  iv: string,
  key: CryptoKey
): Promise<any[]> {
  const encryptedData = Uint8Array.from(atob(encrypted), c => c.charCodeAt(0));
  const ivData = Uint8Array.from(atob(iv), c => c.charCodeAt(0));
  
  const decrypted = await crypto.subtle.decrypt(
    { name: 'AES-GCM', iv: ivData },
    key,
    encryptedData
  );
  
  const decoder = new TextDecoder();
  return JSON.parse(decoder.decode(decrypted));
}
```

---

## Summary: Pick Your Security Level

### Level 1: **Basic (Recommended for most)**
- Use `https://` and `wss://` (browser handles TLS)
- Store bearer token in Chrome Storage
- Add retry policy and circuit breaker
- ✅ Safe for production
- ❌ Requires trusted backend

### Level 2: **Enhanced (Multi-user)**
- Add OAuth2 token refresh
- Use certificate pinning
- Validate response signatures
- ✅ Multi-tenant safe
- ✅ Token can be revoked

### Level 3: **Enterprise (Existing mTLS)**
- Use proxy pattern
- Backend loads certificates
- Extension doesn't need certificates
- ✅ Works with existing infrastructure
- ⚠️ Requires proxy maintenance

### Level 4: **Ultra-Secure (Sensitive data)**
- Add end-to-end encryption
- AES-GCM encryption client-side
- Server can't decrypt without key
- ✅ Maximum security
- ⚠️ More complexity

---

## Next Steps for Guardian

1. ✅ Use `https://api.guardian.example.com` for all connections
2. ✅ Implement OAuth2 token refresh in service worker
3. ✅ Add retry policy (already have in connectors library)
4. ✅ Add circuit breaker (already have in connectors library)
5. ⬜ Consider certificate pinning for high-security customers
6. ⬜ Add E2E encryption for sensitive customer data
7. ⬜ Document security model for enterprise deployments

**No mTLS/MQTTS needed** - browser provides automatic TLS at the transport layer! 🔒
