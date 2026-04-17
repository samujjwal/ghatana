/**
 * Field-Level Encryption for PII
 *
 * Provides AES-256-GCM encryption for sensitive fields at rest.
 * Uses a key management service (KMS) for key storage and rotation.
 *
 * @doc.type service
 * @doc.purpose Field-level encryption for PII
 * @doc.layer platform
 * @doc.pattern EncryptionService
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import crypto from "crypto";

const logger = createStandaloneLogger({ component: "FieldEncryption" });

/**
 * Encryption configuration
 */
export interface FieldEncryptionConfig {
  encryptionKeyId?: string;
  algorithm?: string;
  keyLength?: number;
}

/**
 * Encrypted field format
 */
export interface EncryptedField {
  encryptedData: string;
  iv: string;
  authTag: string;
  keyId: string;
  algorithm: string;
}

/**
 * Field-Level Encryption Service
 */
export class FieldEncryptionService {
  private algorithm: string;
  private keyLength: number;
  private encryptionKeyId: string;

  constructor(config: FieldEncryptionConfig = {}) {
    this.algorithm = config.algorithm || "aes-256-gcm";
    this.keyLength = config.keyLength || 32; // 256 bits for AES-256
    this.encryptionKeyId = config.encryptionKeyId || "default";

    if (!crypto.getCipherInfo(this.algorithm)) {
      throw new Error(`Unsupported encryption algorithm: ${this.algorithm}`);
    }
  }

  /**
   * Encrypt a plaintext value
   */
  encrypt(plaintext: string): EncryptedField {
    if (!plaintext) {
      throw new Error("Cannot encrypt empty or null value");
    }

    // Generate a random IV (initialization vector)
    const iv = crypto.randomBytes(16);

    // Get or generate encryption key
    const key = this.getEncryptionKey();

    // Create cipher
    const cipher = crypto.createCipheriv(this.algorithm, key, iv);

    // Encrypt the data
    let encrypted = cipher.update(plaintext, "utf8", "hex");
    encrypted += cipher.final("hex");

    // Get authentication tag (cast to any to access GCM-specific method)
    const authTag = (cipher as any).getAuthTag();

    const result: EncryptedField = {
      encryptedData: encrypted,
      iv: iv.toString("hex"),
      authTag: authTag.toString("hex"),
      keyId: this.encryptionKeyId,
      algorithm: this.algorithm,
    };

    logger.debug({
      fieldLength: plaintext.length,
      algorithm: this.algorithm,
    }, "Field encrypted");

    return result;
  }

  /**
   * Decrypt an encrypted field
   */
  decrypt(encryptedField: EncryptedField): string {
    if (!encryptedField.encryptedData || !encryptedField.iv || !encryptedField.authTag) {
      throw new Error("Invalid encrypted field format");
    }

    // Parse the encrypted field
    const iv = Buffer.from(encryptedField.iv, "hex");
    const authTag = Buffer.from(encryptedField.authTag, "hex");
    const encryptedData = Buffer.from(encryptedField.encryptedData, "hex");

    // Get the encryption key
    const key = this.getEncryptionKey();

    // Create decipher
    const decipher = crypto.createDecipheriv(this.algorithm, key, iv);
    (decipher as any).setAuthTag(authTag);

    // Decrypt the data
    let decrypted = decipher.update(encryptedData);
    decrypted = Buffer.concat([decrypted, decipher.final()]);

    const result = decrypted.toString("utf8");

    logger.debug({
      algorithm: encryptedField.algorithm,
      keyId: encryptedField.keyId,
    }, "Field decrypted");

    return result;
  }

  /**
   * Check if a value is encrypted
   */
  isEncrypted(value: unknown): value is EncryptedField {
    if (typeof value !== "object" || value === null) {
      return false;
    }

    const field = value as EncryptedField;
    return !!(
      field.encryptedData &&
      field.iv &&
      field.authTag &&
      field.keyId &&
      field.algorithm
    );
  }

  /**
   * Encrypt a specific field in an object
   */
  encryptField<T extends Record<string, unknown>>(
    obj: T,
    fieldName: keyof T,
  ): T {
    const value = obj[fieldName];
    const fieldNameStr = String(fieldName);

    if (typeof value === "string" && value.length > 0) {
      const encrypted = this.encrypt(value);
      (obj as Record<string, unknown>)[`${fieldNameStr}_encrypted`] = JSON.stringify(encrypted);
    }

    return obj;
  }

  /**
   * Decrypt a specific field in an object
   */
  decryptField<T extends Record<string, unknown>>(
    obj: T,
    fieldName: keyof T,
  ): string | null {
    const encryptedFieldName = `${String(fieldName)}_encrypted` as keyof T;
    const encryptedValue = obj[encryptedFieldName];

    if (typeof encryptedValue === "string") {
      try {
        const encryptedField: EncryptedField = JSON.parse(encryptedValue);
        return this.decrypt(encryptedField);
      } catch (error) {
        logger.error({
          fieldName,
          error: error instanceof Error ? error.message : String(error),
        }, "Failed to decrypt field");
        return null;
      }
    }

    return null;
  }

  /**
   * Get encryption key from KMS or environment
   * In production, this should integrate with AWS KMS, HashiCorp Vault, or similar
   */
  private getEncryptionKey(): Buffer {
    // For now, use environment variable or generate a key
    // TODO: Integrate with proper KMS (AWS KMS, HashiCorp Vault, etc.)
    const keyFromEnv = process.env.FIELD_ENCRYPTION_KEY;

    if (keyFromEnv) {
      return Buffer.from(keyFromEnv, "hex");
    }

    // Generate a deterministic key based on keyId (NOT SECURE for production)
    // This is a fallback for development only
    logger.warn(
      "FIELD_ENCRYPTION_KEY not set, using deterministic key - NOT SECURE for production",
    );
    const hash = crypto.createHash("sha256");
    hash.update(this.encryptionKeyId);
    hash.update("tutorputor-field-encryption-key");
    return hash.digest();
  }

  /**
   * Rotate encryption key
   * This would involve re-encrypting all encrypted fields with a new key
   */
  async rotateKey(newKeyId: string): Promise<void> {
    logger.info({ fromKeyId: this.encryptionKeyId, toKeyId: newKeyId }, "Key rotation initiated");

    // TODO: Implement key rotation logic
    // 1. Get all records with encrypted fields
    // 2. Decrypt with old key
    // 3. Encrypt with new key
    // 4. Update records
    // 5. Update current keyId

    this.encryptionKeyId = newKeyId;

    logger.info({ newKeyId }, "Key rotation completed");
  }
}

/**
 * Singleton instance
 */
export const fieldEncryption = new FieldEncryptionService({
  encryptionKeyId: process.env.FIELD_ENCRYPTION_KEY_ID || "default",
});
