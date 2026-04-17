/**
 * KMS Provider Interface and Implementations
 *
 * Provides abstraction for key management services:
 * - AWS KMS
 * - HashiCorp Vault
 * - Environment variable (fallback for development)
 *
 * @doc.type service
 * @doc.purpose KMS provider abstraction for secure key management
 * @doc.layer platform
 * @doc.pattern Provider
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";

const logger = createStandaloneLogger({ component: "KMSProvider" });

// AWS SDK types for dynamic imports
type KMSClient = {
  send: (command: unknown) => Promise<{
    Plaintext?: Uint8Array;
    CiphertextBlob?: Uint8Array;
  }>;
};
type KMSClientConstructor = new (config: { region: string }) => KMSClient;
type GenerateDataKeyCommandConstructor = new (input: { KeyId: string; KeySpec: string }) => unknown;
type DecryptCommandConstructor = new (input: { CiphertextBlob: Buffer; KeyId?: string }) => unknown;

/**
 * KMS Provider interface
 */
export interface KMSProvider {
  /**
   * Get a data encryption key (DEK) from KMS
   * Returns both plaintext (for encryption) and encrypted (for storage) versions
   */
  generateDataKey(): Promise<{ plaintextKey: Buffer; encryptedKey: Buffer }>;

  /**
   * Decrypt a data encryption key
   */
  decryptDataKey(encryptedKey: Buffer): Promise<Buffer>;

  /**
   * Get the KMS key ARN/ID
   */
  getKeyId(): string;

  /**
   * Check if provider is healthy
   */
  healthCheck(): Promise<boolean>;
}

/**
 * AWS KMS Provider
 */
export class AWSKMSProvider implements KMSProvider {
  private kmsClient: unknown;
  private keyId: string;
  private region: string;

  constructor(keyId: string, region = "us-east-1") {
    this.keyId = keyId;
    this.region = region;
    
    // Dynamic import to avoid bundling AWS SDK in browser
    this.initializeClient();
  }

  private async initializeClient(): Promise<void> {
    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      // @ts-ignore - AWS SDK is optional dependency
      const { KMSClient } = await import("@aws-sdk/client-kms");
      const KMSClientCtor = KMSClient as KMSClientConstructor;
      this.kmsClient = new KMSClientCtor({ region: this.region });
      logger.info({ region: this.region, keyId: this.keyId }, "AWS KMS client initialized");
    } catch (error) {
      logger.error({ error: String(error) }, "Failed to initialize AWS KMS client");
      throw new Error("AWS KMS SDK not available. Install @aws-sdk/client-kms");
    }
  }

  async generateDataKey(): Promise<{ plaintextKey: Buffer; encryptedKey: Buffer }> {
    if (!this.kmsClient) {
      await this.initializeClient();
    }

    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      // @ts-ignore - AWS SDK is optional dependency
      const { GenerateDataKeyCommand } = await import("@aws-sdk/client-kms");
      const CmdCtor = GenerateDataKeyCommand as GenerateDataKeyCommandConstructor;
      const command = new CmdCtor({
        KeyId: this.keyId,
        KeySpec: "AES_256",
      });

      const response = await (this.kmsClient as { send: (cmd: unknown) => Promise<{
        Plaintext?: Uint8Array;
        CiphertextBlob?: Uint8Array;
      }> }).send(command);

      if (!response.Plaintext || !response.CiphertextBlob) {
        throw new Error("AWS KMS returned empty data key");
      }

      logger.debug({ keyId: this.keyId }, "Generated data key from AWS KMS");

      return {
        plaintextKey: Buffer.from(response.Plaintext),
        encryptedKey: Buffer.from(response.CiphertextBlob),
      };
    } catch (error) {
      logger.error({
        error: error instanceof Error ? error.message : String(error),
        keyId: this.keyId,
      }, "Failed to generate data key from AWS KMS");
      throw error;
    }
  }

  async decryptDataKey(encryptedKey: Buffer): Promise<Buffer> {
    if (!this.kmsClient) {
      await this.initializeClient();
    }

    try {
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      // @ts-ignore - AWS SDK is optional dependency
      const { DecryptCommand } = await import("@aws-sdk/client-kms");
      const CmdCtor = DecryptCommand as DecryptCommandConstructor;
      const command = new CmdCtor({
        CiphertextBlob: encryptedKey,
        KeyId: this.keyId,
      });

      const response = await (this.kmsClient as { send: (cmd: unknown) => Promise<{
        Plaintext?: Uint8Array;
      }> }).send(command);

      if (!response.Plaintext) {
        throw new Error("AWS KMS returned empty plaintext");
      }

      logger.debug("Decrypted data key with AWS KMS");

      return Buffer.from(response.Plaintext);
    } catch (error) {
      logger.error({
        error: error instanceof Error ? error.message : String(error),
        keyId: this.keyId,
      }, "Failed to decrypt data key with AWS KMS");
      throw error;
    }
  }

  getKeyId(): string {
    return this.keyId;
  }

  async healthCheck(): Promise<boolean> {
    try {
      if (!this.kmsClient) {
        await this.initializeClient();
      }

      // Try to generate a small data key to verify connectivity
      const { plaintextKey } = await this.generateDataKey();
      // Don't use this key, just verifying connectivity
      plaintextKey.fill(0); // Clear sensitive data
      
      return true;
    } catch (error) {
      logger.error({ error: String(error) }, "AWS KMS health check failed");
      return false;
    }
  }
}

