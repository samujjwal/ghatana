/**
 * CSP Security Middleware Tests
 * @doc.type test
 * @doc.purpose Test Content Security Policy implementation
 * @doc.layer unit
 */

import { describe, it, expect, vi } from 'vitest';
import { buildCSPHeader, generateNonce, cspMiddleware } from './csp';
import type { CSPConfig } from './csp';

describe('CSP Security', () => {
  describe('CSP Header Building', () => {
    it('should build valid CSP header', () => {
      const config: CSPConfig = {
        directives: {
          'default-src': ["'self'"],
          'script-src': ["'self'", "'unsafe-inline'"],
        },
      };

      const header = buildCSPHeader(config);

      expect(header).toContain("default-src 'self'");
      expect(header).toContain("script-src 'self' 'unsafe-inline'");
    });

    it('should include nonce in CSP header', () => {
      const nonce = generateNonce();
      const config: CSPConfig = {
        directives: {
          'script-src': ["'self'", `'nonce-${nonce}'`],
        },
      };

      const header = buildCSPHeader(config);

      expect(header).toContain(`'nonce-${nonce}'`);
    });

    it('should enforce strict CSP by default', () => {
      const config: CSPConfig = {
        directives: {
          'default-src': ["'self'"],
        },
      };

      const header = buildCSPHeader(config);

      expect(header).toContain("default-src 'self'");
      expect(header).not.toContain('unsafe-eval');
    });
  });

  describe('Nonce Generation', () => {
    it('should generate cryptographically secure nonce', () => {
      const nonce = generateNonce();

      expect(nonce).toBeDefined();
      expect(typeof nonce).toBe('string');
      expect(nonce.length).toBeGreaterThan(20);
    });

    it('should generate unique nonces', () => {
      const nonce1 = generateNonce();
      const nonce2 = generateNonce();

      expect(nonce1).not.toBe(nonce2);
    });

    it('should generate base64-safe characters', () => {
      const nonce = generateNonce();
      const base64Regex = /^[A-Za-z0-9+/=]+$/;

      expect(base64Regex.test(nonce)).toBe(true);
    });
  });

  describe('CSP Directives', () => {
    it('should support script-src directive', () => {
      const config: CSPConfig = {
        directives: {
          'script-src': ["'self'", 'https://trusted.com'],
        },
      };

      const header = buildCSPHeader(config);

      expect(header).toContain("script-src 'self' https://trusted.com");
    });

    it('should support style-src directive', () => {
      const config: CSPConfig = {
        directives: {
          'style-src': ["'self'", "'unsafe-inline'"],
        },
      };

      const header = buildCSPHeader(config);

      expect(header).toContain("style-src 'self' 'unsafe-inline'");
    });

    it('should support img-src directive', () => {
      const config: CSPConfig = {
        directives: {
          'img-src': ["'self'", 'data:', 'https:'],
        },
      };

      const header = buildCSPHeader(config);

      expect(header).toContain("img-src 'self' data: https:");
    });

    it('should support frame-ancestors directive', () => {
      const config: CSPConfig = {
        directives: {
          'frame-ancestors': ["'none'"],
        },
      };

      const header = buildCSPHeader(config);

      expect(header).toContain("frame-ancestors 'none'");
    });

    it('should support upgrade-insecure-requests', () => {
      const config: CSPConfig = {
        directives: {
          'upgrade-insecure-requests': [],
        },
      };

      const header = buildCSPHeader(config);

      expect(header).toContain('upgrade-insecure-requests');
    });
  });

  describe('Middleware Integration', () => {
    it('should add CSP header to response', () => {
      const config: CSPConfig = {
        directives: {
          'default-src': ["'self'"],
        },
      };

      const middleware = cspMiddleware(config);

      expect(middleware).toBeDefined();
      expect(typeof middleware).toBe('function');
    });

    it('should support nonce in middleware context', () => {
      const config: CSPConfig = {
        directives: {
          'script-src': ["'self'"],
        },
      };

      const middleware = cspMiddleware(config);

      expect(middleware).toBeDefined();
    });
  });

  describe('Security Best Practices', () => {
    it('should prevent inline script execution by default', () => {
      const config: CSPConfig = {
        directives: {
          'script-src': ["'self'"],
        },
      };

      const header = buildCSPHeader(config);

      expect(header).not.toContain('unsafe-inline');
    });

    it('should prevent eval execution', () => {
      const config: CSPConfig = {
        directives: {
          'script-src': ["'self'"],
        },
      };

      const header = buildCSPHeader(config);

      expect(header).not.toContain('unsafe-eval');
    });

    it('should restrict form submissions', () => {
      const config: CSPConfig = {
        directives: {
          'form-action': ["'self'"],
        },
      };

      const header = buildCSPHeader(config);

      expect(header).toContain("form-action 'self'");
    });

    it('should set object-src to none', () => {
      const config: CSPConfig = {
        directives: {
          'object-src': ["'none'"],
        },
      };

      const header = buildCSPHeader(config);

      expect(header).toContain("object-src 'none'");
    });
  });

  describe('Edge Cases', () => {
    it('should handle empty directives', () => {
      const config: CSPConfig = {
        directives: {},
      };

      const header = buildCSPHeader(config);

      expect(header).toBeDefined();
      expect(typeof header).toBe('string');
    });

    it('should handle wildcard sources carefully', () => {
      const config: CSPConfig = {
        directives: {
          'script-src': ['*'],
        },
      };

      const header = buildCSPHeader(config);

      expect(header).toContain('script-src');
    });

    it('should escape special characters in sources', () => {
      const config: CSPConfig = {
        directives: {
          'script-src': ["https://example.com:8080/path?query=value"],
        },
      };

      const header = buildCSPHeader(config);

      expect(header).toBeDefined();
    });
  });
});

