import {
  secureCompare,
  generateRandomString,
  createSignature,
  verifySignature,
  sanitizeInput,
  isSafeUrl,
  generateCsrfToken,
  verifyCsrfToken,
  encryptData,
  decryptData,
  sanitizeHeaders,
} from '../../../src/utils/security';

describe('security utils', () => {
  describe('secureCompare', () => {
    it('should return true for equal strings', () => {
      expect(secureCompare('test', 'test')).toBe(true);
      expect(secureCompare('', '')).toBe(true);
      expect(secureCompare('abc123', 'abc123')).toBe(true);
    });

    it('should return false for different strings', () => {
      expect(secureCompare('test', 'TEST')).toBe(false);
      expect(secureCompare('abc', 'xyz')).toBe(false);
      expect(secureCompare('test', 'test1')).toBe(false);
    });

    it('should return false for different length strings', () => {
      expect(secureCompare('short', 'longer string')).toBe(false);
      expect(secureCompare('', 'non-empty')).toBe(false);
    });

    it('should handle special characters', () => {
      expect(secureCompare('!@#$%', '!@#$%')).toBe(true);
      expect(secureCompare('!@#$%', '!@#$^')).toBe(false);
    });

    it('should handle unicode characters', () => {
      expect(secureCompare('café', 'café')).toBe(true);
      expect(secureCompare('café', 'cafe')).toBe(false);
    });
  });

  describe('generateRandomString', () => {
    it('should generate string of specified length', () => {
      expect(generateRandomString(10)).toHaveLength(10);
      expect(generateRandomString(32)).toHaveLength(32);
      expect(generateRandomString(64)).toHaveLength(64);
    });

    it('should generate default 32-character string', () => {
      expect(generateRandomString()).toHaveLength(32);
    });

    it('should generate unique strings', () => {
      const str1 = generateRandomString();
      const str2 = generateRandomString();
      expect(str1).not.toBe(str2);
    });

    it('should generate hexadecimal strings', () => {
      const str = generateRandomString(20);
      expect(/^[0-9a-f]+$/i.test(str)).toBe(true);
    });

    it('should handle edge cases', () => {
      expect(generateRandomString(1)).toHaveLength(1);
      expect(generateRandomString(2)).toHaveLength(2);
    });

    it('should generate even-length strings correctly', () => {
      const evenLength = 20;
      const result = generateRandomString(evenLength);
      expect(result).toHaveLength(evenLength);
    });

    it('should generate odd-length strings correctly', () => {
      const oddLength = 21;
      const result = generateRandomString(oddLength);
      expect(result).toHaveLength(oddLength);
    });
  });

  describe('createSignature', () => {
    it('should create HMAC signature', () => {
      const signature = createSignature('secret', 'message');
      expect(signature).toBeTruthy();
      expect(typeof signature).toBe('string');
    });

    it('should create consistent signatures for same input', () => {
      const sig1 = createSignature('secret', 'message');
      const sig2 = createSignature('secret', 'message');
      expect(sig1).toBe(sig2);
    });

    it('should create different signatures for different messages', () => {
      const sig1 = createSignature('secret', 'message1');
      const sig2 = createSignature('secret', 'message2');
      expect(sig1).not.toBe(sig2);
    });

    it('should create different signatures for different secrets', () => {
      const sig1 = createSignature('secret1', 'message');
      const sig2 = createSignature('secret2', 'message');
      expect(sig1).not.toBe(sig2);
    });

    it('should support different algorithms', () => {
      const sha256 = createSignature('secret', 'message', 'sha256');
      const sha512 = createSignature('secret', 'message', 'sha512');
      expect(sha256).not.toBe(sha512);
      expect(sha512.length).toBeGreaterThan(sha256.length);
    });

    it('should handle empty messages', () => {
      const signature = createSignature('secret', '');
      expect(signature).toBeTruthy();
    });

    it('should handle unicode messages', () => {
      const signature = createSignature('secret', 'café ☕');
      expect(signature).toBeTruthy();
    });
  });

  describe('verifySignature', () => {
    it('should verify valid signatures', () => {
      const signature = createSignature('secret', 'message');
      expect(verifySignature(signature, 'secret', 'message')).toBe(true);
    });

    it('should reject invalid signatures', () => {
      const signature = createSignature('secret', 'message');
      expect(verifySignature('invalid', 'secret', 'message')).toBe(false);
    });

    it('should reject signatures with wrong secret', () => {
      const signature = createSignature('secret1', 'message');
      expect(verifySignature(signature, 'secret2', 'message')).toBe(false);
    });

    it('should reject signatures with wrong message', () => {
      const signature = createSignature('secret', 'message1');
      expect(verifySignature(signature, 'secret', 'message2')).toBe(false);
    });

    it('should verify signatures with custom algorithms', () => {
      const signature = createSignature('secret', 'message', 'sha512');
      expect(verifySignature(signature, 'secret', 'message', 'sha512')).toBe(true);
    });

    it('should reject if algorithm mismatch', () => {
      const signature = createSignature('secret', 'message', 'sha256');
      expect(verifySignature(signature, 'secret', 'message', 'sha512')).toBe(false);
    });
  });

  describe('sanitizeInput', () => {
    it('should escape HTML special characters', () => {
      expect(sanitizeInput('<script>alert("xss")</script>'))
        .toBe('&lt;script&gt;alert(&quot;xss&quot;)&lt;&#x2F;script&gt;');
    });

    it('should escape ampersands', () => {
      expect(sanitizeInput('Tom & Jerry')).toBe('Tom &amp; Jerry');
    });

    it('should escape less than and greater than', () => {
      expect(sanitizeInput('<div>')).toBe('&lt;div&gt;');
    });

    it('should escape quotes', () => {
      expect(sanitizeInput('Say "hello"')).toBe('Say &quot;hello&quot;');
    });

    it('should escape single quotes', () => {
      expect(sanitizeInput("It's fine")).toBe('It&#x27;s fine');
    });

    it('should escape forward slashes', () => {
      expect(sanitizeInput('path/to/file')).toBe('path&#x2F;to&#x2F;file');
    });

    it('should handle empty strings', () => {
      expect(sanitizeInput('')).toBe('');
    });

    it('should handle complex XSS attempts', () => {
      const xss = '<img src=x onerror="alert(1)">';
      const sanitized = sanitizeInput(xss);
      expect(sanitized).not.toContain('<');
      expect(sanitized).not.toContain('>');
      expect(sanitized).not.toContain('"');
    });

    it('should preserve safe text', () => {
      expect(sanitizeInput('Hello World 123')).toBe('Hello World 123');
    });

    it('should handle multiple escapes in one string', () => {
      const result = sanitizeInput('<div id="test">A & B</div>');
      expect(result).toContain('&lt;');
      expect(result).toContain('&gt;');
      expect(result).toContain('&quot;');
      expect(result).toContain('&amp;');
    });
  });

  describe('isSafeUrl', () => {
    describe('protocol validation', () => {
      it('should allow http and https URLs', () => {
        expect(isSafeUrl('http://example.com')).toBe(true);
        expect(isSafeUrl('https://example.com')).toBe(true);
      });

      it('should allow ws and wss URLs', () => {
        expect(isSafeUrl('ws://example.com')).toBe(true);
        expect(isSafeUrl('wss://example.com')).toBe(true);
      });

      it('should reject other protocols', () => {
        expect(isSafeUrl('ftp://example.com')).toBe(false);
        expect(isSafeUrl('file:///etc/passwd')).toBe(false);
        expect(isSafeUrl('javascript:alert(1)')).toBe(false);
        expect(isSafeUrl('data:text/html,<script>alert(1)</script>')).toBe(false);
      });
    });

    describe('domain whitelisting', () => {
      it('should allow exact domain matches', () => {
        expect(isSafeUrl('https://example.com', ['example.com'])).toBe(true);
      });

      it('should allow subdomain matches', () => {
        expect(isSafeUrl('https://api.example.com', ['.example.com'])).toBe(true);
        expect(isSafeUrl('https://deep.api.example.com', ['.example.com'])).toBe(true);
      });

      it('should reject non-whitelisted domains', () => {
        expect(isSafeUrl('https://evil.com', ['example.com'])).toBe(false);
      });

      it('should handle multiple allowed domains', () => {
        const allowed = ['example.com', 'test.com'];
        expect(isSafeUrl('https://example.com', allowed)).toBe(true);
        expect(isSafeUrl('https://test.com', allowed)).toBe(true);
        expect(isSafeUrl('https://other.com', allowed)).toBe(false);
      });

      it('should handle subdomain pattern in whitelist', () => {
        const allowed = ['.example.com'];
        expect(isSafeUrl('https://example.com', allowed)).toBe(true);
        expect(isSafeUrl('https://api.example.com', allowed)).toBe(true);
      });
    });

    describe('private IP blocking', () => {
      it('should block localhost', () => {
        expect(isSafeUrl('http://localhost')).toBe(false);
        expect(isSafeUrl('http://127.0.0.1')).toBe(false);
        expect(isSafeUrl('http://127.0.0.2')).toBe(false);
      });

      it('should block private IPv4 ranges', () => {
        expect(isSafeUrl('http://10.0.0.1')).toBe(false);
        expect(isSafeUrl('http://172.16.0.1')).toBe(false);
        expect(isSafeUrl('http://172.31.255.255')).toBe(false);
        expect(isSafeUrl('http://192.168.1.1')).toBe(false);
      });

      it('should block IPv6 localhost', () => {
        expect(isSafeUrl('http://[::1]')).toBe(false);
      });

      it('should block IPv6 private addresses', () => {
        expect(isSafeUrl('http://[fc00::1]')).toBe(false);
        expect(isSafeUrl('http://[fd00::1]')).toBe(false);
        expect(isSafeUrl('http://[fe80::1]')).toBe(false);
      });

      it('should allow public IPv4 addresses', () => {
        expect(isSafeUrl('http://8.8.8.8')).toBe(true);
        expect(isSafeUrl('http://1.1.1.1')).toBe(true);
      });
    });

    describe('error handling', () => {
      it('should reject invalid URLs', () => {
        expect(isSafeUrl('not-a-url')).toBe(false);
        expect(isSafeUrl('://')).toBe(false);
        expect(isSafeUrl('')).toBe(false);
      });

      it('should handle malformed URLs', () => {
        expect(isSafeUrl('http://')).toBe(false);
        expect(isSafeUrl('http://]')).toBe(false);
      });
    });
  });

  describe('generateCsrfToken', () => {
    it('should generate token and hash', () => {
      const result = generateCsrfToken();
      expect(result.token).toBeTruthy();
      expect(result.hash).toBeTruthy();
      expect(typeof result.token).toBe('string');
      expect(typeof result.hash).toBe('string');
    });

    it('should generate unique tokens', () => {
      const token1 = generateCsrfToken();
      const token2 = generateCsrfToken();
      expect(token1.token).not.toBe(token2.token);
      expect(token1.hash).not.toBe(token2.hash);
    });

    it('should generate 32-character tokens', () => {
      const result = generateCsrfToken();
      expect(result.token).toHaveLength(32);
    });

    it('should generate verifiable tokens', () => {
      const { token, hash } = generateCsrfToken();
      expect(verifyCsrfToken(token, hash)).toBe(true);
    });
  });

  describe('verifyCsrfToken', () => {
    it('should verify valid CSRF tokens', () => {
      const { token, hash } = generateCsrfToken();
      expect(verifyCsrfToken(token, hash)).toBe(true);
    });

    it('should reject invalid tokens', () => {
      const { hash } = generateCsrfToken();
      expect(verifyCsrfToken('invalid-token', hash)).toBe(false);
    });

    it('should reject invalid hashes', () => {
      const { token } = generateCsrfToken();
      expect(verifyCsrfToken(token, 'invalid-hash')).toBe(false);
    });

    it('should reject mismatched token/hash pairs', () => {
      const pair1 = generateCsrfToken();
      const pair2 = generateCsrfToken();
      expect(verifyCsrfToken(pair1.token, pair2.hash)).toBe(false);
    });
  });

  describe('encryptData / decryptData', () => {
    it('should encrypt and decrypt data correctly', () => {
      const original = 'sensitive data';
      const secret = 'encryption-key';

      const encrypted = encryptData(original, secret);
      const decrypted = decryptData(encrypted, secret);

      expect(encrypted).not.toBe(original);
      expect(decrypted).toBe(original);
    });

    it('should handle empty strings', () => {
      const encrypted = encryptData('', 'secret');
      const decrypted = decryptData(encrypted, 'secret');
      expect(decrypted).toBe('');
    });

    it('should handle unicode data', () => {
      const original = 'café ☕ 中文';
      const encrypted = encryptData(original, 'secret');
      const decrypted = decryptData(encrypted, 'secret');
      expect(decrypted).toBe(original);
    });

    it('should produce different ciphertext for same data with different keys', () => {
      const data = 'test data';
      const enc1 = encryptData(data, 'key1');
      const enc2 = encryptData(data, 'key2');
      expect(enc1).not.toBe(enc2);
    });

    it('should fail to decrypt with wrong key', () => {
      const encrypted = encryptData('data', 'correct-key');
      const decrypted = decryptData(encrypted, 'wrong-key');
      expect(decrypted).not.toBe('data');
    });

    it('should handle long data', () => {
      const original = 'a'.repeat(1000);
      const encrypted = encryptData(original, 'secret');
      const decrypted = decryptData(encrypted, 'secret');
      expect(decrypted).toBe(original);
    });

    it('should return base64 encoded data', () => {
      const encrypted = encryptData('test', 'secret');
      expect(/^[A-Za-z0-9+/]+=*$/.test(encrypted)).toBe(true);
    });

    it('should handle special characters', () => {
      const original = '!@#$%^&*()_+-={}[]|\\:";\'<>?,./';
      const encrypted = encryptData(original, 'secret');
      const decrypted = decryptData(encrypted, 'secret');
      expect(decrypted).toBe(original);
    });
  });

  describe('sanitizeHeaders', () => {
    it('should pass through safe headers', () => {
      const headers = {
        'content-type': 'application/json',
        'accept': 'application/json',
        'user-agent': 'test-agent',
      };
      const sanitized = sanitizeHeaders(headers);
      expect(sanitized['content-type']).toBeDefined();
      expect(sanitized['accept']).toBeDefined();
      expect(sanitized['user-agent']).toBeDefined();
    });

    it('should remove blacklisted headers', () => {
      const headers = {
        'host': 'example.com',
        'connection': 'keep-alive',
        'content-length': '100',
        'transfer-encoding': 'chunked',
        'upgrade': 'websocket',
        'safe-header': 'value',
      };
      const sanitized = sanitizeHeaders(headers);
      expect(sanitized['host']).toBeUndefined();
      expect(sanitized['connection']).toBeUndefined();
      expect(sanitized['content-length']).toBeUndefined();
      expect(sanitized['transfer-encoding']).toBeUndefined();
      expect(sanitized['upgrade']).toBeUndefined();
      expect(sanitized['safe-header']).toBeDefined();
    });

    it('should remove proxy headers', () => {
      const headers = {
        'proxy-authorization': 'Basic xyz',
        'proxy-connection': 'keep-alive',
        'x-forwarded-for': '1.2.3.4',
        'x-forwarded-proto': 'https',
        'x-real-ip': '1.2.3.4',
        'safe-header': 'value',
      };
      const sanitized = sanitizeHeaders(headers);
      expect(sanitized['proxy-authorization']).toBeUndefined();
      expect(sanitized['proxy-connection']).toBeUndefined();
      expect(sanitized['x-forwarded-for']).toBeUndefined();
      expect(sanitized['x-forwarded-proto']).toBeUndefined();
      expect(sanitized['x-real-ip']).toBeUndefined();
      expect(sanitized['safe-header']).toBeDefined();
    });

    it('should remove headers with CRLF injection attempts', () => {
      const headers = {
        'normal-header': 'value',
        'injected\r\n': 'value',
        'also-injected': 'value\r\ninjected-header: value',
      };
      const sanitized = sanitizeHeaders(headers);
      expect(sanitized['normal-header']).toBeDefined();
      expect(sanitized['injected\r\n']).toBeUndefined();
    });

    it('should remove headers with URL-encoded CRLF', () => {
      const headers = {
        'header%0D%0A': 'value',
        'header%0d%0a': 'value',
        'normal': 'value',
      };
      const sanitized = sanitizeHeaders(headers);
      expect(sanitized['header%0D%0A']).toBeUndefined();
      expect(sanitized['header%0d%0a']).toBeUndefined();
      expect(sanitized['normal']).toBeDefined();
    });

    it('should remove headers with only whitespace', () => {
      const headers = {
        '   ': 'value',
        'key': '   ',
        'valid': 'value',
      };
      const sanitized = sanitizeHeaders(headers);
      expect(sanitized['   ']).toBeUndefined();
      expect(sanitized['key']).toBeUndefined();
      expect(sanitized['valid']).toBeDefined();
    });

    it('should normalize header keys to lowercase', () => {
      const headers = {
        'Content-Type': 'application/json',
        'ACCEPT': 'application/json',
        'User-Agent': 'test',
      };
      const sanitized = sanitizeHeaders(headers);
      expect(sanitized['content-type']).toBeDefined();
      expect(sanitized['accept']).toBeDefined();
      expect(sanitized['user-agent']).toBeDefined();
    });

    it('should trim header keys', () => {
      const headers = {
        '  content-type  ': 'application/json',
      };
      const sanitized = sanitizeHeaders(headers);
      expect(sanitized['content-type']).toBeDefined();
    });

    it('should sanitize header values', () => {
      const headers = {
        'test': '<script>alert(1)</script>',
      };
      const sanitized = sanitizeHeaders(headers);
      expect(sanitized['test']).not.toContain('<script>');
      expect(sanitized['test']).toContain('&lt;script&gt;');
    });

    it('should handle empty headers object', () => {
      const sanitized = sanitizeHeaders({});
      expect(sanitized).toEqual({});
    });

    it('should handle non-string header values', () => {
      const headers = {
        'number': 123 as any,
        'boolean': true as any,
        'null': null as any,
      };
      const sanitized = sanitizeHeaders(headers);
      // Values should be converted to strings and sanitized
      expect(sanitized['number']).toBe('123');
      expect(sanitized['boolean']).toBe('true');
    });
  });
});