/**
 * HashiCorp Vault Provider
 */
export class VaultKMSProvider implements KMSProvider {
  private vaultAddr: string;
  private transitPath: string;
  private keyName: string;
  private token: string;

  constructor(
    vaultAddr: string,
    token: string,
    keyName: string,
    transitPath = "transit",
  ) {
    this.vaultAddr = vaultAddr.replace(/\/$/, ""); // Remove trailing slash
    this.token = token;
    this.keyName = keyName;
    this.transitPath = transitPath;
  }

  private async makeRequest<T>(
    method: string,
    path: string,
    body?: unknown,
  ): Promise<T> {
    const url = `${this.vaultAddr}/v1/${path}`;
    
    const response = await fetch(url, {
      method,
      headers: {
        "Content-Type": "application/json",
        "X-Vault-Token": this.token,
      },
      body: body ? JSON.stringify(body) : null,
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Vault request failed: ${response.status} ${errorText}`);
    }

    const data = await response.json() as { data: T };
    return data.data;
  }

  async generateDataKey(): Promise<{ plaintextKey: Buffer; encryptedKey: Buffer }> {
    try {
      const response = await this.makeRequest<{
        plaintext: string;
        ciphertext: string;
      }>(
        "POST",
        `${this.transitPath}/datakey/plaintext/${this.keyName}`,
        { bits: 256 },
      );

      logger.debug({ keyName: this.keyName }, "Generated data key from Vault");

      return {
        plaintextKey: Buffer.from(response.plaintext, "base64"),
        encryptedKey: Buffer.from(response.ciphertext, "base64"),
      };
    } catch (error) {
      logger.error({
        error: error instanceof Error ? error.message : String(error),
        keyName: this.keyName,
      }, "Failed to generate data key from Vault");
      throw error;
    }
  }

  async decryptDataKey(encryptedKey: Buffer): Promise<Buffer> {
    try {
      const response = await this.makeRequest<{
        plaintext: string;
      }>(
        "POST",
        `${this.transitPath}/decrypt/${this.keyName}`,
        { ciphertext: encryptedKey.toString("base64") },
      );

      logger.debug("Decrypted data key with Vault");

      return Buffer.from(response.plaintext, "base64");
    } catch (error) {
      logger.error({
        error: error instanceof Error ? error.message : String(error),
        keyName: this.keyName,
      }, "Failed to decrypt data key with Vault");
      throw error;
    }
  }

  getKeyId(): string {
    return `${this.vaultAddr}/${this.transitPath}/${this.keyName}`;
  }

  async healthCheck(): Promise<boolean> {
    try {
      const response = await fetch(`${this.vaultAddr}/v1/sys/health`, {
        headers: { "X-Vault-Token": this.token },
      });
      
      // Vault returns 200 for initialized, unsealed, and active
      // 429 for unsealed and standby
      // 472 for data recovery mode replication secondary
      // 473 for performance standby
      return response.ok || response.status === 429 || response.status === 473;
    } catch (error) {
      logger.error({ error: String(error) }, "Vault health check failed");
      return false;
    }
  }
}

/**
 * Environment Variable Provider (Development Only)
 * NOT for production use
 */
export class EnvironmentKMSProvider implements KMSProvider {
  private keyId: string;
  private masterKey: Buffer;

  constructor(keyId = "env-default") {
    this.keyId = keyId;
    
    const envKey = process.env.FIELD_ENCRYPTION_KEY;
    if (envKey) {
      this.masterKey = Buffer.from(envKey, "hex");
      if (this.masterKey.length !== 32) {
        throw new Error("FIELD_ENCRYPTION_KEY must be 64 hex characters (32 bytes)");
      }
    } else {
      // Development fallback - log warning
      logger.warn(
        "FIELD_ENCRYPTION_KEY not set, using deterministic key - NOT SECURE for production",
      );
      
      // Create deterministic key from keyId (DEV ONLY)
      const crypto = require("crypto");
      const hash = crypto.createHash("sha256");
      hash.update(keyId);
      hash.update("tutorputor-field-encryption-key");
      this.masterKey = hash.digest();
    }
  }

  async generateDataKey(): Promise<{ plaintextKey: Buffer; encryptedKey: Buffer }> {
    // Generate random DEK
    const crypto = require("crypto");
    const dek = crypto.randomBytes(32);
    
    // Encrypt DEK with master key using AES-256-GCM
    const iv = crypto.randomBytes(16);
    const cipher = crypto.createCipheriv("aes-256-gcm", this.masterKey, iv);
    
    let encrypted = cipher.update(dek);
    encrypted = Buffer.concat([encrypted, cipher.final()]);
    
    const authTag = cipher.getAuthTag();
    
    // Combine: iv (16) + authTag (16) + encrypted (variable)
    const encryptedKey = Buffer.concat([iv, authTag, encrypted]);
    
    logger.debug("Generated data key from environment provider");
    
    return {
      plaintextKey: dek,
      encryptedKey,
    };
  }

  async decryptDataKey(encryptedKey: Buffer): Promise<Buffer> {
    const crypto = require("crypto");
    
    // Extract components
    const iv = encryptedKey.slice(0, 16);
    const authTag = encryptedKey.slice(16, 32);
    const encrypted = encryptedKey.slice(32);
    
    const decipher = crypto.createDecipheriv("aes-256-gcm", this.masterKey, iv);
    decipher.setAuthTag(authTag);
    
    let decrypted = decipher.update(encrypted);
    decrypted = Buffer.concat([decrypted, decipher.final()]);
    
    logger.debug("Decrypted data key with environment provider");
    
    return decrypted;
  }

  getKeyId(): string {
    return `env://${this.keyId}`;
  }

  async healthCheck(): Promise<boolean> {
    try {
      // Test encrypt/decrypt roundtrip
      const { plaintextKey, encryptedKey } = await this.generateDataKey();
      const decrypted = await this.decryptDataKey(encryptedKey);
      
      const matches = plaintextKey.equals(decrypted);
      plaintextKey.fill(0);
      decrypted.fill(0);
      
      return matches;
    } catch (error) {
      logger.error({ error: String(error) }, "Environment KMS health check failed");
      return false;
    }
  }
}

/**
 * Create KMS provider based on environment configuration
 */
export function createKMSProvider(): KMSProvider {
  const kmsType = process.env.KMS_TYPE?.toLowerCase();

  if (kmsType === "aws") {
    const keyId = process.env.AWS_KMS_KEY_ID;
    const region = process.env.AWS_REGION || "us-east-1";
    
    if (!keyId) {
      throw new Error("AWS_KMS_KEY_ID is required when KMS_TYPE=aws");
    }
    
    logger.info({ region, keyId }, "Using AWS KMS provider");
    return new AWSKMSProvider(keyId, region);
  }

  if (kmsType === "vault") {
    const vaultAddr = process.env.VAULT_ADDR;
    const vaultToken = process.env.VAULT_TOKEN;
    const keyName = process.env.VAULT_TRANSIT_KEY || "tutorputor";
    
    if (!vaultAddr || !vaultToken) {
      throw new Error("VAULT_ADDR and VAULT_TOKEN are required when KMS_TYPE=vault");
    }
    
    logger.info({ vaultAddr, keyName }, "Using HashiCorp Vault provider");
    return new VaultKMSProvider(vaultAddr, vaultToken, keyName);
  }

  // Fallback to environment variable (development)
  logger.warn("KMS_TYPE not set or invalid, using environment key provider (NOT FOR PRODUCTION)");
  return new EnvironmentKMSProvider(process.env.FIELD_ENCRYPTION_KEY_ID);
}
