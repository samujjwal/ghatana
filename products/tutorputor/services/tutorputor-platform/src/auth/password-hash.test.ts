/**
 * Password Hash Verification Tests
 *
 * Tests for scrypt-based password hashing and verification functions.
 * These are critical security functions that must work correctly.
 *
 * @doc.type test
 * @doc.purpose Verify password hashing and verification security functions
 * @doc.layer core
 * @doc.pattern Unit Test
 */
import { describe, it, expect, beforeEach } from "vitest";
import { hashPassword, verifyPassword } from "./index.js";

describe("Password Hash Verification", () => {
  describe("hashPassword", () => {
    it("should generate a hash with salt and key", async () => {
      const password = "test-password-123";
      const hash = await hashPassword(password);

      expect(hash).toBeDefined();
      expect(hash).toContain(":");
      const [salt, key] = hash.split(":");
      expect(salt).toHaveLength(64); // 32 bytes * 2 (hex)
      expect(key).toHaveLength(128); // 64 bytes * 2 (hex)
    });

    it("should generate different hashes for the same password", async () => {
      const password = "same-password";
      const hash1 = await hashPassword(password);
      const hash2 = await hashPassword(password);

      expect(hash1).not.toBe(hash2);
    });
  });

  describe("verifyPassword", () => {
    it("should verify correct password", async () => {
      const password = "correct-password";
      const hash = await hashPassword(password);

      const isValid = await verifyPassword(password, hash);
      expect(isValid).toBe(true);
    });

    it("should reject incorrect password", async () => {
      const password = "correct-password";
      const wrongPassword = "wrong-password";
      const hash = await hashPassword(password);

      const isValid = await verifyPassword(wrongPassword, hash);
      expect(isValid).toBe(false);
    });

    it("should reject malformed hash", async () => {
      const password = "test-password";
      const malformedHash = "invalid:hash";

      const isValid = await verifyPassword(password, malformedHash);
      expect(isValid).toBe(false);
    });

    it("should reject empty hash", async () => {
      const password = "test-password";

      const isValid = await verifyPassword(password, "");
      expect(isValid).toBe(false);
    });

    it("should reject hash without colon", async () => {
      const password = "test-password";
      const invalidHash = "invalidhashwithoutcolon";

      const isValid = await verifyPassword(password, invalidHash);
      expect(isValid).toBe(false);
    });
  });

  describe("integration", () => {
    it("should hash and verify password in sequence", async () => {
      const password = "integration-test-password";
      
      const hash = await hashPassword(password);
      const isValid = await verifyPassword(password, hash);
      
      expect(isValid).toBe(true);
    });

    it("should handle empty password securely", async () => {
      const password = "";
      const hash = await hashPassword(password);
      
      expect(hash).toBeDefined();
      expect(hash).toContain(":");
      
      const isValid = await verifyPassword(password, hash);
      expect(isValid).toBe(true);
    });

    it("should handle long passwords", async () => {
      const password = "a".repeat(1000);
      const hash = await hashPassword(password);
      
      expect(hash).toBeDefined();
      
      const isValid = await verifyPassword(password, hash);
      expect(isValid).toBe(true);
    });
  });
});
