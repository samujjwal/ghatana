# Browser Extension Security Documentation Index

## Quick Answer

**Q: How do we ensure secure communication with mTLS/MQTTS from within a browser extension?**

**A: You can't use mTLS/MQTTS directly.** Browser extensions cannot access the file system to load certificates. Instead, use these approaches:

1. **Server-side TLS** (Recommended) - Browser automatically validates server certificate
2. **OAuth2 Authentication** - Token-based auth on top of HTTPS
3. **Proxy Pattern** - Proxy handles mTLS to backend
4. **Certificate Pinning** - Verify known server certificates
5. **E2E Encryption** - Client-side encryption for sensitive data

---

## Documentation Files

### 🚀 Quick Start (5 minutes)

**→ [MTLS_MQTTS_QUICK_REFERENCE.md](./MTLS_MQTTS_QUICK_REFERENCE.md)**
- Why mTLS doesn't work in browser
- 5 approaches at a glance
- Code snippets
- Guardian example
- DO's and DON'Ts
- **Best for**: Quick answer, decision making

---

### 📚 Deep Dive (30 minutes)

**→ [SECURE_COMMUNICATION_SUMMARY.md](./SECURE_COMMUNICATION_SUMMARY.md)**
- Why mTLS/MQTTS can't be used
- Complete explanation of 5 patterns
- Security comparison table
- Guardian recommended setup (3 phases)
- Common mistakes
- **Best for**: Understanding full context

---

### 🔒 5 Security Patterns Explained

**→ [SECURE_COMMUNICATION_BROWSER.md](./SECURE_COMMUNICATION_BROWSER.md)**
- **Pattern 1**: Server-side TLS (recommended)
- **Pattern 2**: OAuth2/OIDC (multi-user)
- **Pattern 3**: Proxy Pattern (enterprise mTLS)
- **Pattern 4**: Certificate Pinning (high security)
- **Pattern 5**: End-to-End Encryption (maximum security)
- Detailed code examples for each
- Comparison and use cases
- **Best for**: Learning all approaches

---

### 💻 Implementation Guide

**→ [SECURE_BROWSER_IMPLEMENTATION.md](./SECURE_BROWSER_IMPLEMENTATION.md)**
- Server-side TLS + OAuth2 setup (Recommended)
- Advanced: Certificate pinning implementation
- Enterprise: Proxy pattern setup
- Ultra-secure: E2E encryption implementation
- Complete code examples
- Production checklist
- **Best for**: Hands-on implementation

---

### 🛠️ Guardian Extension Setup

**→ [../guardian/SECURE_COMMUNICATION_SETUP.md](../guardian/SECURE_COMMUNICATION_SETUP.md)**
- Service worker initialization
- Policy synchronization
- Real-time WebSocket updates
- Token management
- Message handler for content scripts
- Security considerations
- Testing guide
- Deployment checklist
- **Best for**: Guardian extension developers

---

### 📖 Connector Library Documentation

**→ [QUICKSTART.md](./QUICKSTART.md)**
- Connector library overview
- Available connector types
- Browser vs Node.js differences
- Building and testing
- **Best for**: General connector usage

**→ [BROWSER_CONNECTORS.md](./BROWSER_CONNECTORS.md)**
- Restricted connector types for browser
- Configuration reference
- Tree-shaking benefits
- Migration guide
- **Best for**: Browser-specific connector usage

**→ [BROWSER_IMPLEMENTATION_SUMMARY.md](./BROWSER_IMPLEMENTATION_SUMMARY.md)**
- Browser entry point details
- Implementation summary
- Configuration files explained
- Testing the build
- **Best for**: Understanding browser build setup

---

## Decision Tree

### "How do I secure browser-to-backend communication?"

