/**
 * Signing Service Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for kernel signing service
 * @doc.layer platform
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeEach, afterEach } from "vitest";
import {
  SigningService,
  type SignedKernelManifest,
  type SigningKeyPair,
} from "../signing-service";

describe("SigningService", () => {
  let signingService: SigningService;
  let testKeyPair: SigningKeyPair;

  beforeEach(() => {
    // Generate a test key pair
    signingService = new SigningService();
    testKeyPair = signingService.generateKeyPair();

    // Configure service with test keys
    signingService = new SigningService({
      privateKey: testKeyPair.privateKey,
      publicKey: testKeyPair.publicKey,
      keyId: testKeyPair.keyId,
    });
  });

  afterEach(() => {
    // Clean up
  });

  describe("generateKeyPair", () => {
    it("should generate a valid key pair", () => {
      const keyPair = signingService.generateKeyPair();

      expect(keyPair.privateKey).toBeDefined();
      expect(keyPair.publicKey).toBeDefined();
      expect(keyPair.keyId).toBeDefined();
      expect(keyPair.createdAt).toBeInstanceOf(Date);
      expect(keyPair.privateKey).toMatch(/-----BEGIN PRIVATE KEY-----/);
      expect(keyPair.publicKey).toMatch(/-----BEGIN PUBLIC KEY-----/);
    });

    it("should generate unique key IDs", () => {
      const keyPair1 = signingService.generateKeyPair();
      const keyPair2 = signingService.generateKeyPair();

      expect(keyPair1.keyId).not.toBe(keyPair2.keyId);
    });
  });

  describe("signManifest", () => {
    it("should sign a kernel manifest", () => {
      const manifest = {
        kernelId: "kernel-123",
        name: "Test Kernel",
        version: "1.0.0",
        description: "A test kernel",
        author: "Test Author",
        codeHash: "abc123",
        dependencies: ["dep1", "dep2"],
        createdAt: new Date().toISOString(),
      };

      const signed = signingService.signManifest(manifest);

      expect(signed.signature).toBeDefined();
      expect(signed.publicKey).toBe(testKeyPair.publicKey);
      expect(signed.algorithm).toBe("Ed25519");
      expect(signed.signedAt).toBeDefined();
    });

    it("should throw error if private key not configured", () => {
      const noKeyService = new SigningService();
      const manifest = {
        kernelId: "kernel-123",
        name: "Test Kernel",
        version: "1.0.0",
        description: "A test kernel",
        author: "Test Author",
        codeHash: "abc123",
        dependencies: [],
        createdAt: new Date().toISOString(),
      };

      expect(() => noKeyService.signManifest(manifest)).toThrow("Private key not configured");
    });
  });

  describe("verifyManifest", () => {
    it("should verify a valid signed manifest", () => {
      const manifest = {
        kernelId: "kernel-123",
        name: "Test Kernel",
        version: "1.0.0",
        description: "A test kernel",
        author: "Test Author",
        codeHash: "abc123",
        dependencies: [],
        createdAt: new Date().toISOString(),
      };

      const signed = signingService.signManifest(manifest);
      const result = signingService.verifyManifest(signed);

      expect(result.valid).toBe(true);
      expect(result.kernelId).toBe("kernel-123");
      expect(result.error).toBeUndefined();
    });

    it("should reject a tampered manifest", () => {
      const manifest = {
        kernelId: "kernel-123",
        name: "Test Kernel",
        version: "1.0.0",
        description: "A test kernel",
        author: "Test Author",
        codeHash: "abc123",
        dependencies: [],
        createdAt: new Date().toISOString(),
      };

      const signed = signingService.signManifest(manifest);
      signed.codeHash = "tampered"; // Tamper with the manifest

      const result = signingService.verifyManifest(signed);

      expect(result.valid).toBe(false);
      expect(result.error).toBeDefined();
    });

    it("should reject manifest with invalid signature", () => {
      const manifest = {
        kernelId: "kernel-123",
        name: "Test Kernel",
        version: "1.0.0",
        description: "A test kernel",
        author: "Test Author",
        codeHash: "abc123",
        dependencies: [],
        createdAt: new Date().toISOString(),
      };

      const signed = signingService.signManifest(manifest);
      signed.signature = "invalid-signature"; // Tamper with signature

      const result = signingService.verifyManifest(signed);

      expect(result.valid).toBe(false);
      expect(result.error).toBeDefined();
    });

    it("should reject manifest without public key", () => {
      const manifest = {
        kernelId: "kernel-123",
        name: "Test Kernel",
        version: "1.0.0",
        description: "A test kernel",
        author: "Test Author",
        codeHash: "abc123",
        dependencies: [],
        createdAt: new Date().toISOString(),
      };

      const signed = signingService.signManifest(manifest);
      // Create a modified manifest with invalid public key
      const signedWithoutKey = {
        ...signed,
        publicKey: "invalid-public-key",
      };

      const result = signingService.verifyManifest(signedWithoutKey);

      expect(result.valid).toBe(false);
    });
  });

  describe("canSign", () => {
    it("should return true when private key is configured", () => {
      expect(signingService.canSign()).toBe(true);
    });

    it("should return false when private key is not configured", () => {
      const noKeyService = new SigningService();
      expect(noKeyService.canSign()).toBe(false);
    });
  });

  describe("canVerify", () => {
    it("should always return true", () => {
      const noKeyService = new SigningService();
      expect(noKeyService.canVerify()).toBe(true);
    });
  });

  describe("getKeyId", () => {
    it("should return the configured key ID", () => {
      expect(signingService.getKeyId()).toBe(testKeyPair.keyId);
    });
  });

  describe("cross-key verification", () => {
    it("should verify signature from different key pair", () => {
      // Sign with first key pair
      const manifest = {
        kernelId: "kernel-123",
        name: "Test Kernel",
        version: "1.0.0",
        description: "A test kernel",
        author: "Test Author",
        codeHash: "abc123",
        dependencies: [],
        createdAt: new Date().toISOString(),
      };

      const signed = signingService.signManifest(manifest);

      // Verify with a different service instance (same public key)
      const verifyService = new SigningService({
        publicKey: testKeyPair.publicKey,
      });

      const result = verifyService.verifyManifest(signed);

      expect(result.valid).toBe(true);
    });

    it("should reject signature from different private key", () => {
      // Sign with first key pair
      const manifest = {
        kernelId: "kernel-123",
        name: "Test Kernel",
        version: "1.0.0",
        description: "A test kernel",
        author: "Test Author",
        codeHash: "abc123",
        dependencies: [],
        createdAt: new Date().toISOString(),
      };

      const signed = signingService.signManifest(manifest);

      // Verify with a different public key
      const differentKeyPair = signingService.generateKeyPair();
      const verifyService = new SigningService({
        publicKey: differentKeyPair.publicKey,
      });

      const result = verifyService.verifyManifest(signed);

      expect(result.valid).toBe(false);
    });
  });
});
