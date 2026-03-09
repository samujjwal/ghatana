/**
 * @fileoverview Data Encryption Utilities
 *
 * Provides AES-GCM encryption for sensitive data stored in IndexedDB.
 * Uses Web Crypto API for secure, browser-native encryption.
 *
 * **Features**:
 * - AES-256-GCM encryption
 * - Automatic key derivation from passphrase
 * - Secure random IV generation
 * - Data integrity verification (GCM mode)
 * - Zero external dependencies
 *
 * **Security Notes**:
 * - Keys are derived from passphrase using PBKDF2
 * - Each encryption uses a unique random IV
 * - GCM mode provides authenticated encryption
 * - Suitable for protecting PII and sensitive events
 *
 * @module utils/encryption
 */

/**
 * Encryption configuration
 */
export interface EncryptionConfig {
  /** Passphrase for key derivation */
  passphrase?: string;
  /** Salt for PBKDF2 (should be stored securely) */
  salt?: Uint8Array;
  /** Number of PBKDF2 iterations */
  iterations?: number;
}

/**
 * Encrypted data structure
 */
export interface EncryptedData {
  /** Encrypted data */
  ciphertext: string;
  /** Initialization vector (base64) */
  iv: string;
  /** Salt used for key derivation (base64) */
  salt: string;
  /** Algorithm identifier */
  algorithm: string;
}

/**
 * Default encryption configuration
 */
const DEFAULT_CONFIG: Required<EncryptionConfig> = {
  passphrase: 'dcmaar-extension-default-passphrase', // Should be overridden in production
  salt: new Uint8Array(16),
  iterations: 100000,
};

/**
 * Crypto utility class for data encryption/decryption
 */
export class CryptoUtil {
  private config: Required<EncryptionConfig>;
  private keyCache: CryptoKey | null = null;

  constructor(config: EncryptionConfig = {}) {
    this.config = {
      ...DEFAULT_CONFIG,
      ...config,
    };

    // Generate random salt if not provided
    if (!config.salt) {
      this.config.salt = crypto.getRandomValues(new Uint8Array(16));
    }
  }

  /**
   * Derive encryption key from passphrase using PBKDF2
   */
  private async deriveKey(passphrase: string, salt: BufferSource): Promise<CryptoKey> {
    // Convert passphrase to key material
    const encoder = new TextEncoder();
    const keyMaterial = await crypto.subtle.importKey(
      'raw',
      encoder.encode(passphrase),
      'PBKDF2',
      false,
      ['deriveBits', 'deriveKey']
    );

    // Derive AES-GCM key
    return crypto.subtle.deriveKey(
      {
        name: 'PBKDF2',
        salt: salt as BufferSource,
        iterations: this.config.iterations,
        hash: 'SHA-256',
      },
      keyMaterial,
      { name: 'AES-GCM', length: 256 },
      false,
      ['encrypt', 'decrypt']
    );
  }

  /**
   * Get or create encryption key
   */
  private async getKey(): Promise<CryptoKey> {
    if (this.keyCache) {
      return this.keyCache;
    }

    this.keyCache = await this.deriveKey(this.config.passphrase, this.config.salt as BufferSource);
    return this.keyCache;
  }

  /**
   * Encrypt data using AES-GCM
   */
  async encrypt(data: string): Promise<EncryptedData> {
    const encoder = new TextEncoder();
    const dataBuffer = encoder.encode(data);

    // Generate random IV
    const iv = crypto.getRandomValues(new Uint8Array(12));

    // Get encryption key
    const key = await this.getKey();

    // Encrypt data
    const ciphertext = await crypto.subtle.encrypt(
      {
        name: 'AES-GCM',
        iv,
      },
      key,
      dataBuffer
    );

    // Convert to base64 for storage
    return {
      ciphertext: this.arrayBufferToBase64(ciphertext),
      iv: this.arrayBufferToBase64(iv),
      salt: this.arrayBufferToBase64(this.config.salt),
      algorithm: 'AES-GCM-256',
    };
  }

  /**
   * Decrypt data using AES-GCM
   */
  async decrypt(encrypted: EncryptedData): Promise<string> {
    // Convert from base64
    const ciphertext = this.base64ToArrayBuffer(encrypted.ciphertext);
    const iv = this.base64ToArrayBuffer(encrypted.iv);

    // Get decryption key
    const key = await this.getKey();

    try {
      // Decrypt data
      const decrypted = await crypto.subtle.decrypt(
        {
          name: 'AES-GCM',
          iv: iv as BufferSource,
        },
        key,
        ciphertext as BufferSource
      );

      // Convert to string
      const decoder = new TextDecoder();
      return decoder.decode(decrypted);
    } catch (error) {
      throw new Error('Decryption failed: Invalid key or corrupted data');
    }
  }

