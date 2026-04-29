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
 * Encryption key resolver abstraction.
 * Production implementations should integrate with AWS KMS, HashiCorp Vault,
 * Azure Key Vault, or another secure key management service.
 */
export interface FieldEncryptionKeyResolver {
  resolve(keyId: string): Buffer;
  hasKey(keyId: string): boolean;
}

/**
 * Default environment-based key resolver.
 * In production, FIELD_ENCRYPTION_KEY must be set to a 64-character hex string.
 * Supports multiple comma-separated keys for rotation: `oldKeyId=hex,newKeyId=hex`
 */
class EnvironmentKeyResolver implements FieldEncryptionKeyResolver {
  private keys = new Map<string, Buffer>();

  constructor() {
    const envKey = process.env.FIELD_ENCRYPTION_KEY;
    const envKeyMap = process.env.FIELD_ENCRYPTION_KEYS;

    if (envKeyMap) {
      // Multi-key format: keyId1=hex,keyId2=hex
      for (const entry of envKeyMap.split(",")) {
        const [keyId, hexKey] = entry.split("=");
        if (keyId && hexKey && hexKey.length === 64) {
          this.keys.set(keyId.trim(), Buffer.from(hexKey.trim(), "hex"));
        }
      }
    }

    if (envKey && envKey.length === 64) {
      this.keys.set("default", Buffer.from(envKey, "hex"));
    }
  }

  resolve(keyId: string): Buffer {
    const key = this.keys.get(keyId);
    if (!key) {
      throw new FieldEncryptionKeyError(
        `Encryption key not found for keyId: ${keyId}. Ensure FIELD_ENCRYPTION_KEY or FIELD_ENCRYPTION_KEYS is configured.`
      );
    }
    return key;
  }

  hasKey(keyId: string): boolean {
    return this.keys.has(keyId);
  }
}

/**
 * Thrown when a required encryption key cannot be resolved.
 */
export class FieldEncryptionKeyError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "FieldEncryptionKeyError";
  }
}

/**
 * Encryption configuration
 */
export interface FieldEncryptionConfig {
  encryptionKeyId?: string;
  algorithm?: string;
  keyLength?: number;
  keyResolver?: FieldEncryptionKeyResolver;
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
 * Batch re-encryption record for key rotation.
 */
export interface RotationRecord {
  id: string;
  encryptedField: EncryptedField;
}

/**
 * Field-Level Encryption Service
 */
export class FieldEncryptionService {
  private algorithm: string;
  private keyLength: number;
  private encryptionKeyId: string;
  private keyResolver: FieldEncryptionKeyResolver;

  constructor(config: FieldEncryptionConfig = {}) {
    this.algorithm = config.algorithm || "aes-256-gcm";
    this.keyLength = config.keyLength || 32; // 256 bits for AES-256
    this.encryptionKeyId = config.encryptionKeyId || "default";
    this.keyResolver = config.keyResolver ?? new EnvironmentKeyResolver();

    if (!crypto.getCipherInfo(this.algorithm)) {
      throw new Error(`Unsupported encryption algorithm: ${this.algorithm}`);
    }

    // Eagerly validate that the current key is available
    this.validateKeyAvailable(this.encryptionKeyId);
  }

