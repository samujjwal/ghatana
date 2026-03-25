# Security Documentation

> **Scope**: Security architecture for Media Processing Library  
> **Package**: `com.ghatana.media`  
> **Version**: 1.0.0

---

## Overview

The Media Processing Library (`com.ghatana.media`) implements defense-in-depth security for audio, video, and multimodal processing. This document describes the security architecture, encryption strategies, and compliance considerations.

---

## Security Architecture

### Threat Model

| Threat | Mitigation | Status |
|--------|-----------|--------|
| **Model theft** | File permissions, encryption at rest | ✅ Implemented |
| **Audio eavesdropping** | In-memory processing, no persistent storage | ✅ Implemented |
| **Profile data exposure** | AES-256-GCM encryption | ⚠️ Stub implementation |
| **DoS via large files** | Size limits, streaming processing | ✅ Implemented |
| **Inference poisoning** | Input validation, normalization | ✅ Implemented |
| **Side-channel attacks** | Constant-time operations where applicable | ⚠️ Partial |

---

## Encryption

### Profile Encryption (User Data)

User profiles contain voice models, adaptation data, and preferences. These are encrypted at rest.

**Algorithm**: AES-256-GCM
- **Key derivation**: PBKDF2 with 100,000 iterations
- **Key storage**: Environment variable or HSM (configurable)
- **Nonce**: 96-bit random per profile
- **Authentication**: Built-in GCM authentication tag

```java
// Profile encryption stub (to be implemented)
public class ProfileEncryption {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    
    public byte[] encrypt(byte[] profileData, byte[] key) {
        // Implementation: Generate IV, init cipher, encrypt, append auth tag
        // TODO: Full implementation with proper key management
    }
    
    public byte[] decrypt(byte[] encryptedData, byte[] key) {
        // Implementation: Extract IV, verify tag, decrypt
        // TODO: Full implementation
    }
}
```

### Model Encryption (ONNX Files)

ONNX model files can be encrypted to prevent unauthorized use.

**Recommendation**: Use AES-256-CBC with HMAC-SHA256 for integrity.

**Key Management**:
- Development: Environment variable `MODEL_ENCRYPTION_KEY`
- Production: HSM or cloud KMS (AWS KMS, Azure Key Vault, GCP KMS)
- Rotation: Annual key rotation with re-encryption

### Transport Security

**gRPC**: TLS 1.3 with mutual authentication (optional)

```yaml
# Example configuration
grpc:
  tls:
    enabled: true
    certChain: /etc/ssl/certs/server.crt
    privateKey: /etc/ssl/private/server.key
    clientAuth: REQUIRE  # or OPTIONAL, NONE
```

**HTTP**: TLS 1.3 via reverse proxy (Envoy, NGINX)

---

## Input Validation

### Audio Data Validation

```java
public void validateAudio(AudioData audio) {
    // Size limits (prevent DoS)
    long maxSizeBytes = 100 * 1024 * 1024; // 100 MB
    if (audio.data().length > maxSizeBytes) {
        throw new ValidationError("Audio exceeds maximum size of 100 MB");
    }
    
    // Duration limits
    if (audio.duration().toSeconds() > 300) { // 5 minutes
        throw new ValidationError("Audio exceeds maximum duration of 5 minutes");
    }
    
    // Sample rate validation
    if (audio.sampleRate() < 8000 || audio.sampleRate() > 192000) {
        throw new ValidationError("Invalid sample rate: " + audio.sampleRate());
    }
    
    // Format whitelist
    Set<AudioFormat> allowed = Set.of(AudioFormat.PCM, AudioFormat.WAV, AudioFormat.FLAC);
    if (!allowed.contains(audio.format())) {
        throw new ValidationError("Unsupported audio format: " + audio.format());
    }
}
```

### Image Data Validation

