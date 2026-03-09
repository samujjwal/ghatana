# Browser Extension Security: Visual Architecture Guide

## Problem & Solution Overview

### ❌ The Problem

```
Browser Extension Developer: "How do I use mTLS/MQTTS?"
                                      ↓
                           Browser Engine
                           - No fs module
                           - No tls module
                           - No certificate loading
                                      ↓
                           ❌ FAIL: Runtime Error
```

**Error Messages:**
```
Cannot find module 'fs'
Cannot find module 'mqtt'
Certificate loading failed
mTLS handshake failed
```

---

### ✅ The Solution

```
Browser Extension Developer: "Use OAuth2 + Server TLS"
                                      ↓
                    Browser provides automatic TLS:
                    - Validates server certificate
                    - Encrypts connection
                    - Hardware-backed on modern OS
                                      ↓
                    ✅ SUCCESS: Secure Communication
```

---

## 5 Patterns Architecture Diagrams

### Pattern 1: Server-Side TLS (Recommended ⭐⭐⭐⭐⭐)

```
┌─────────────────────┐
│  Browser Extension  │
│  createConnector({  │
│    type: 'http',    │
│    url: 'https://...'
│  })                 │
└──────────┬──────────┘
           │ 1. Browser validates
           │    server certificate
           ↓
    ╔══════════════╗
    ║   HTTPS TLS  ║ ← Automatic
    ║   Encrypted  ║   No config needed
    ║   Connection ║
    ╚══════════════╝
           │ 2. Backend processes
           │    request
           ↓
┌──────────────────────────┐
│    Backend Server        │
│  - Serves HTTPS          │
│  - Has valid certificate │
│  - Processes requests    │
└──────────────────────────┘

Security: ⭐⭐⭐⭐ (Very Good)
Complexity: ⭐ (Simple)
Cost: None (free cert from Let's Encrypt)
```

**Guardian Implementation:**
```
Extension → https://api.guardian.example.com
           (Browser validates certificate)
           → Backend validates extension token
           ✅ Mutual authentication
```

---

### Pattern 2: OAuth2/OIDC (Multi-User ⭐⭐⭐⭐⭐)

```
┌─────────────────────────────┐
│   Browser Extension         │
│   1. Get token              │
│   2. Add Bearer auth        │
│   3. Send request           │
└──────────┬──────────────────┘
           │
           ↓
    ╔════════════════════╗
    ║ OAuth2 Provider    ║
    ║ (auth.example.com) ║
    ║ Validates: ID + PW ║
    ║ Returns: Token     ║
    ╚────────┬───────────╝
           ↓
┌─────────────────────────────┐
│   Extension (has token)     │
│   Add to header:            │
│   Authorization: Bearer X   │
└──────────┬──────────────────┘
           │ https://
           ↓
╔══════════════════════════════╗
║ Backend API                  ║
║ 1. Validate certificate ✓    ║
║ 2. Validate token ✓          ║
║ 3. Process request ✓         ║
║ 4. Log audit trail ✓         ║
╚══════════════════════════════╝

Security: ⭐⭐⭐⭐⭐ (Excellent)
Complexity: ⭐⭐⭐ (Medium)
Benefits: Token revocation, expiration, audit trail
```

**Timeline:**
```
Day 1: Token valid (8 hours)
       ✓ Extension can access API

Day 2: Token expired (24h)
       ✗ Extension requests new token
       ✓ OAuth provider checks user still authorized
       ✓ Returns new token
       ✓ Extension resumes operations

Day 3: User revoked extension access
       ✗ OAuth provider denies token renewal
       ✗ Extension can no longer access API
       ✓ No certificate needed - policy enforced server-side
```

---

### Pattern 3: Proxy Pattern (Enterprise mTLS ⭐⭐⭐⭐)

```
Problem: Backend REQUIRES client mTLS certificates
Solution: Proxy handles certificates, extension uses simple auth

┌─────────────────────────────┐
│   Browser Extension         │
│   (No certificate access)   │
│   Bearer token auth         │
└──────────┬──────────────────┘
           │ https:// + token
           ↓
    ╔════════════════════════════╗
    ║   Authentication Proxy     ║
    ║   (Node.js Server)         ║
    ║   - Validates extension    ║
    ║   - Loads mTLS cert (fs)   ║
    ║   - Forwards requests      ║
    ╚────────┬───────────────────╝
             │ mTLS + client cert
             ↓
    ┌────────────────────────────┐
    │   Secure Backend           │
    │   (Requires mTLS)          │
    │   - Validates cert         │
    │   - Processes request      │
    └────────────────────────────┘

Architecture:
┌─────────────────────────────────────┐
│ Extension talks to Proxy:           │
│   https://proxy.internal:8443       │
│   Bearer: extension_token           │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│ Proxy talks to Backend:             │
│   https://backend.internal:443      │
│   mTLS: client_cert + client_key    │
└─────────────────────────────────────┘

Security: ⭐⭐⭐⭐ (Very Good)
Complexity: ⭐⭐⭐⭐ (High)
Advantage: Works with existing mTLS infrastructure
```

