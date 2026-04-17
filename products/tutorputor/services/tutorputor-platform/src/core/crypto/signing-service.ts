/**
 * Kernel Signing Service
 *
 * Provides Ed25519 digital signature capabilities for kernel manifests.
 * Ensures kernel authenticity and integrity in the marketplace.
 *
 * @doc.type service
 * @doc.purpose Digital signature verification for kernel manifests
 * @doc.layer platform
 * @doc.pattern SigningService
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import crypto from "crypto";

const logger = createStandaloneLogger({ component: "SigningService" });

/**
 * Signed kernel manifest
 */
export interface SignedKernelManifest {
  kernelId: string;
  name: string;
  version: string;
  description: string;
  author: string;
  codeHash: string;
  dependencies: string[];
  createdAt: string;
  signature: string;
  publicKey: string;
  algorithm: string;
  signedAt: string;
}

/**
 * Signing key pair
 */
export interface SigningKeyPair {
  privateKey: string;
  publicKey: string;
  keyId: string;
  createdAt: Date;
}

/**
 * Signature verification result
 */
export interface VerificationResult {
  valid: boolean;
  kernelId: string;
  signerKeyId: string;
  signedAt: string;
  error?: string;
}

/**
 * Signing Service Configuration
 */
export interface SigningServiceConfig {
  privateKey?: string;
  publicKey?: string;
  keyId?: string;
}

/**
 * Kernel Signing Service
 */
export class SigningService {
  private signingAlgorithm = "ed25519";
  private hashAlgorithm = "SHA256";
  private privateKey: string | null = null;
  private publicKey: string | null = null;
  private keyId: string;

  constructor(config: SigningServiceConfig = {}) {
    this.keyId = config.keyId || process.env.SIGNING_KEY_ID || "default";

    // Load keys from environment or config
    this.privateKey = config.privateKey || process.env.SIGNING_PRIVATE_KEY || null;
    this.publicKey = config.publicKey || process.env.SIGNING_PUBLIC_KEY || null;

    if (!this.privateKey && !this.publicKey) {
      logger.warn("No signing keys configured. Service will only verify signatures, not sign.");
    }

    // Validate key format if provided
    if (this.privateKey && !this.isValidKey(this.privateKey)) {
      throw new Error("Invalid private key format");
    }
    if (this.publicKey && !this.isValidKey(this.publicKey)) {
      throw new Error("Invalid public key format");
    }
  }

  /**
   * Sign a kernel manifest
   */
  signManifest(manifest: Omit<SignedKernelManifest, "signature" | "publicKey" | "algorithm" | "signedAt">): SignedKernelManifest {
    if (!this.privateKey) {
      throw new Error("Private key not configured. Cannot sign manifests.");
    }

    // Create canonical payload for signing
    const payload = this.createCanonicalPayload(manifest);

    // Sign the payload
    const signature = this.sign(payload, this.privateKey);

    const signedManifest: SignedKernelManifest = {
      ...manifest,
      signature: signature,
      publicKey: this.publicKey!,
      algorithm: this.signingAlgorithm,
      signedAt: new Date().toISOString(),
    };

    logger.info({
      kernelId: manifest.kernelId,
      keyId: this.keyId,
    }, "Kernel manifest signed");

    return signedManifest;
  }

