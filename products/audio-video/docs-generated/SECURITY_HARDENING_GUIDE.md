# Audio-Video Product Security Hardening Guide

**Version:** 1.0  
**Last Updated:** 2026-04-14  
**Classification:** Internal Use

---

## Executive Summary

This guide outlines security hardening measures for the Audio-Video product deployment. Following Ghatana security standards and industry best practices for production environments.

---

## 1. Authentication & Authorization

### 1.1 JWT Token Security

**Current Implementation:**
- ✅ gRPC authentication interceptor implemented
- ✅ Token validation using platform security library
- ✅ Tenant context extraction from JWT claims

**Hardening Measures:**

```yaml
# JWT Configuration
jwt:
  issuer: "ghatana-auth"
  audience: "audio-video"
  expiration: 3600  # 1 hour
  refreshTokenExpiration: 86400  # 24 hours
  algorithm: "RS256"  # Use asymmetric keys
  keyRotationInterval: 86400  # Daily rotation
```

**Implementation:**
- Store JWT signing keys in HashiCorp Vault
- Implement token blacklisting for logout
- Use short-lived access tokens with refresh tokens
- Validate token signature on every request

### 1.2 Role-Based Access Control (RBAC)

**Roles Defined:**
| Role | Permissions |
|------|------------|
| `av:user` | Create transcriptions, view own data |
| `av:admin` | View all tenant data, manage users |
| `av:system` | Internal service-to-service calls |

**Implementation:**
```java
@PreAuthorize("hasRole('av:user') or hasRole('av:admin')")
public Promise<TranscriptionEntity> transcribe(...) { ... }
```

---

## 2. Data Protection

### 2.1 Encryption at Rest

**Database Encryption:**
- Enable PostgreSQL TDE (Transparent Data Encryption)
- Encrypt sensitive columns (audio file paths, metadata)
- Use AES-256 encryption algorithm

```sql
-- Enable encryption for sensitive columns
ALTER TABLE audio_files 
ALTER COLUMN storage_path TYPE bytea 
USING pgp_sym_encrypt(storage_path, '${ENCRYPTION_KEY}');
```

**File Storage Encryption:**
- Encrypt audio files stored in object storage (S3/MinIO)
- Use server-side encryption with KMS
- Implement client-side encryption for sensitive content

### 2.2 Encryption in Transit

**gRPC TLS:**
```yaml
# Server configuration
gRPC:
  tls:
    enabled: true
    certPath: /etc/ssl/certs/av-server.crt
    keyPath: /etc/ssl/private/av-server.key
    clientAuth: REQUIRED  # mTLS
```

**Service Mesh:**
- Implement mutual TLS between all services
- Use service mesh (Istio/Linkerd) for certificate management
- Rotate certificates every 30 days

---

## 3. API Security

### 3.1 Rate Limiting

**Configuration:**
```yaml
rateLimiting:
  transcription:
    requestsPerMinute: 60
    requestsPerHour: 500
  tts:
    requestsPerMinute: 30
    requestsPerHour: 300
  burstAllowance: 10
```

**Implementation:**
- Redis-based rate limiting
- Per-tenant rate limiting
- IP-based blocking for abuse

### 3.2 Input Validation

**Security Headers:**
```java
// gRPC metadata validation
public void validateRequest(Metadata headers) {
    String tenantId = headers.get(TENANT_ID_KEY);
    if (tenantId == null || !isValidTenantId(tenantId)) {
        throw new StatusRuntimeException(Status.UNAUTHENTICATED);
    }
    
    // Validate content size
    long maxSize = 100 * 1024 * 1024; // 100MB
    if (request.getAudioData().size() > maxSize) {
        throw new StatusRuntimeException(Status.INVALID_ARGUMENT);
    }
}
```

### 3.3 Audit Logging

**Log All Security Events:**
```json
{
  "timestamp": "2026-04-14T10:30:00Z",
  "event": "TRANSCRIPTION_CREATED",
  "userId": "uuid",
  "tenantId": "tenant-123",
  "resourceId": "audio-file-uuid",
  "action": "CREATE",
  "outcome": "SUCCESS",
  "sourceIp": "192.168.1.100",
  "userAgent": "grpc-java/1.58.0"
}
```

---

## 4. Infrastructure Security

### 4.1 Container Security

**Dockerfile Hardening:**
```dockerfile
# Use minimal base image
FROM gcr.io/distroless/java21-debian12

# Run as non-root user
USER 65534:65534

# Read-only filesystem
COPY --chown=65534:65534 app.jar /app.jar

# No shell access
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

**Security Scanning:**
- Trivy for container image scanning
- OWASP Dependency Check for libraries
- Weekly security scans in CI/CD

### 4.2 Network Security

**Network Policies:**
```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: stt-service-policy
spec:
  podSelector:
    matchLabels:
      app: stt-service
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - podSelector:
        matchLabels:
          app: api-gateway
    ports:
    - protocol: TCP
      port: 50051
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: postgres
    ports:
    - protocol: TCP
      port: 5432