  private validateKeyAvailable(keyId: string): void {
    if (!this.keyResolver.hasKey(keyId)) {
      const isProduction = process.env.NODE_ENV === "production";
      if (isProduction) {
        throw new FieldEncryptionKeyError(
          `Production startup blocked: encryption key "${keyId}" is not available. ` +
            `Configure FIELD_ENCRYPTION_KEY or FIELD_ENCRYPTION_KEYS.`
        );
      }
      logger.warn(
        `Encryption key "${keyId}" not configured. FIELD_ENCRYPTION_KEY must be set before deploying to production.`
      );
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

    // Resolve the current encryption key from the key resolver
    const key = this.keyResolver.resolve(this.encryptionKeyId);

    // Create cipher
    const cipher = crypto.createCipheriv(this.algorithm, key, iv);

    // Encrypt the data
    let encrypted = cipher.update(plaintext, "utf8", "hex");
    encrypted += cipher.final("hex");

    // Get authentication tag
    const authTag = (cipher as crypto.CipherGCM).getAuthTag();

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
   * Decrypt an encrypted field using the key referenced by keyId.
   * This supports decryption of data encrypted with rotated keys
   * as long as the old key remains available in the resolver.
   */
  decrypt(encryptedField: EncryptedField): string {
    if (!encryptedField.encryptedData || !encryptedField.iv || !encryptedField.authTag) {
      throw new Error("Invalid encrypted field format");
    }

    // Parse the encrypted field
    const iv = Buffer.from(encryptedField.iv, "hex");
    const authTag = Buffer.from(encryptedField.authTag, "hex");
    const encryptedData = Buffer.from(encryptedField.encryptedData, "hex");

    // Resolve key by the stored keyId (supports rotated keys)
    const key = this.keyResolver.resolve(encryptedField.keyId);

    // Create decipher
    const decipher = crypto.createDecipheriv(this.algorithm, key, iv);
    (decipher as crypto.DecipherGCM).setAuthTag(authTag);

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
   * Rotate encryption key by re-encrypting a batch of records.
   *
   * @param newKeyId - The target key ID to encrypt with.
   * @param fetchBatch - Async generator that yields batches of encrypted records.
   * @param updateRecord - Callback that persists the re-encrypted record.
   *
   * The old key must remain available in the key resolver until all records
   * are migrated. After rotation completes, the service switches to newKeyId.
   */
  async rotateKey(
    newKeyId: string,
    fetchBatch: (cursor?: string) => Promise<{ records: RotationRecord[]; nextCursor?: string }>,
    updateRecord: (id: string, encryptedField: EncryptedField) => Promise<void>,
  ): Promise<{ migrated: number; failed: number }> {
    if (!this.keyResolver.hasKey(newKeyId)) {
      throw new FieldEncryptionKeyError(
        `Cannot rotate to unknown key "${newKeyId}". Add it to the key resolver first.`
      );
    }

    logger.info({ fromKeyId: this.encryptionKeyId, toKeyId: newKeyId }, "Key rotation initiated");

    let migrated = 0;
    let failed = 0;
    let cursor: string | undefined;

    do {
      const batch = await fetchBatch(cursor);
      for (const record of batch.records) {
        try {
          const plaintext = this.decrypt(record.encryptedField);
          const reencrypted = this.encryptWithKeyId(plaintext, newKeyId);
          await updateRecord(record.id, reencrypted);
          migrated++;
        } catch (error) {
          failed++;
          logger.error({
            recordId: record.id,
            keyId: record.encryptedField.keyId,
            error: error instanceof Error ? error.message : String(error),
          }, "Key rotation: failed to re-encrypt record");
        }
      }
      cursor = batch.nextCursor;
    } while (cursor);

    this.encryptionKeyId = newKeyId;

    logger.info({ newKeyId, migrated, failed }, "Key rotation completed");
    return { migrated, failed };
  }

  /**
   * Encrypt with a specific key ID (used during rotation).
   */
  private encryptWithKeyId(plaintext: string, keyId: string): EncryptedField {
    if (!plaintext) {
      throw new Error("Cannot encrypt empty or null value");
    }

    const iv = crypto.randomBytes(16);
    const key = this.keyResolver.resolve(keyId);
    const cipher = crypto.createCipheriv(this.algorithm, key, iv);

    let encrypted = cipher.update(plaintext, "utf8", "hex");
    encrypted += cipher.final("hex");
    const authTag = (cipher as crypto.CipherGCM).getAuthTag();

    return {
      encryptedData: encrypted,
      iv: iv.toString("hex"),
      authTag: authTag.toString("hex"),
      keyId,
      algorithm: this.algorithm,
    };
  }
}

/**
 * Singleton instance
 */
export const fieldEncryption = new FieldEncryptionService({
  encryptionKeyId: process.env.FIELD_ENCRYPTION_KEY_ID || "default",
});