**Proxy Code:**
```typescript
// Only in Node.js proxy (NOT in extension)
const clientAgent = new https.Agent({
  key: fs.readFileSync('/secrets/client-key.pem'),    ← Can use fs here
  cert: fs.readFileSync('/secrets/client-cert.pem'),  ← Can use fs here
  ca: fs.readFileSync('/secrets/ca.pem'),
  rejectUnauthorized: true
});

// Receives token from extension
// Uses mTLS to backend
```

---

### Pattern 4: Certificate Pinning (High-Value ⭐⭐⭐⭐⭐)

```
Scenario: Very high-value target
Protection: Verify server certificate matches known pin

Normal TLS:
┌────────────────────────────┐
│  Browser cert validation   │
│  - Check certificate chain │
│  - Verify CA signature     │
│  - Check expiration        │
│  ⚠️ Risk: Compromised CA   │
└────────────────────────────┘

Certificate Pinning:
┌────────────────────────────┐
│  Browser cert validation   │
│  + Pin verification       │
│  - Check certificate chain │
│  - Verify CA signature     │
│  - Check expiration        │
│  - ALSO: Verify public key │
│  hash matches known pin    │
│  ✓ Prevents CA compromise  │
└────────────────────────────┘

Flow:
1. Extension has known certificate pin:
   KNOWN_PIN = "sha256/AAAA..."

2. Before connecting:
   ACTUAL_PIN = calculatePin(serverCert)

3. Verify:
   if (ACTUAL_PIN === KNOWN_PIN) {
     ✓ Connect
   } else {
     ✗ Reject - possible attack
   }

Security: ⭐⭐⭐⭐⭐ (Maximum)
Complexity: ⭐⭐ (Moderate)
Attack Prevention: CA compromise, MITM with rogue cert
```

**Real-World Example:**
```
Day 1: Deploy Guardian with pin
       Pin: sha256/AbCdEf123456...
       ✓ Extension validates server certificate

Day 2: Attacker compromises Certificate Authority
       Attacker creates valid-looking certificate
       ✗ Extension pin check fails
       ✗ Connection rejected
       ✓ Guardian users protected

Day 3: Guardian administrators rotate certificate
       Update pin to: sha256/XyZ789abcd...
       ✓ Extension receives updated pin
       ✓ New certificate works
       ✓ Zero downtime
```

---

### Pattern 5: End-to-End Encryption (Maximum ⭐⭐⭐⭐⭐)

```
Scenario: Extremely sensitive data
Protection: Client-side encryption, server can't decrypt

┌──────────────────────────────────────────┐
│   Browser Extension                      │
│   Plaintext: { policies: [...] }         │
│                     ↓                    │
│   AES-GCM Encryption (client key)        │
│                     ↓                    │
│   Ciphertext: "aBcDeF123456..."          │
└──────────────────┬───────────────────────┘
                   │ https://
                   ↓
           ┌───────────────────┐
           │ Browser validates │
           │ server certificate│
           └───────────────────┘
                   │
                   ↓
        ╔═══════════════════════╗
        ║ HTTPS Encrypted       ║
        ║ (Double encryption)   ║
        ║ - TLS encryption      ║
        ║ - AES-GCM encryption  ║
        ╚═══════════════════════╝
                   │
                   ↓
┌──────────────────────────────────────────┐
│   Backend Server                         │
│   Receives: "aBcDeF123456..."            │
│   (Ciphertext only - can't decrypt)      │
│                                          │
│   Server capabilities:                   │
│   ✓ Store encrypted data                 │
│   ✓ Retrieve encrypted data              │
│   ✗ Read plaintext                       │
│   ✗ Modify policies                      │
│                                          │
│   Only client has key to decrypt!        │
└──────────────────────────────────────────┘

Threat Model Protection:
┌──────────────────────────────────┐
│ Threat               │ Protected? │
├──────────────────────┼────────────┤
│ Network MITM         │ ✓ TLS      │
│ Compromised CA       │ ✓ Pinning  │
│ Rogue server cert    │ ✓ Pinning  │
│ Compromised backend  │ ✓ E2E Enc. │
│ Insider attack       │ ✓ E2E Enc. │
│ Attacker has both?   │ ✓ Auth key │
└──────────────────────┴────────────┘

Security: ⭐⭐⭐⭐⭐ (Maximum)
Complexity: ⭐⭐⭐ (Complex)
Use Case: Highly sensitive data only
```

---

## Guardian Extension Architecture

### Current State

```
┌─────────────────────────┐
│ Content Script          │
│ - Analyzes website      │
│ - Calls: chrome.runtime │
└────────────┬────────────┘
             │ chrome.runtime.sendMessage()
             ↓
┌─────────────────────────┐
│ Service Worker          │
│ - Manages policies      │
│ - HTTP calls            │
│ - Stores data           │
└────────────┬────────────┘
             │ fetch() or HttpConnector
             ↓
        INSECURE (http or unencrypted)
```

