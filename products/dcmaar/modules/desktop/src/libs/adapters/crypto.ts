/**
 * WebCrypto utilities for AES-GCM encryption/decryption.
 * Used for encrypting queue data and sensitive storage.
 */

export interface EncryptionKey {
  key: CryptoKey;
  algorithm: string;
}

export interface EncryptedData {
  ciphertext: string; // base64url
  iv: string; // base64url
  tag?: string; // base64url (included in ciphertext for AES-GCM)
}

export class CryptoService {
  private static readonly ALGORITHM = 'AES-GCM';
  private static readonly KEY_LENGTH = 256;
  private static readonly IV_LENGTH = 12; // 96 bits recommended for AES-GCM

  async generateKey(): Promise<CryptoKey> {
    return await crypto.subtle.generateKey(
      {
        name: CryptoService.ALGORITHM,
        length: CryptoService.KEY_LENGTH,
      },
      true, // extractable
      ['encrypt', 'decrypt'],
    );
  }

  async deriveKey(password: string, salt: Uint8Array): Promise<CryptoKey> {
    const encoder = new TextEncoder();
    const keyMaterial = await crypto.subtle.importKey(
      'raw',
      encoder.encode(password),
      'PBKDF2',
      false,
      ['deriveKey'],
    );

    return await crypto.subtle.deriveKey(
      {
        name: 'PBKDF2',
        // @ts-ignore - Uint8Array is compatible with BufferSource
        salt,
        iterations: 100000,
        hash: 'SHA-256',
      },
      keyMaterial,
      {
        name: CryptoService.ALGORITHM,
        length: CryptoService.KEY_LENGTH,
      },
      false, // not extractable
      ['encrypt', 'decrypt'],
    );
  }

  async encrypt(data: unknown, key: CryptoKey): Promise<EncryptedData> {
    const iv = crypto.getRandomValues(new Uint8Array(CryptoService.IV_LENGTH));
    const encrypted = await crypto.subtle.encrypt(
      { name: 'AES-GCM', iv },
      key,
      // @ts-ignore - Uint8Array is compatible with BufferSource
      data
    );

    return {
      ciphertext: this.arrayBufferToBase64Url(encrypted),
      iv: this.arrayBufferToBase64Url(iv.buffer),
    };
  }

  async decrypt(encrypted: EncryptedData, key: CryptoKey): Promise<unknown> {
    const iv = this.base64UrlToArrayBuffer(encrypted.iv);
    const ciphertext = this.base64UrlToArrayBuffer(encrypted.ciphertext);

    const decrypted = await crypto.subtle.decrypt(
      { name: 'AES-GCM', iv },
      key,
      // @ts-ignore - ArrayBuffer type compatibility
      ciphertext
    );

    const decoder = new TextDecoder();
    const json = decoder.decode(decrypted);
    return JSON.parse(json);
  }

  async exportKey(key: CryptoKey): Promise<string> {
    const exported = await crypto.subtle.exportKey('raw', key);
    return this.arrayBufferToBase64Url(exported);
  }

  async importKey(keyData: string): Promise<CryptoKey> {
    const buffer = this.base64UrlToArrayBuffer(keyData);

    return await crypto.subtle.importKey(
      'raw',
      buffer,
      {
        name: CryptoService.ALGORITHM,
        length: CryptoService.KEY_LENGTH,
      },
      true,
      ['encrypt', 'decrypt'],
    );
  }

  generateSalt(): Uint8Array {
    return crypto.getRandomValues(new Uint8Array(16));
  }

  private arrayBufferToBase64Url(buffer: ArrayBuffer): string {
    const bytes = new Uint8Array(buffer);
    let binary = '';
    for (let i = 0; i < bytes.length; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    const base64 = btoa(binary);
    return base64.replace(/\+/g, '-').replace(/\//g, '_').replace(/=/g, '');
  }

  private base64UrlToArrayBuffer(base64url: string): ArrayBuffer {
    const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
    const padded = base64.padEnd(
      base64.length + ((4 - (base64.length % 4)) % 4),
      '=',
    );
    const binary = atob(padded);
    const bytes = new Uint8Array(binary.length);

    for (let i = 0; i < binary.length; i++) {
      bytes[i] = binary.charCodeAt(i);
    }

    return bytes.buffer;
  }
}

export const createCryptoService = (): CryptoService => {
  return new CryptoService();
};
