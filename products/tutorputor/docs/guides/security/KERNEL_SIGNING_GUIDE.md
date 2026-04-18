# Kernel Signing Guide

**Date:** 2026-04-17
**Status:** Implementation Complete

## Overview

Kernel signing provides cryptographic verification of kernel authenticity and integrity in the TutorPutor marketplace. This ensures that kernels published in the marketplace are from trusted authors and have not been tampered with.

## Implementation Details

### Signing Service

**Location:** `services/tutorputor-platform/src/core/crypto/signing-service.ts`

The `SigningService` provides:
- Ed25519 digital signature generation
- SHA-256 hashing for payload canonicalization
- Signature verification
- Key pair generation
- Key rotation support

### Kernel Schema Updates

The `KernelPlugin` model in `libs/tutorputor-core/prisma/schema.prisma` now includes:

```prisma
model KernelPlugin {
  // ... existing fields ...
  signature    String?  // Base64-encoded signature
  publicKey    String?  // Public key used for verification
  algorithm    String?  @default("ed25519") // Signing algorithm used
  signedAt     DateTime? // When the kernel was signed
  signerKeyId  String?  // Key identifier for the signing key
  codeHash     String?  // Hash of the kernel code for integrity verification
  // ...
}
```

## Usage

### Generating a Signing Key Pair

```typescript
import { signingService } from "@tutorputor/platform/core/crypto";

// Generate a new key pair
const keyPair = signingService.generateKeyPair();

console.log("Key ID:", keyPair.keyId);
console.log("Private Key:", keyPair.privateKey); // Keep this secret!
console.log("Public Key:", keyPair.publicKey);  // Share this for verification
```

### Signing a Kernel Manifest

```typescript
import { signingService } from "@tutorputor/platform/core/crypto";

// Create kernel manifest
const manifest = {
  kernelId: "kernel-123",
  name: "Math Problem Solver",
  version: "1.0.0",
  description: "Solves math problems using symbolic computation",
  author: "TutorPutor Team",
  codeHash: "abc123def456", // Hash of kernel code
  dependencies: ["symbolic-math", "expression-parser"],
  createdAt: new Date().toISOString(),
};

// Sign the manifest
const signedManifest = signingService.signManifest(manifest);

// Save to database
await prisma.kernelPlugin.update({
  where: { id: "kernel-123" },
  data: {
    signature: signedManifest.signature,
    publicKey: signedManifest.publicKey,
    algorithm: signedManifest.algorithm,
    signedAt: new Date(signedManifest.signedAt),
    signerKeyId: signingService.getKeyId(),
    codeHash: manifest.codeHash,
  },
});
```

### Verifying a Kernel Signature

```typescript
import { signingService } from "@tutorputor/platform/core/crypto";

// Load kernel from database
const kernel = await prisma.kernelPlugin.findUnique({
  where: { id: "kernel-123" },
});

if (!kernel?.signature) {
  throw new Error("Kernel is not signed");
}

// Create manifest from kernel data
const manifest = {
  kernelId: kernel.pluginId,
  name: kernel.name,
  version: kernel.version,
  description: kernel.description || "",
  author: kernel.author || "",
  codeHash: kernel.codeHash || "",
  dependencies: JSON.parse(kernel.dependencies || "[]"),
  createdAt: kernel.createdAt.toISOString(),
};

// Verify signature
const result = signingService.verifyManifest({
  ...manifest,
  signature: kernel.signature,
  publicKey: kernel.publicKey!,
  algorithm: kernel.algorithm || "ed25519",
  signedAt: kernel.signedAt?.toISOString() || "",
});

if (!result.valid) {
  throw new Error(`Invalid signature: ${result.error}`);
}

console.log("Kernel signature verified successfully");
```

## Configuration

### Environment Variables

```bash
# Signing keys (generate these once and keep secure)
SIGNING_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----"
SIGNING_PUBLIC_KEY="-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----"
SIGNING_KEY_ID="production-key-2024"
```