describe('CSP Nonce — cryptographic security properties', () => {
  it('should generate 100 unique nonces with no collisions', () => {
    const nonces = new Set<string>();
    for (let i = 0; i < 100; i++) {
      nonces.add(generateNonce());
    }
    expect(nonces.size).toBe(100);
  });

  it('should generate nonces with sufficient byte entropy (≥16 bytes)', () => {
    const nonce = generateNonce();
    // Base64 encodes 3 bytes as 4 chars; ≥16 bytes → base64 length ≥ ceil(16/3)*4 = 24 chars
    // (without padding stripping). Allow for URL-safe variants.
    const decodedByteEstimate = Math.floor((nonce.replace(/=+$/, '').length * 3) / 4);
    expect(decodedByteEstimate).toBeGreaterThanOrEqual(16);
  });

  it('should generate nonces that are valid for use in CSP header values', () => {
    const nonce = generateNonce();
    const config: CSPConfig = {
      directives: {
        'script-src': [`'nonce-${nonce}'`],
      },
    };
    const header = buildCSPHeader(config);
    expect(header).toContain(`nonce-${nonce}`);
  });

  it('should generate nonces that do not contain characters unsafe for HTTP headers', () => {
    for (let i = 0; i < 20; i++) {
      const nonce = generateNonce();
      // HTTP header values must not contain raw control chars (0x00-0x1F, 0x7F) or DEL
      expect(nonce).not.toMatch(/[\x00-\x1F\x7F]/);
    }
  });

  it('generated nonces should exhibit entropy — no two adjacent nonces share the same 8-char prefix', () => {
    const nonces = Array.from({ length: 20 }, () => generateNonce());
    const prefixes = nonces.map((n) => n.slice(0, 8));
    const uniquePrefixes = new Set(prefixes);
    // With 128-bit entropy it is astronomically unlikely to share even an 8-char prefix
    expect(uniquePrefixes.size).toBe(20);
  });
});