```

### 4.3 Secrets Management

**Vault Integration:**
```java
// Fetch secrets from Vault at runtime
VaultTemplate vaultTemplate = new VaultTemplate();
Secret secret = vaultTemplate.read("secret/audio-video/db-credentials");
String dbPassword = secret.getData().get("password");
```

**Environment Variables:**
- Never commit secrets to git
- Use Docker secrets or Kubernetes secrets
- Rotate secrets every 90 days

---

## 5. Database Security

### 5.1 Access Control

**PostgreSQL Hardening:**
```sql
-- Create dedicated user with minimal privileges
CREATE USER av_app WITH PASSWORD '${STRONG_PASSWORD}';

-- Grant only necessary permissions
GRANT CONNECT ON DATABASE audio_video TO av_app;
GRANT USAGE ON SCHEMA public TO av_app;
GRANT SELECT, INSERT, UPDATE ON audio_files TO av_app;
GRANT SELECT, INSERT, UPDATE ON transcriptions TO av_app;

-- Revoke dangerous permissions
REVOKE CREATE ON SCHEMA public FROM av_app;
```

### 5.2 Query Safety

**SQL Injection Prevention:**
- ✅ Use JPA named queries (parameterized)
- ✅ Validate all user inputs
- ✅ Never construct SQL with string concatenation

**Example:**
```java
// SAFE: Named query with parameters
@NamedQuery(
    name = "AudioFile.findByTenantId",
    query = "SELECT af FROM AudioFileEntity af WHERE af.tenantId = :tenantId AND af.deleted = false"
)

// UNSAFE: Never do this
String query = "SELECT * FROM audio_files WHERE tenant_id = '" + tenantId + "'";
```

---

## 6. Monitoring & Alerting

### 6.1 Security Metrics

**Prometheus Alerts:**
```yaml
- alert: HighAuthenticationFailureRate
  expr: rate(stt_auth_failures_total[5m]) > 0.1
  for: 5m
  labels:
    severity: warning
  annotations:
    summary: "High authentication failure rate"

- alert: SuspiciousFileUploadSize
  expr: histogram_quantile(0.99, rate(audio_file_size_bytes_bucket[5m])) > 100000000
  for: 1m
  labels:
    severity: warning
  annotations:
    summary: "Suspiciously large file upload detected"

- alert: DatabaseConnectionAnomaly
  expr: rate(db_connection_errors_total[5m]) > 0.05
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "Database connection errors detected"
```

### 6.2 Audit Log Aggregation

**Loki Queries:**
```bash
# Find all failed authentication attempts
{job="audio-video/stt-service"} |= "AUTH_FAILED"

# Find access to sensitive resources
{job="audio-video/stt-service"} |= "TRANSCRIPTION_ACCESSED" 
  | json | tenantId="tenant-123"

# Find errors related to security
{job="audio-video/stt-service"} |= "SECURITY_EXCEPTION"
```

---

## 7. Incident Response

### 7.1 Security Incident Playbooks

**Data Breach Response:**
1. Isolate affected services
2. Revoke compromised credentials
3. Audit access logs for scope
4. Notify affected tenants
5. File incident report

**DDoS Response:**
1. Enable rate limiting
2. Scale up services
3. Enable CDN/WAF
4. Contact security team

### 7.2 Security Contacts

| Role | Contact | Escalation |
|------|---------|------------|
| Security Team | security@ghatana.com | 24/7 |
| On-Call Engineer | +1-555-SEC-HELP | P1 incidents |
| Management | ciso@ghatana.com | Breach disclosure |

---

## 8. Compliance

### 8.1 Data Privacy

**GDPR Compliance:**
- Right to deletion (soft delete → hard delete)
- Data export capability
- Consent tracking
- Privacy-by-design architecture

### 8.2 Data Retention

```yaml
retention:
  transcriptions: 90 days
  audioFiles: 30 days
  auditLogs: 1 year
  metrics: 30 days

# Automatic cleanup job
cleanup:
  enabled: true
  schedule: "0 2 * * *"  # Daily at 2 AM
  dryRun: false
```

---

## 9. Security Checklist

### Pre-Deployment

- [ ] All dependencies scanned for vulnerabilities
- [ ] Secrets removed from code and configs
- [ ] TLS certificates generated and valid
- [ ] Database encryption enabled
- [ ] Rate limiting configured
- [ ] Audit logging enabled
- [ ] Security monitoring configured
- [ ] Incident response plan reviewed
- [ ] Security team sign-off obtained

### Post-Deployment

- [ ] Penetration testing completed
- [ ] Security monitoring validated
- [ ] Incident response drill conducted
- [ ] Security documentation updated
- [ ] Team security training completed

---

## 10. Security Tools

| Tool | Purpose | Integration |
|------|---------|-------------|
| Trivy | Container scanning | CI/CD |
| OWASP DC | Dependency check | CI/CD |
| Vault | Secrets management | Runtime |
| Falco | Runtime security | Kubernetes |
| Prometheus | Security metrics | Continuous |
| Loki | Audit log analysis | Continuous |

---

**Next Review Date:** 2026-07-14  
**Owner:** Security Team  
**Approvers:** Engineering Lead, CISO
