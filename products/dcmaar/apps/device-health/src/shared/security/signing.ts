import type { BatchEnvelope, Signed } from '../../core/interfaces';

export interface PublicKeyMaterial {
  kid: string;
  key: CryptoKey;
  algorithm: AlgorithmIdentifier;
}

export interface BatchSigner {
  sign(batch: BatchEnvelope): Promise<string>;
}

export interface BatchSignerOptions {
  /**
   * Shared secret used for HMAC signing. When omitted, a random secret is
   * generated for the lifetime of the signer.
   */
  secret?: string;
  /**
   * Hash algorithm used for the HMAC signature.
   */
  hash?: 'SHA-256' | 'SHA-384' | 'SHA-512';
}

const encoder = new TextEncoder();

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

function getSubtle(): SubtleCrypto {
  const cryptoImpl = globalThis.crypto;
  if (!cryptoImpl?.subtle) {
    throw new Error('Web Crypto API is not available in this environment');
  }
  return cryptoImpl.subtle;
}

function toBase64Url(buffer: ArrayBuffer): string {
  const binary = String.fromCharCode(...new Uint8Array(buffer));
  return base64Encode(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/u, '');
}

function fromBase64Url(encoded: string): Uint8Array {
  const padded = encoded.replace(/-/g, '+').replace(/_/g, '/');
  const bytes = base64Decode(padded);
  const output = new Uint8Array(bytes.length);
  for (let i = 0; i < bytes.length; i++) {
    output[i] = bytes.charCodeAt(i);
  }
  return output;
}

function normaliseJson(value: unknown): string {
  return JSON.stringify(value, (_key, val) => {
    if (typeof val !== 'object' || val === null || Array.isArray(val)) {
      return val;
    }
    const ordered: Record<string, unknown> = {};
    for (const key of Object.keys(val).sort()) {
      ordered[key] = (val as Record<string, unknown>)[key];
    }
    return ordered;
  });
}

class HmacBatchSigner implements BatchSigner {
  private readonly keyPromise: Promise<CryptoKey>;
  private readonly hash: BatchSignerOptions['hash'];

  constructor(options: BatchSignerOptions) {
    const hash = options.hash ?? 'SHA-256';
    const secret = options.secret ?? generateSecret();

    this.hash = hash;
    this.keyPromise = getSubtle().importKey(
      'raw',
      encoder.encode(secret),
      {
        name: 'HMAC',
        hash: { name: hash },
      },
      false,
      ['sign']
    );
  }

  async sign(batch: BatchEnvelope): Promise<string> {
    const key = await this.keyPromise;
    const payload = encoder.encode(normaliseJson(batch));
    const signature = await getSubtle().sign({ name: 'HMAC' }, key, payload);
    return toBase64Url(signature);
  }
}

export function createBatchSigner(options: BatchSignerOptions = {}): BatchSigner {
  return new HmacBatchSigner(options);
}

export async function importPublicKey(
  jwk: JsonWebKey,
  kid: string,
  algorithm: AlgorithmIdentifier = { name: 'RSASSA-PKCS1-v1_5' } as AlgorithmIdentifier
): Promise<PublicKeyMaterial> {
  const key = await getSubtle().importKey('jwk', jwk, algorithm, false, ['verify']);
  return {
    kid,
    key,
    algorithm,
  };
}

export async function verifySignedPayload<T>(
  signed: Signed<T>,
  keys: PublicKeyMaterial[]
): Promise<boolean> {
  const keyMaterial = keys.find((material) => material.kid === signed.kid);
  if (!keyMaterial) {
    return false;
  }

  const canonicalPayload = normaliseJson(signed.payload);
  const payloadBytes = encoder.encode(canonicalPayload);
  const signature = fromBase64Url(signed.jws);

  return getSubtle().verify(
    keyMaterial.algorithm,
    keyMaterial.key,
    signature as BufferSource,
    payloadBytes
  );
}

export async function signPayload<T>(
  payload: T,
  key: CryptoKey,
  algorithm: AlgorithmIdentifier = { name: 'RSASSA-PKCS1-v1_5' }
): Promise<string> {
  const canonicalPayload = normaliseJson(payload);
  const bytes = encoder.encode(canonicalPayload);
  const signature = await getSubtle().sign(algorithm, key, bytes);
  return toBase64Url(signature);
}

function generateSecret(): string {
  const cryptoImpl = globalThis.crypto;
  if (!cryptoImpl?.getRandomValues) {
    throw new Error('Web Crypto API is not available in this environment');
  }
  const random = cryptoImpl.getRandomValues(new Uint8Array(32));
  return toBase64Url(random.buffer);
}
