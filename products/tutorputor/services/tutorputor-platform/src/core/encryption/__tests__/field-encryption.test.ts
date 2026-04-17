/**
 * Field-Level Encryption Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for field-level encryption service
 * @doc.layer platform
 * @doc.pattern UnitTest
 */

import { describe, it, expect, beforeAll } from "vitest";
import {
  FieldEncryptionService,
  type EncryptedField,
} from "../field-encryption";

describe("FieldEncryptionService", () => {
  let encryptionService: FieldEncryptionService;

  beforeAll(() => {
    encryptionService = new FieldEncryptionService({
      encryptionKeyId: "test-key-id",
    });
  });

  describe("encrypt", () => {
    it("should encrypt a plaintext string", () => {
      const plaintext = "user@example.com";
      const encrypted = encryptionService.encrypt(plaintext);

      expect(encrypted).toBeDefined();
      expect(encrypted.encryptedData).toBeDefined();
      expect(encrypted.iv).toBeDefined();
      expect(encrypted.authTag).toBeDefined();
      expect(encrypted.keyId).toBe("test-key-id");
      expect(encrypted.algorithm).toBe("aes-256-gcm");
    });

    it("should throw error for empty string", () => {
      expect(() => encryptionService.encrypt("")).toThrow("Cannot encrypt empty or null value");
    });

    it("should throw error for null value", () => {
      expect(() => encryptionService.encrypt(null as unknown as string)).toThrow("Cannot encrypt empty or null value");
    });

    it("should produce different encrypted values for same plaintext (due to random IV)", () => {
      const plaintext = "user@example.com";
      const encrypted1 = encryptionService.encrypt(plaintext);
      const encrypted2 = encryptionService.encrypt(plaintext);

      expect(encrypted1.encryptedData).not.toBe(encrypted2.encryptedData);
      expect(encrypted1.iv).not.toBe(encrypted2.iv);
    });
  });

  describe("decrypt", () => {
    it("should decrypt an encrypted field", () => {
      const plaintext = "user@example.com";
      const encrypted = encryptionService.encrypt(plaintext);
      const decrypted = encryptionService.decrypt(encrypted);

      expect(decrypted).toBe(plaintext);
    });

    it("should throw error for invalid encrypted field", () => {
      expect(() => encryptionService.decrypt({} as EncryptedField)).toThrow("Invalid encrypted field format");
    });

    it("should throw error for missing fields", () => {
      const partial = { encryptedData: "data" } as EncryptedField;
      expect(() => encryptionService.decrypt(partial)).toThrow("Invalid encrypted field format");
    });
  });

  describe("isEncrypted", () => {
    it("should return true for valid encrypted field", () => {
      const plaintext = "user@example.com";
      const encrypted = encryptionService.encrypt(plaintext);

      expect(encryptionService.isEncrypted(encrypted)).toBe(true);
    });

    it("should return false for invalid encrypted field", () => {
      expect(encryptionService.isEncrypted({})).toBe(false);
    });

    it("should return false for null", () => {
      expect(encryptionService.isEncrypted(null)).toBe(false);
    });

    it("should return false for string", () => {
      expect(encryptionService.isEncrypted("plaintext")).toBe(false);
    });
  });

  describe("encryptField", () => {
    it("should encrypt a field in an object", () => {
      const obj = { email: "user@example.com", name: "John Doe" };
      const result = encryptionService.encryptField(obj, "email");

      expect(result.email_encrypted).toBeDefined();
      expect(typeof result.email_encrypted).toBe("string");
    });

    it("should not encrypt empty string field", () => {
      const obj = { email: "", name: "John Doe" };
      const result = encryptionService.encryptField(obj, "email");

      expect(result.email_encrypted).toBeUndefined();
    });

    it("should not encrypt non-string field", () => {
      const obj = { age: 25, name: "John Doe" } as Record<string, unknown>;
      const result = encryptionService.encryptField(obj, "age");

      expect(result.age_encrypted).toBeUndefined();
    });
  });

  describe("decryptField", () => {
    it("should decrypt a field in an object", () => {
      const obj = { email: "user@example.com", name: "John Doe" };
      const encrypted = encryptionService.encryptField(obj, "email");

      const decrypted = encryptionService.decryptField(encrypted, "email");

      expect(decrypted).toBe("user@example.com");
    });

    it("should return null for non-encrypted field", () => {
      const obj = { email: "user@example.com", name: "John Doe" };
      const decrypted = encryptionService.decryptField(obj, "email");

      expect(decrypted).toBeNull();
    });

    it("should return null for invalid encrypted data", () => {
      const obj = { email_encrypted: "invalid-json" } as Record<string, unknown>;
      const decrypted = encryptionService.decryptField(obj, "email");

      expect(decrypted).toBeNull();
    });
  });

  describe("round-trip encryption", () => {
    it("should successfully round-trip various data types", () => {
      const testCases = [
        "simple@email.com",
        "user+tag@example.com",
        "very.long.email.address@subdomain.example.co.uk",
        "+1-555-123-4567",
        "555-123-4567",
        "(555) 123-4567",
        "Assessment response with special characters: !@#$%^&*()",
      ];

      for (const testCase of testCases) {
        const encrypted = encryptionService.encrypt(testCase);
        const decrypted = encryptionService.decrypt(encrypted);
        expect(decrypted).toBe(testCase);
      }
    });
  });
});
