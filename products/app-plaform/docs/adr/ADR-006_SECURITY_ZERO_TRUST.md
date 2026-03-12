# ADR-006: Security Architecture — Zero-Trust Model
## Project Siddhanta - Architectural Decision Record

**Status**: Accepted  
**Date**: 2026-03-08  
**Decision**: Adopt Zero-Trust security architecture with defense-in-depth  
**Impact**: High

---

# CONTEXT

## Problem Statement

Project Siddhanta handles sensitive financial data across multiple jurisdictions. The platform must:

- Protect against external and internal threats
- Comply with multiple regulatory frameworks (SEBON, SEBI, RBI, MiFID II)
- Support multi-tenant isolation
- Enable air-gapped deployment for restricted environments
- Provide forensic-grade audit trails
- Guard against data breaches, unauthorized access, and insider threats

## Constraints

1. **Regulatory**: Must meet SEBON, SEBI, RBI, MiFID II security requirements
2. **Multi-Tenant**: Complete data isolation between tenants
3. **Air-Gap**: Must operate without internet in restricted environments
4. **Performance**: Security controls must not degrade trading performance
5. **Auditability**: Every access and action must be traceable

---

# DECISION

## Architecture Choice

**Adopt a Zero-Trust security model: "Never trust, always verify" — with defense-in-depth across all architecture layers.**

### **Security Layers**

#### **Layer 1: Network Security**
- VPC isolation with strict security groups
- Web Application Firewall (WAF) with DDoS protection
- Network policies restricting inter-service communication
- TLS 1.3 for all external traffic
- mTLS for all internal service-to-service communication via Istio

#### **Layer 2: Identity & Authentication (K-01)**
- OAuth 2.0 + JWT token-based authentication
- Multi-factor authentication (TOTP, WebAuthn)
- SSO federation (SAML, OIDC) with external providers
- API key management for service accounts
- Session management with configurable timeouts
- Privileged Access Management (PAM) for admin operations

#### **Layer 3: Authorization**
- Role-Based Access Control (RBAC) with fine-grained permissions
- Attribute-Based Access Control (ABAC) for context-aware decisions
- OPA/Rego policy evaluation via K-03
- Resource-level permissions (create, read, update, delete, approve)
- Maker-checker enforcement for critical operations

#### **Layer 4: Data Security**
- Encryption at rest: AES-256-GCM for all databases
- Encryption in transit: TLS 1.3 / mTLS
- Field-level encryption for PII/sensitive data
- Row-Level Security (RLS) in PostgreSQL for tenant isolation
- Data classification and governance via K-08
- Secrets management via K-14 (multi-provider vault)

#### **Layer 5: Application Security**
- Input validation and sanitization
- SQL injection prevention (parameterized queries)
- XSS protection (Content Security Policy)
- CSRF protection (SameSite cookies, CSRF tokens)
- Rate limiting per tenant/endpoint via K-11
- Request size limits and schema validation

#### **Layer 6: Audit & Monitoring**
- Immutable audit trail via K-07 (hash-chained)
- Security event monitoring via K-06
- Anomaly detection for unauthorized access patterns
- Compliance logging for regulatory evidence
- Incident response integration

### **Zero-Trust Principles Applied**

| Principle | Implementation |
|-----------|---------------|
| **Verify Explicitly** | Every request authenticated + authorized, no session caching trust |
| **Least Privilege** | RBAC + ABAC, minimum necessary permissions |
| **Assume Breach** | mTLS between services, encrypted data at rest, segmented networks |
| **Never Trust Network** | Service mesh (Istio) enforces identity-based policies |
| **Continuous Validation** | Token refresh, session revalidation, anomaly detection |

---

# CONSEQUENCES

## Positive Consequences

### **Regulatory Compliance**
- Meets SEBON, SEBI, RBI, MiFID II security requirements
- Forensic-grade audit trail for regulatory investigations
- Data residency enforcement via K-08
- Complete access audit for compliance reporting

### **Risk Reduction**
- Defense-in-depth prevents single point of failure
- mTLS prevents man-in-the-middle attacks between services
- RLS prevents cross-tenant data leakage
- Encryption protects data at rest and in transit

### **Operational Security**
- Automated key rotation via K-14
- Break-glass access with full audit trail
- Incident response workflows with K-06 alerting
- Security scanning in CI/CD pipeline

## Negative Consequences

### **Performance Impact**
- mTLS adds ~0.5ms per service-to-service call
- JWT validation adds ~1ms per request
- Encryption/decryption adds CPU overhead
- **Mitigation**: Hardware-accelerated encryption, JWT caching, connection pooling

### **Operational Complexity**
- Certificate management across all services
- Policy management across multiple engines
- Security monitoring infrastructure investment
- **Mitigation**: Automated certificate lifecycle (K-14), centralized policy management (K-03)

---

# ALTERNATIVES CONSIDERED

## Option 1: Perimeter-Only Security
- **Rejected**: Insufficient for insider threats and multi-tenant environments
- **Risk**: Single breach bypasses all controls

## Option 2: VPN-Based Trust
- **Rejected**: Incompatible with cloud-native deployment; no service identity
- **Risk**: Lateral movement after initial access

## Option 3: Third-Party Security Platform
- **Rejected**: Vendor lock-in; insufficient customization for air-gap environments
- **Risk**: Dependency on external vendor; compliance gaps

---

# IMPLEMENTATION NOTES

## Technology
- **Identity**: K-01 IAM (OAuth 2.0, JWT, MFA)
- **Policy Engine**: K-03 Rules Engine (OPA/Rego)
- **Service Mesh**: Istio (mTLS, network policies, telemetry)
- **Secrets**: K-14 Secrets Management (HashiCorp Vault, AWS SM)
- **Encryption**: AES-256-GCM, TLS 1.3, Ed25519 signing
- **Audit**: K-07 Audit Framework (hash-chained, immutable)

## Security Testing Requirements
- Static Application Security Testing (SAST)
- Dynamic Application Security Testing (DAST)
- Dependency vulnerability scanning
- Container image scanning
- Penetration testing (annual)
- Red team exercises (biannual)

---

**Decision Makers**: Platform Architecture Team, Security Team  
**Reviewers**: CISO, Regulatory Compliance Team  
**Approval Date**: 2026-03-08