  /**
   * Verify a signed kernel manifest
   */
  verifyManifest(signedManifest: SignedKernelManifest): VerificationResult {
    try {
      // Extract the public key from the manifest
      const publicKey = signedManifest.publicKey;

      if (!publicKey) {
        return {
          valid: false,
          kernelId: signedManifest.kernelId,
          signerKeyId: "unknown",
          signedAt: signedManifest.signedAt,
          error: "Public key not found in manifest",
        };
      }

      // Validate public key format
      if (!this.isValidKey(publicKey)) {
        return {
          valid: false,
          kernelId: signedManifest.kernelId,
          signerKeyId: "unknown",
          signedAt: signedManifest.signedAt,
          error: "Invalid public key format",
        };
      }

      // Create canonical payload
      const payload = this.createCanonicalPayload(signedManifest);

      // Verify signature
      const isValid = this.verify(payload, signedManifest.signature, publicKey);

      if (!isValid) {
        return {
          valid: false,
          kernelId: signedManifest.kernelId,
          signerKeyId: this.keyId,
          signedAt: signedManifest.signedAt,
          error: "Signature verification failed",
        };
      }

      logger.info({
        kernelId: signedManifest.kernelId,
        valid: true,
      }, "Kernel manifest verified");

      return {
        valid: true,
        kernelId: signedManifest.kernelId,
        signerKeyId: this.keyId,
        signedAt: signedManifest.signedAt,
      };
    } catch (error) {
      logger.error({
        kernelId: signedManifest.kernelId,
        error: error instanceof Error ? error.message : String(error),
      }, "Manifest verification error");

      return {
        valid: false,
        kernelId: signedManifest.kernelId,
        signerKeyId: "unknown",
        signedAt: signedManifest.signedAt,
        error: error instanceof Error ? error.message : String(error),
      };
    }
  }

  /**
   * Generate a new signing key pair
   */
  generateKeyPair(): SigningKeyPair {
    const { privateKey, publicKey } = crypto.generateKeyPairSync("ed25519");

    const keyPair: SigningKeyPair = {
      privateKey: privateKey.export({ type: "pkcs8", format: "pem" }) as string,
      publicKey: publicKey.export({ type: "spki", format: "pem" }) as string,
      keyId: this.generateKeyId(),
      createdAt: new Date(),
    };

    logger.info({
      keyId: keyPair.keyId,
    }, "New signing key pair generated");

    return keyPair;
  }

  /**
   * Check if the service can sign manifests
   */
  canSign(): boolean {
    return !!this.privateKey;
  }

  /**
   * Check if the service can verify signatures
   */
  canVerify(): boolean {
    return true; // Can always verify with provided public keys
  }

  /**
   * Get the current key ID
   */
  getKeyId(): string {
    return this.keyId;
  }

  /**
   * Create canonical payload for signing
   */
  private createCanonicalPayload(manifest: Omit<SignedKernelManifest, "signature" | "publicKey" | "algorithm" | "signedAt">): string {
    // Sort keys and create deterministic string representation
    const sorted = Object.keys(manifest)
      .sort()
      .reduce((acc, key) => {
        const value = (manifest as Record<string, unknown>)[key];
        acc[key] = typeof value === "object" ? JSON.stringify(value) : String(value);
        return acc;
      }, {} as Record<string, string>);

    return JSON.stringify(sorted);
  }

  /**
   * Sign data with Ed25519
   */
  private sign(data: string, privateKey: string): string {
    const sign = crypto.createSign(this.hashAlgorithm);
    sign.update(data);
    sign.end();

    const signature = sign.sign(privateKey);
    return signature.toString("base64");
  }

  /**
   * Verify signature with Ed25519
   */
  private verify(data: string, signature: string, publicKey: string): boolean {
    try {
      const verify = crypto.createVerify(this.hashAlgorithm);
      verify.update(data);
      verify.end();

      const signatureBuffer = Buffer.from(signature, "base64");
      return verify.verify(publicKey, signatureBuffer);
    } catch (error) {
      logger.error({
        error: error instanceof Error ? error.message : String(error),
      }, "Signature verification error");
      return false;
    }
  }

  /**
   * Validate key format (PEM format check)
   */
  private isValidKey(key: string): boolean {
    // Basic PEM format validation
    const pemRegex = /^-----BEGIN ([A-Z ]+)-----\n([\s\S]+)\n-----END \1-----$/;
    return pemRegex.test(key.trim());
  }

  /**
   * Generate a unique key ID
   */
  private generateKeyId(): string {
    return `key-${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
  }
}

/**
 * Singleton instance
 */
const config: SigningServiceConfig = {};
if (process.env.SIGNING_PRIVATE_KEY) config.privateKey = process.env.SIGNING_PRIVATE_KEY;
if (process.env.SIGNING_PUBLIC_KEY) config.publicKey = process.env.SIGNING_PUBLIC_KEY;
if (process.env.SIGNING_KEY_ID) config.keyId = process.env.SIGNING_KEY_ID;

export const signingService = new SigningService(config);