### Generating Keys

```bash
# Generate Ed25519 key pair using Node.js
node -e "
const crypto = require('crypto');
const { privateKey, publicKey } = crypto.generateKeyPairSync('ed25519');
console.log('PRIVATE_KEY:');
console.log(privateKey.export({ type: 'pkcs8', format: 'pem' }));
console.log('PUBLIC_KEY:');
console.log(publicKey.export({ type: 'spki', format: 'pem' }));
"
```

## Marketplace Integration

### Kernel Upload Flow

1. **Developer uploads kernel** → Kernel code is hashed
2. **Manifest is created** → Contains kernel metadata and code hash
3. **Manifest is signed** → Using platform signing key or developer's key
4. **Signature is stored** → In KernelPlugin table
5. **Kernel is published** → Only signed kernels can be published

### Kernel Download Flow

1. **User downloads kernel** → Kernel code and signature are retrieved
2. **Signature is verified** → Using public key from manifest
3. **Code hash is checked** → Ensures code hasn't been modified
4. **Kernel is executed** → Only if verification succeeds

### Marketplace Governance

1. **Review Queue** → Unsigned kernels cannot be published
2. **Approved Signers** → Only approved keys can sign kernels
3. **Key Rotation** → Rotate signing keys periodically
4. **Revocation** → Revoke compromised keys

## Security Considerations

### Key Management

- **Private keys must be kept secret** - Never commit to version control
- **Use environment variables or secret managers** - AWS Secrets Manager, HashiCorp Vault
- **Rotate keys regularly** - At least annually, or upon compromise
- **Backup keys securely** - Encrypted backups in multiple locations

### Signature Verification

- **Always verify before execution** - Never execute unsigned kernels
- **Check code hash** - Ensures code integrity
- **Verify algorithm** - Ensure expected algorithm is used
- **Log verification failures** - For security monitoring

### Performance Impact

- **Signing is fast** - Ed25519 is efficient (< 10ms per signature)
- **Verification is fast** - Similar performance to signing
- **Hash calculation** - SHA-256 is fast for typical kernel sizes
- **Database overhead** - Minimal (additional string fields)

## Testing

Unit tests are available at:
`services/tutorputor-platform/src/core/crypto/__tests__/signing-service.test.ts`

Run tests:
```bash
pnpm test -- signing-service.test.ts
```

## Migration Strategy

### Phase 1: Add Signature Fields (Current)
- Add signature fields to KernelPlugin schema
- Deploy signing service
- No enforcement yet

### Phase 2: Enable Signing for New Kernels
- Require signature for new kernel uploads
- Sign kernels automatically with platform key
- Update marketplace to show signature status

### Phase 3: Migrate Existing Kernels
- Batch job to sign existing kernels
- Process in batches to avoid performance impact
- Verify all kernels are signed

### Phase 4: Require Signature for Execution
- Reject unsigned kernels at execution time
- Only allow signed kernels in marketplace
- Remove legacy support

## Compliance Mapping

- **SOC 2 Type II:** Cryptographic controls for integrity verification
- **Supply Chain Security:** Verification of third-party code
- **Marketplace Trust:** Authenticity of published kernels

## Troubleshooting

### Common Issues

**"Private key not configured"**
- Ensure SIGNING_PRIVATE_KEY environment variable is set
- Check key format is correct PEM format

**"Signature verification failed"**
- Kernel may have been tampered with
- Check code hash matches actual code
- Verify public key is correct

**"Invalid public key format"**
- Ensure public key is in PEM format
- Check for line breaks and proper headers

### Debug Mode

Enable debug logging:
```bash
LOG_LEVEL=debug pnpm start
```

## Next Steps

1. Integrate signing service into kernel upload flow
2. Add signature verification to kernel execution
3. Implement marketplace governance for approved signers
4. Create key rotation procedures
5. Add monitoring for signature verification failures
6. Implement sandbox execution for kernel isolation