  /**
   * Encrypt JSON object
   */
  async encryptJSON<T = any>(obj: T): Promise<EncryptedData> {
    const json = JSON.stringify(obj);
    return this.encrypt(json);
  }

  /**
   * Decrypt JSON object
   */
  async decryptJSON<T = any>(encrypted: EncryptedData): Promise<T> {
    const json = await this.decrypt(encrypted);
    return JSON.parse(json);
  }

  /**
   * Generate a secure random passphrase
   */
  static generatePassphrase(length: number = 32): string {
    const array = crypto.getRandomValues(new Uint8Array(length));
    return Array.from(array, (byte) => byte.toString(16).padStart(2, '0')).join('');
  }

  /**
   * Convert ArrayBuffer to base64 string
   */
  private arrayBufferToBase64(buffer: ArrayBuffer | Uint8Array): string {
    const bytes = buffer instanceof ArrayBuffer ? new Uint8Array(buffer) : buffer;
    let binary = '';
    for (let i = 0; i < bytes.byteLength; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary);
  }

  /**
   * Convert base64 string to ArrayBuffer
   */
  private base64ToArrayBuffer(base64: string): Uint8Array {
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
  }

  /**
   * Clear cached key (for security)
   */
  clearKey(): void {
    this.keyCache = null;
  }
}

/**
 * Singleton instance for extension-wide use
 */
let cryptoInstance: CryptoUtil | null = null;

/**
 * Get or create crypto utility instance
 */
export function getCrypto(config?: EncryptionConfig): CryptoUtil {
  if (!cryptoInstance) {
    cryptoInstance = new CryptoUtil(config);
  }
  return cryptoInstance;
}

/**
 * Initialize crypto with custom configuration
 */
export function initCrypto(config: EncryptionConfig): CryptoUtil {
  cryptoInstance = new CryptoUtil(config);
  return cryptoInstance;
}

/**
 * Quick encrypt function
 */
export async function encrypt(data: string, config?: EncryptionConfig): Promise<EncryptedData> {
  const crypto = getCrypto(config);
  return crypto.encrypt(data);
}

/**
 * Quick decrypt function
 */
export async function decrypt(encrypted: EncryptedData, config?: EncryptionConfig): Promise<string> {
  const crypto = getCrypto(config);
  return crypto.decrypt(encrypted);
}

/**
 * Quick encrypt JSON function
 */
export async function encryptJSON<T = any>(obj: T, config?: EncryptionConfig): Promise<EncryptedData> {
  const crypto = getCrypto(config);
  return crypto.encryptJSON(obj);
}

/**
 * Quick decrypt JSON function
 */
export async function decryptJSON<T = any>(encrypted: EncryptedData, config?: EncryptionConfig): Promise<T> {
  const crypto = getCrypto(config);
  return crypto.decryptJSON(encrypted);
}

/**
 * Check if data is encrypted
 */
export function isEncrypted(data: any): data is EncryptedData {
  return (
    data &&
    typeof data === 'object' &&
    typeof data.ciphertext === 'string' &&
    typeof data.iv === 'string' &&
    typeof data.salt === 'string' &&
    typeof data.algorithm === 'string'
  );
}

/**
 * Selective field encryption helper
 */
export async function encryptFields<T extends Record<string, any>>(
  obj: T,
  fields: (keyof T)[],
  config?: EncryptionConfig
): Promise<T> {
  const crypto = getCrypto(config);
  const result = { ...obj };

  for (const field of fields) {
    if (field in result && result[field] !== undefined) {
      const value = result[field];
      result[field] = await crypto.encrypt(JSON.stringify(value)) as any;
    }
  }

  return result;
}

/**
 * Selective field decryption helper
 */
export async function decryptFields<T extends Record<string, any>>(
  obj: T,
  fields: (keyof T)[],
  config?: EncryptionConfig
): Promise<T> {
  const crypto = getCrypto(config);
  const result = { ...obj };

  for (const field of fields) {
    if (field in result && isEncrypted(result[field])) {
      const decrypted = await crypto.decrypt(result[field] as any);
      result[field] = JSON.parse(decrypted);
    }
  }

  return result;
}
