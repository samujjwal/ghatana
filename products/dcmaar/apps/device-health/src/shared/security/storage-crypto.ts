/**
 * Cryptographic utilities for secure storage.
 * Provides AES-GCM helpers for encrypting data at rest while keeping legacy
 * helper functions for compatibility with older modules.
 */

const _DEFAULT_ENCRYPTION_KEY = 'dcmaar_encryption_key';

const encoder = new TextEncoder();
const decoder = new TextDecoder();
const DEFAULT_PASSPHRASE = 'dcmaar.storage.passphrase';
const DEFAULT_SALT = 'dcmaar.storage.salt';
const DEFAULT_IV_LENGTH = 12;

function base64Encode(binary: string): string {
  if (typeof btoa === 'function') {
    return btoa(binary);
  }
  const BufferCtor = (globalThis as { Buffer?: typeof Buffer }).Buffer;
  if (BufferCtor) {
    return BufferCtor.from(binary, 'binary').toString('base64');
  }
  throw new Error('Base64 encoding not supported');
}

function base64Decode(encoded: string): string {
  if (typeof atob === 'function') {
    return atob(encoded);
  }
  const BufferCtor = (globalThis as { Buffer?: typeof Buffer }).Buffer;
  if (BufferCtor) {
    return BufferCtor.from(encoded, 'base64').toString('binary');
  }
  throw new Error('Base64 decoding not supported');
}

function getCrypto(): Crypto | undefined {
  return globalThis.crypto;
}

function getSubtle(): SubtleCrypto {
  const cryptoImpl = getCrypto();
  if (!cryptoImpl?.subtle) {
    throw new Error('Web Crypto API is not available');
  }
  return cryptoImpl.subtle;
}

function toBase64(buffer: ArrayBuffer): string {
  const binary = String.fromCharCode(...new Uint8Array(buffer));
  return base64Encode(binary);
}

function fromBase64(encoded: string): Uint8Array {
  const binary = base64Decode(encoded);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes;
}

export function generateInitializationVector(length: number = DEFAULT_IV_LENGTH): Uint8Array {
  const cryptoImpl = getCrypto();
  if (!cryptoImpl?.getRandomValues) {
    throw new Error('Web Crypto API is not available');
  }
  const iv = new Uint8Array(length);
  cryptoImpl.getRandomValues(iv);
  return iv;
}

export async function createAesKeyFromPassphrase(
  passphrase: string,
  salt: string = DEFAULT_SALT,
  iterations: number = 100_000
): Promise<CryptoKey> {
  const subtle = getSubtle();
  const baseKey = await subtle.importKey('raw', encoder.encode(passphrase), 'PBKDF2', false, [
    'deriveKey',
  ]);

  return subtle.deriveKey(
    {
      name: 'PBKDF2',
      salt: encoder.encode(salt),
      iterations,
      hash: 'SHA-256',
    },
    baseKey,
    {
      name: 'AES-GCM',
      length: 256,
    },
    false,
    ['encrypt', 'decrypt']
  );
}

export async function encryptString(plainText: string, key: CryptoKey): Promise<string> {
  const subtle = getSubtle();
  const iv = generateInitializationVector();
  const cipher = await subtle.encrypt(
    {
      name: 'AES-GCM',
      iv: iv as BufferSource,
    },
    key,
    encoder.encode(plainText)
  );

  return `${toBase64(iv.buffer as ArrayBuffer)}:${toBase64(cipher as ArrayBuffer)}`;
}

export async function decryptString(payload: string, key: CryptoKey): Promise<string> {
  const subtle = getSubtle();
  const [ivPart, cipherPart] = payload.split(':', 2);
  if (!ivPart || !cipherPart) {
    throw new Error('Invalid encrypted payload');
  }
  const iv = fromBase64(ivPart);
  const cipherBytes = fromBase64(cipherPart);

  const plain = await subtle.decrypt(
    {
      name: 'AES-GCM',
      iv: iv as BufferSource,
    },
    key,
    cipherBytes as BufferSource
  );

  return decoder.decode(plain);
}

/**
 * Legacy helpers retained for compatibility. They fall back to simple base64
 * transformations when the Web Crypto API is unavailable.
 */
export async function encryptData(data: string): Promise<string> {
  return base64Encode(encodeURIComponent(data));
}

export async function decryptData(encrypted: string): Promise<string> {
  try {
    return decodeURIComponent(base64Decode(encrypted));
  } catch (error) {
    console.error('Failed to decrypt data:', error);
    throw new Error('Failed to decrypt data');
  }
}

export async function hashString(str: string): Promise<string> {
  const cryptoImpl = getCrypto();
  try {
    const digest = await cryptoImpl?.subtle?.digest('SHA-256', encoder.encode(str));
    if (digest) {
      return `h${toBase64(digest).slice(0, 32)}`;
    }
  } catch {
    // fall back to legacy implementation
  }

  let hash = 0;
  for (let i = 0; i < str.length; i++) {
    hash = (hash << 5) - hash + str.charCodeAt(i);
    hash |= 0;
  }
  return `h${Math.abs(hash).toString(36)}`;
}

export class SecureStorage {
  private readonly storage: Storage;
  private readonly encryptionKey: string;
  private readonly keyPromise: Promise<CryptoKey> | null;

  constructor(
    storage: Storage = typeof localStorage !== 'undefined' ? localStorage : ({} as Storage),
    encryptionKey: string = _DEFAULT_ENCRYPTION_KEY,
    useStrongEncryption = false
  ) {
    this.storage = storage;
    this.encryptionKey = encryptionKey;
    this.keyPromise =
      useStrongEncryption && getCrypto()?.subtle ? createAesKeyFromPassphrase(encryptionKey) : null;
  }

  async setItem(key: string, value: unknown): Promise<void> {
    const serialized = JSON.stringify(value);
    try {
      const encrypted = this.keyPromise
        ? await encryptString(serialized, await this.keyPromise)
        : base64Encode(encodeURIComponent(serialized));
      this.storage.setItem(key, encrypted);
    } catch (error) {
      console.error('Failed to encrypt and store data:', error);
      throw error;
    }
  }

  async getItem<T = unknown>(key: string): Promise<T | null> {
    const payload = this.storage.getItem(key);
    if (!payload) {
      return null;
    }
    try {
      const decrypted = this.keyPromise
        ? await decryptString(payload, await this.keyPromise)
        : decodeURIComponent(base64Decode(payload));
      return JSON.parse(decrypted) as T;
    } catch (error) {
      console.error('Failed to decrypt and parse data:', error);
      return null;
    }
  }

  removeItem(key: string): void {
    this.storage.removeItem(key);
  }

  clear(): void {
    this.storage.clear();
  }
}

export const secureStorage = new SecureStorage();
