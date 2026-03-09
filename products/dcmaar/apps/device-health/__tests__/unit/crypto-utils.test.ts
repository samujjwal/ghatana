import { describe, it, expect } from 'vitest';
import { base64ToArrayBuffer, pemToArrayBuffer, derToRawSignature, __test__ } from '../../src/shared/crypto/utils';

function hexToUint8Array(hex: string): Uint8Array {
  return new Uint8Array(hex.match(/../g)!.map(b => parseInt(b, 16)));
}

describe('Crypto Utils', () => {
  describe('base64ToArrayBuffer', () => {
    it('should convert base64 to ArrayBuffer', () => {
      const base64 = 'SGVsbG8gV29ybGQh';
      const result = base64ToArrayBuffer(base64);
      const actual = Array.from(new Uint8Array(result));
      const expected = Array.from(new TextEncoder().encode('Hello World!'));
      expect(actual).toEqual(expected);
    });
  });

  describe('pemToArrayBuffer', () => {
    it('should convert PEM to ArrayBuffer', () => {
      const pem = `-----BEGIN PUBLIC KEY-----
        SGVsbG8gV29ybGQh
        -----END PUBLIC KEY-----`;
      const result = pemToArrayBuffer(pem);
      const actual = Array.from(new Uint8Array(result));
      const expected = Array.from(new TextEncoder().encode('Hello World!'));
      expect(actual).toEqual(expected);
    });
  });

  describe('derToRawSignature', () => {
    it('should convert DER signature to raw format', () => {
      // This is a sample DER signature (shortened for example)
      const derSignature = hexToUint8Array('304402201234567890abcdef0123456789abcdef0123456789abcdef0123456789abcdef022100abcdef0123456789abcdef0123456789abcdef0123456789abcdef01234567');
      const result = derToRawSignature(derSignature);
      
      // The result should be the concatenation of r and s values
      expect(result.length).toBe(64); // 32 bytes for r + 32 bytes for s
    });
  });

  describe('_normalizeDer', () => {
    it('should normalize DER signature', () => {
      const { _normalizeDer } = __test__;
      // This is a sample DER signature (shortened for example)
      const derSignature = hexToUint8Array('304402201234567890abcdef0123456789abcdef0123456789abcdef0123456789abcdef022100abcdef0123456789abcdef0123456789abcdef0123456789abcdef01234567');
      const result = _normalizeDer(derSignature);
      
      expect(Buffer.isBuffer(result)).toBe(true);
      expect(Uint8Array.from(result!)[0]).toBe(0x30); // DER sequence
    });
  });
});