### Recommended: Pattern 2 Setup

```
┌─────────────────────────┐
│ Content Script          │
│ - Check policy          │
│ - Block if needed       │
└────────────┬────────────┘
             │ MESSAGE
             ↓
┌────────────────────────────────────────┐
│ Service Worker (Secure)                │
│ 1. Initialized with:                   │
│    - HttpConnector                     │
│    - WebSocketConnector                │
│    - OAuth2 token                      │
│    - Retry policy                      │
│    - Circuit breaker                   │
│                                        │
│ 2. Sync policies:                      │
│    GET /policies (HTTPS)               │
│    Auth: Bearer token                  │
│                                        │
│ 3. Real-time updates:                  │
│    WebSocket (WSS)                     │
│    Auto-reconnect                      │
│                                        │
│ 4. Cache locally:                      │
│    Chrome Storage (encrypted OS level) │
└────────────┬───────────────────────────┘
             │ https:// + wss://
             │ (Automatic TLS)
             ↓
    ╔═════════════════════╗
    ║ Backend API         ║
    ║ - HTTPS certificate ║
    ║ - Token validation  ║
    ║ - Policy storage    ║
    ║ - WebSocket server  ║
    ╚═════════════════════╝

Security Layers:
1. TLS (Browser automatic)
2. OAuth2 (Token auth)
3. Retry (Resilience)
4. Circuit Breaker (Protection)
5. Local Cache (Fallback)
```

---

## Security Comparison Matrix

```
                    TLS   OAuth  Proxy  Pin  E2E
                    ───   ─────  ─────  ───  ───
Protection
├─ Network MITM       ✓      ✓      ✓     ✓    ✓
├─ CA Compromise      ✗      ✗      ✗     ✓    ✓
├─ Backend breach     ✗      ✓      ✓     ✓    ✓
├─ Insider threat     ✗      ✓      ✓     ✓    ✓
└─ Token theft        ✗      ✗      ✓     ✓    ✓

Complexity
├─ Setup              ⭐     ⭐⭐   ⭐⭐⭐  ⭐⭐  ⭐⭐⭐
├─ Maintenance        ⭐     ⭐⭐   ⭐⭐⭐  ⭐    ⭐⭐
└─ Debugging          ⭐     ⭐⭐   ⭐⭐⭐  ⭐⭐  ⭐⭐⭐

Performance
├─ Latency            ⭐     ⭐     ⭐⭐   ⭐    ⭐⭐
├─ Throughput         ⭐     ⭐     ⭐⭐   ⭐    ⭐⭐
└─ CPU Usage          ⭐     ⭐     ⭐     ⭐⭐  ⭐⭐⭐

Use Pattern 1 (TLS) for: Standard APIs
Use Pattern 2 (OAuth) for: Multi-user, any size
Use Pattern 3 (Proxy) for: Existing mTLS backend
Use Pattern 4 (Pin) for: High-value targets
Use Pattern 5 (E2E) for: Highly sensitive data
```

---

## Decision Flow

```
START: "How do I secure browser ↔ backend?"
│
├─ Does backend have HTTPS?
│  ├─ YES → Use Pattern 1 (TLS) ✓
│  └─ NO  → Get free cert (Let's Encrypt)
│
├─ Need multi-user support?
│  ├─ YES → Add Pattern 2 (OAuth2)
│  └─ NO  → Continue with Pattern 1
│
├─ Backend requires mTLS certs?
│  ├─ YES → Use Pattern 3 (Proxy)
│  └─ NO  → Continue
│
├─ High-value target?
│  ├─ YES → Add Pattern 4 (Pinning)
│  └─ NO  → Continue
│
├─ Extremely sensitive data?
│  ├─ YES → Add Pattern 5 (E2E Encryption)
│  └─ NO  → Continue
│
END: "You're done! Secure communication ready ✓"
```

---

## Key Insight

```
Traditional View (WRONG for browser):
┌─────────────────────┐
│ Client wants mTLS   │
│ Why? "Maximum       │
│ security"           │
└──────────┬──────────┘
           │
        ❌ Won't work
           ✓ Use proxy
           ✓ Use E2E encryption

Browser Reality (RIGHT):
┌────────────────────────┐
│ Browser HAS automatic  │
│ TLS via HTTPS/WSS      │
│ Add authentication     │
│ (OAuth2) on top        │
│ Result: Enterprise     │
│ security without       │
│ certificate files      │
└────────────────────────┘
           │
        ✓ Works perfectly
        ✓ Simpler than mTLS
        ✓ Better than mTLS
```

---

## Summary

```
mTLS/MQTTS in Browser:    ❌ Not possible - fs/tls not available
HTTPS/WSS + OAuth2:       ✅ Browser native - zero config
Certificate Pinning:      ✅ High security - verify known certs
Proxy Pattern:            ✅ Enterprise - proxy handles certs
E2E Encryption:           ✅ Maximum security - client encrypts
```

**For Guardian**: Use **Pattern 1 + 2** = HTTPS + OAuth2 ✓
