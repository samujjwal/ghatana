import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import type { FastifyRequest } from 'fastify';

import {
  canUseTrustedProxyAuth,
  hasTrustedProxyIdentityHeaders,
  hasTrustedProxySecret,
  isPrivateOrLoopbackIp,
  isTrustedProxyAuthEnabled,
} from '../trustedProxyAuth';

describe('trustedProxyAuth', () => {
  const originalNodeEnv = process.env.NODE_ENV;
  const originalTrustedHeaders = process.env.TRUST_PROXY_AUTH_HEADERS;
  const originalTrustedSecret = process.env.TRUST_PROXY_AUTH_SHARED_SECRET;

  beforeEach(() => {
    process.env.NODE_ENV = 'development';
    delete process.env.TRUST_PROXY_AUTH_HEADERS;
    delete process.env.TRUST_PROXY_AUTH_SHARED_SECRET;
  });

  afterEach(() => {
    process.env.NODE_ENV = originalNodeEnv;
    process.env.TRUST_PROXY_AUTH_HEADERS = originalTrustedHeaders;
    process.env.TRUST_PROXY_AUTH_SHARED_SECRET = originalTrustedSecret;
  });

  it('requires explicit opt-in configuration', () => {
    expect(isTrustedProxyAuthEnabled()).toBe(false);

    process.env.TRUST_PROXY_AUTH_HEADERS = 'true';

    expect(isTrustedProxyAuthEnabled()).toBe(true);
  });

  it('requires a configured shared secret', () => {
    process.env.TRUST_PROXY_AUTH_HEADERS = 'true';

    expect(
      hasTrustedProxySecret({ headers: {} } as unknown as FastifyRequest),
    ).toBe(false);

    process.env.TRUST_PROXY_AUTH_SHARED_SECRET = 'internal-secret';

    expect(
      hasTrustedProxySecret({ headers: {} } as unknown as FastifyRequest),
    ).toBe(false);

    expect(
      hasTrustedProxySecret({
        headers: { 'x-trusted-proxy-secret': 'internal-secret' },
      } as unknown as FastifyRequest),
    ).toBe(true);
  });

  it('recognizes private and loopback address ranges', () => {
    expect(isPrivateOrLoopbackIp('127.0.0.1')).toBe(true);
    expect(isPrivateOrLoopbackIp('::1')).toBe(true);
    expect(isPrivateOrLoopbackIp('::ffff:192.168.1.15')).toBe(true);
    expect(isPrivateOrLoopbackIp('8.8.8.8')).toBe(false);
  });

  it('allows trusted proxy headers only from opted-in private sources', () => {
    process.env.TRUST_PROXY_AUTH_HEADERS = 'true';
    process.env.TRUST_PROXY_AUTH_SHARED_SECRET = 'internal-secret';

    expect(
      canUseTrustedProxyAuth({
        ip: '127.0.0.1',
        headers: {
          'x-tenant-id': 'tenant-1',
          'x-user-id': 'user-1',
          'x-user-role': 'admin',
          'x-trusted-proxy-secret': 'internal-secret',
        },
      } as unknown as FastifyRequest),
    ).toBe(true);
    expect(canUseTrustedProxyAuth({ ip: '8.8.8.8' } as unknown as FastifyRequest)).toBe(false);
  });

  it('requires a complete trusted identity tuple', () => {
    expect(
      hasTrustedProxyIdentityHeaders({
        headers: {
          'x-tenant-id': 'tenant-1',
          'x-user-id': 'user-1',
          'x-user-role': 'admin',
        },
      } as unknown as FastifyRequest),
    ).toBe(true);
    expect(
      hasTrustedProxyIdentityHeaders({
        headers: {
          'x-tenant-id': 'tenant-1',
          'x-user-role': 'admin',
        },
      } as unknown as FastifyRequest),
    ).toBe(false);
    expect(
      hasTrustedProxyIdentityHeaders({
        headers: {
          authorization: 'Bearer token',
          'x-tenant-id': 'tenant-1',
          'x-user-id': 'user-1',
          'x-user-role': 'admin',
        },
      } as unknown as FastifyRequest),
    ).toBe(false);
  });

  it('requires the shared secret when configured', () => {
    process.env.TRUST_PROXY_AUTH_HEADERS = 'true';
    process.env.TRUST_PROXY_AUTH_SHARED_SECRET = 'internal-secret';

    expect(
      hasTrustedProxySecret({
        headers: { 'x-trusted-proxy-secret': 'internal-secret' },
      } as unknown as FastifyRequest),
    ).toBe(true);
    expect(
      canUseTrustedProxyAuth({
        ip: '127.0.0.1',
        headers: {
          'x-tenant-id': 'tenant-1',
          'x-user-id': 'user-1',
          'x-user-role': 'admin',
          'x-trusted-proxy-secret': 'internal-secret',
        },
      } as unknown as FastifyRequest),
    ).toBe(true);
    expect(
      canUseTrustedProxyAuth({
        ip: '127.0.0.1',
        headers: {
          'x-tenant-id': 'tenant-1',
          'x-user-id': 'user-1',
          'x-user-role': 'admin',
          'x-trusted-proxy-secret': 'wrong-secret',
        },
      } as unknown as FastifyRequest),
    ).toBe(false);
  });
});