```java
public void validateImage(ImageData image) {
    // Size limits
    long maxSizeBytes = 50 * 1024 * 1024; // 50 MB
    if (image.data().length > maxSizeBytes) {
        throw new ValidationError("Image exceeds maximum size of 50 MB");
    }
    
    // Dimension limits
    if (image.width() > 4096 || image.height() > 4096) {
        throw new ValidationError("Image dimensions exceed maximum (4096x4096)");
    }
    
    // Format whitelist
    Set<ImageFormat> allowed = Set.of(
        ImageFormat.PNG, ImageFormat.JPEG, 
        ImageFormat.WEBP, ImageFormat.RAW
    );
    if (!allowed.contains(image.format())) {
        throw new ValidationError("Unsupported image format: " + image.format());
    }
}
```

---

## Access Control

### Profile Access

User profiles are isolated by user ID. The library does not implement authentication but expects callers to provide validated user context.

```java
public interface ProfileAccessControl {
    boolean canRead(String userId, String profileId);
    boolean canWrite(String userId, String profileId);
    boolean canDelete(String userId, String profileId);
}
```

### Model Access

Model files should have filesystem permissions:
- Owner: read-only (0400)
- Group: none
- Others: none

```bash
chmod 400 /models/*.onnx
chown media-service:media-service /models/*.onnx
```

---

## Audit Logging

### Events to Log

| Event | Data | Retention |
|-------|------|-----------|
| Transcription | User ID, audio hash (SHA-256), model ID, latency | 90 days |
| Synthesis | User ID, text hash, voice ID, latency | 90 days |
| Detection | User ID, image hash, model ID, object count | 90 days |
| Profile access | User ID, profile ID, operation, timestamp | 1 year |
| Model loading | Model ID, timestamp, success/failure | 1 year |

**Log Format**: JSON with structured fields

```json
{
  "timestamp": "2026-03-25T10:30:00Z",
  "event": "TRANSCRIPTION",
  "user_id": "user_123",
  "audio_hash": "sha256:abc123...",
  "model_id": "whisper-base",
  "latency_ms": 1250,
  "success": true
}
```

---

## Compliance

### GDPR (EU)

- **Data minimization**: Only process necessary audio/text
- **Right to erasure**: Profile deletion API
- **Data portability**: Export profiles to standard format
- **Consent**: Caller must obtain user consent before processing

### HIPAA (US Healthcare)

⚠️ **WARNING**: PHI (Protected Health Information) should not be processed without:
- BAA (Business Associate Agreement) with cloud provider
- End-to-end encryption
- Access logging
- Data retention policies

**Recommendation**: Use on-premises deployment for PHI.

### SOC 2

- **Security**: Encryption at rest and in transit
- **Availability**: Health checks, metrics, alerting
- **Processing integrity**: Input validation, error handling
- **Confidentiality**: Access control, encryption
- **Privacy**: Profile isolation, audit logging

---

## Security Checklist

### Deployment

- [ ] Model files have restrictive permissions (0400)
- [ ] TLS certificates configured for gRPC/HTTP
- [ ] Environment variables for secrets (not in config files)
- [ ] Network policies restrict access to inference ports
- [ ] Container runs as non-root user

### Development

- [ ] No hardcoded credentials
- [ ] Input validation on all public APIs
- [ ] Error messages don't leak internal details
- [ ] Dependencies scanned for vulnerabilities (OWASP Dependency Check)
- [ ] No sensitive data in logs

### Operations

- [ ] Health endpoint exposed for monitoring
- [ ] Metrics collection for anomaly detection
- [ ] Log aggregation to SIEM
- [ ] Incident response playbook
- [ ] Regular security audits

---

## Vulnerability Disclosure

**Contact**: security@ghatana.internal

**Process**:
1. Submit vulnerability report
2. Acknowledgment within 48 hours
3. Investigation and fix timeline
4. Coordinated disclosure

---

## References

- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [NIST Cybersecurity Framework](https://www.nist.gov/cyberframework)
- [GDPR Text](https://gdpr-info.eu/)
- [HIPAA Security Rule](https://www.hhs.gov/hipaa/for-professionals/security/index.html)

---

*Document Version: 1.0.0*  
*Last Updated: 2026-03-25*