1. **Does your backend already have HTTPS certificate?**
   - YES → Use **Pattern 1: Server-side TLS** (Recommended)
     - Read: [MTLS_MQTTS_QUICK_REFERENCE.md](./MTLS_MQTTS_QUICK_REFERENCE.md)
   - NO → Get HTTPS certificate first (free from Let's Encrypt)

2. **Do you need to support multiple users?**
   - YES → Add **Pattern 2: OAuth2** on top of TLS
     - Read: [SECURE_BROWSER_IMPLEMENTATION.md](./SECURE_BROWSER_IMPLEMENTATION.md)
   - NO → Pattern 1 is sufficient

3. **Does your backend REQUIRE client mTLS certificates?**
   - YES → Use **Pattern 3: Proxy Pattern**
     - Backend proxy handles mTLS
     - Read: [SECURE_BROWSER_IMPLEMENTATION.md](./SECURE_BROWSER_IMPLEMENTATION.md) (Enterprise section)
   - NO → Proceed with Patterns 1-2

4. **Is this a high-value target (gov, finance, security)?**
   - YES → Add **Pattern 4: Certificate Pinning**
     - Read: [SECURE_COMMUNICATION_BROWSER.md](./SECURE_COMMUNICATION_BROWSER.md)
   - NO → Patterns 1-2 sufficient

5. **Do you handle highly sensitive data?**
   - YES → Add **Pattern 5: E2E Encryption**
     - Read: [SECURE_BROWSER_IMPLEMENTATION.md](./SECURE_BROWSER_IMPLEMENTATION.md) (Ultra-Secure section)
   - NO → Stop here - you're done

---

## By Role

### Security Engineer

Start here:
1. [SECURE_COMMUNICATION_SUMMARY.md](./SECURE_COMMUNICATION_SUMMARY.md) - Overview
2. [SECURE_COMMUNICATION_BROWSER.md](./SECURE_COMMUNICATION_BROWSER.md) - All 5 patterns
3. [SECURE_BROWSER_IMPLEMENTATION.md](./SECURE_BROWSER_IMPLEMENTATION.md) - Implementation options

### Backend Developer

Start here:
1. [MTLS_MQTTS_QUICK_REFERENCE.md](./MTLS_MQTTS_QUICK_REFERENCE.md) - What doesn't work and why
2. [SECURE_BROWSER_IMPLEMENTATION.md](./SECURE_BROWSER_IMPLEMENTATION.md#enterprise-proxy-pattern-for-mtls) - Proxy pattern
3. [../guardian/SECURE_COMMUNICATION_SETUP.md](../guardian/SECURE_COMMUNICATION_SETUP.md#authentication-token-management) - Token management

### Frontend/Extension Developer

Start here:
1. [../guardian/SECURE_COMMUNICATION_SETUP.md](../guardian/SECURE_COMMUNICATION_SETUP.md) - Guardian setup
2. [SECURE_BROWSER_IMPLEMENTATION.md](./SECURE_BROWSER_IMPLEMENTATION.md#quick-start-server-side-tls--oauth2) - Implementation
3. [MTLS_MQTTS_QUICK_REFERENCE.md](./MTLS_MQTTS_QUICK_REFERENCE.md) - Quick reference

### DevOps/Ops

Start here:
1. [MTLS_MQTTS_QUICK_REFERENCE.md](./MTLS_MQTTS_QUICK_REFERENCE.md) - Overview
2. [SECURE_BROWSER_IMPLEMENTATION.md](./SECURE_BROWSER_IMPLEMENTATION.md#enterprise-proxy-pattern-for-mtls) - Proxy setup
3. [../guardian/SECURE_COMMUNICATION_SETUP.md](../guardian/SECURE_COMMUNICATION_SETUP.md#deployment-checklist) - Deployment checklist

---

## Key Concepts

### Why Browser Can't Use mTLS

| Component | Browser | Backend |
|-----------|---------|---------|
| Load certificate files | ❌ No fs module | ✅ Yes |
| Access TLS library | ❌ Via HTTPS only | ✅ Direct |
| Use MQTT protocol | ❌ No mqtt.js | ✅ Yes |
| Manage certificates | ❌ Not possible | ✅ Easy |

**Result**: mTLS/MQTTS won't work in browser extension.

### What Works Instead

Browser automatically provides:
- ✅ **TLS via HTTPS** - Validates server certificate
- ✅ **TLS via WSS** - WebSocket with encryption
- ✅ **OAuth2** - Token-based authentication
- ✅ **Certificate Pinning** - Verify known certs
- ✅ **Encryption** - AES-GCM for sensitive data

### Guardian's Advantage

With `@dcmaar/connectors`:
- ✅ 4 browser-compatible connector types (http, websocket, filesystem, native)
- ✅ Tree-shaking for minimal bundle
- ✅ Built-in retry policy
- ✅ Built-in circuit breaker
- ✅ Built-in rate limiter
- ✅ Built-in metrics collection

---

## Common Questions Answered

### Q1: Can I use mTLS in browser extension?
**A**: No. Browser has no file system access for certificates.
- **Read**: [MTLS_MQTTS_QUICK_REFERENCE.md](./MTLS_MQTTS_QUICK_REFERENCE.md)

### Q2: What's better, mTLS or OAuth2?
**A**: For browser: OAuth2. For backend: mTLS. For both: Use both (OAuth2 + mTLS proxy).
- **Read**: [SECURE_COMMUNICATION_BROWSER.md](./SECURE_COMMUNICATION_BROWSER.md)

### Q3: How do I handle backend that requires client certificates?
**A**: Use proxy pattern. Proxy loads certificates, extension uses simple token auth.
- **Read**: [SECURE_BROWSER_IMPLEMENTATION.md](./SECURE_BROWSER_IMPLEMENTATION.md#enterprise-proxy-pattern-for-mtls)

### Q4: Is HTTPS enough for security?
**A**: For basic use, yes. For high-security, add OAuth2 + certificate pinning.
- **Read**: [SECURE_COMMUNICATION_SUMMARY.md](./SECURE_COMMUNICATION_SUMMARY.md)

### Q5: How do I encrypt sensitive data?
**A**: Use client-side AES-GCM encryption. Server gets encrypted blob only.
- **Read**: [SECURE_BROWSER_IMPLEMENTATION.md](./SECURE_BROWSER_IMPLEMENTATION.md#ultra-secure-end-to-end-encryption)

### Q6: How do I protect against CA compromise?
**A**: Use certificate pinning. Verify known certificate public key hash.
- **Read**: [SECURE_BROWSER_IMPLEMENTATION.md](./SECURE_BROWSER_IMPLEMENTATION.md#advanced-certificate-pinning-for-high-security)

### Q7: How do I implement for Guardian?
**A**: See setup guide with working service worker code.
- **Read**: [../guardian/SECURE_COMMUNICATION_SETUP.md](../guardian/SECURE_COMMUNICATION_SETUP.md)

---

## Implementation Checklist

### Phase 1: Basic Security (Week 1)
- [ ] Backend: Deploy HTTPS certificate
- [ ] Extension: Use `https://` and `wss://` URLs
- [ ] Backend: Implement `/auth/token` endpoint
- [ ] Extension: Get token and use Bearer auth
- [ ] Test: Verify HTTPS works in extension

### Phase 2: Production Hardening (Week 2)
- [ ] Backend: Implement token refresh
- [ ] Extension: Add retry policy
- [ ] Extension: Add circuit breaker
- [ ] Extension: Cache policies locally
- [ ] Test: Verify resilience with network failures

### Phase 3: Enhanced Security (Week 3)
- [ ] Backend: Set up proxy if mTLS needed
- [ ] Extension: Implement certificate pinning
- [ ] Extension: Add metrics collection
- [ ] Test: Verify certificate validation

### Phase 4: Maximum Security (Week 4)
- [ ] Extension: Implement E2E encryption
- [ ] Backend: Validate encrypted payloads
- [ ] Test: Verify encryption works
- [ ] Deploy: Production release

---

## Related Resources

- [Chrome Extension Security Docs](https://developer.chrome.com/docs/extensions/mv3/security/)
- [OWASP Extension Security](https://owasp.org/www-community/attacks/Extension_Hijacking)
- [RFC 8446 - TLS 1.3](https://tools.ietf.org/html/rfc8446)
- [OAuth 2.0 Security](https://tools.ietf.org/html/rfc6749#section-10)
- [Connector Library Docs](./docs/)

---

## Get Started Now

### 1. If you have 5 minutes
→ Read [MTLS_MQTTS_QUICK_REFERENCE.md](./MTLS_MQTTS_QUICK_REFERENCE.md)

### 2. If you have 30 minutes
→ Read [SECURE_COMMUNICATION_SUMMARY.md](./SECURE_COMMUNICATION_SUMMARY.md)

### 3. If you need to implement
→ Read [../guardian/SECURE_COMMUNICATION_SETUP.md](../guardian/SECURE_COMMUNICATION_SETUP.md)

### 4. If you need advanced setup
→ Read [SECURE_BROWSER_IMPLEMENTATION.md](./SECURE_BROWSER_IMPLEMENTATION.md)

---

**Latest Update**: November 22, 2025  
**Status**: ✅ Complete and production-ready  
**Version**: 1.0.0
