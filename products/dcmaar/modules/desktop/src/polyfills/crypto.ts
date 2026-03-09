/**
 * Browser polyfill for node:crypto module
 *
 * Provides browser-compatible versions of Node.js crypto functions
 * using the Web Crypto API.
 */

/**
 * Generate a random UUID v4
 * Uses browser's crypto.randomUUID() if available, otherwise generates manually
 */
export function randomUUID(): string {
  // Use native crypto.randomUUID if available (modern browsers)
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }

  // Fallback: generate UUID v4 manually
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);

  // Set version (4) and variant bits
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;

  // Convert to UUID string
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');

  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

/**
 * Generate random bytes
 */
export function randomBytes(size: number): Uint8Array {
  const bytes = new Uint8Array(size);
  crypto.getRandomValues(bytes);
  return bytes;
}

/**
 * Create a hash (using Web Crypto API)
 */
export async function createHash(algorithm: string): Promise<{
  update: (data: string | Uint8Array) => void;
  digest: (encoding?: string) => Promise<string>;
}> {
  const data: (string | Uint8Array)[] = [];

  return {
    update: (chunk: string | Uint8Array) => {
      data.push(chunk);
    },
    digest: async (encoding: string = 'hex') => {
      // Concatenate all data
      const combined = data.map(d => (typeof d === 'string' ? new TextEncoder().encode(d) : d));

      const totalLength = combined.reduce((sum, arr) => sum + arr.length, 0);
      const buffer = new Uint8Array(totalLength);

      let offset = 0;
      for (const arr of combined) {
        buffer.set(arr, offset);
        offset += arr.length;
      }

      // Hash using Web Crypto API
      const algoMap: Record<string, string> = {
        sha256: 'SHA-256',
        sha512: 'SHA-512',
        sha1: 'SHA-1',
      };

      const hashBuffer = await crypto.subtle.digest(
        algoMap[algorithm.toLowerCase()] || 'SHA-256',
        buffer
      );

      // Convert to requested encoding
      if (encoding === 'hex') {
        return Array.from(new Uint8Array(hashBuffer))
          .map(b => b.toString(16).padStart(2, '0'))
          .join('');
      } else if (encoding === 'base64') {
        const bytes = new Uint8Array(hashBuffer);
        let binary = '';
        for (let i = 0; i < bytes.length; i++) {
          binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
      }

      return hashBuffer.toString();
    },
  };
}

// Default export for module compatibility
export default {
  randomUUID,
  randomBytes,
  createHash,
};
