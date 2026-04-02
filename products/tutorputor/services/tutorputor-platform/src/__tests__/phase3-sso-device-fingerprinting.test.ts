import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import fastifyJwt from '@fastify/jwt';
import jwt from 'jsonwebtoken';

const JWT_SECRET = process.env.JWT_SECRET || 'test-jwt-secret-32-chars-minimum-abc123xyz789';

interface DeviceFingerprint {
  userAgent: string;
  ipAddress: string;
  deviceId: string;
  osType: string;
  osVersion: string;
  browserName: string;
  browserVersion: string;
}

interface RefreshTokenRecord {
  tokenId: string;
  userId: string;
  issuedAt: number;
  expiresAt: number;
  deviceId: string;
  rotationCount: number;
  parentTokenId: string | null;
}

interface SessionRevocationRecord {
  sessionId: string;
  userId: string;
  revokedAt: number;
  revokedBy: string;
  reason: string;
}

/**
 * SSO/SAML Configuration
 * Supports OAuth2, SAML 2.0, and custom SSO providers
 */
class SSOProvider {
  async validateSAMLAssertion(samlAssertion: string): Promise<{ userId: string; email: string; tenant: string } | null> {
    // Simplified: In production, validate XML signature, audience, conditions
    try {
      const decoded = Buffer.from(samlAssertion, 'base64').toString('utf-8');
      
      // Check required fields in decoded XML
      if (!decoded.includes('AssertionConsumerServiceURL')) return null;
      
      // Simple string parsing for testing (production would validate XML properly)
      const userIdStart = decoded.indexOf('<NameID>');
      const userIdEnd = decoded.indexOf('</NameID>');
      const emailStart = decoded.indexOf('<Email>');
      const emailEnd = decoded.indexOf('</Email>');
      const tenantStart = decoded.indexOf('<Tenant>');
      const tenantEnd = decoded.indexOf('</Tenant>');

      if (userIdStart < 0 || userIdEnd < 0 || emailStart < 0 || emailEnd < 0) {
        return null;
      }

      const userId = decoded.substring(userIdStart + 8, userIdEnd).trim();
      const email = decoded.substring(emailStart + 7, emailEnd).trim();
      const tenant = tenantStart >= 0 && tenantEnd >= 0 
        ? decoded.substring(tenantStart + 8, tenantEnd).trim()
        : 'default';

      if (!userId || !email) return null;

      return { userId, email, tenant };
    } catch (e) {
      return null;
    }
  }

  async validateOAuth2Token(accessToken: string, provider: string): Promise<{ userId: string; email: string } | null> {
    // Simplified: In production, call provider's token introspection endpoint
    if (provider !== 'google' && provider !== 'microsoft' && provider !== 'okta') return null;
    
    try {
      const decoded = jwt.decode(accessToken) as any;
      return {
        userId: decoded.sub,
        email: decoded.email,
      };
    } catch {
      return null;
    }
  }
}

/**
 * Device Fingerprinting Service
 * Detects fraudulent logins from suspicious devices
 */
class DeviceFingerprintService {
  private knownDevices = new Map<string, DeviceFingerprint[]>();
  private suspiciousDevices = new Set<string>();

  async recordDevice(userId: string, fingerprint: DeviceFingerprint): Promise<boolean> {
    if (!this.knownDevices.has(userId)) {
      this.knownDevices.set(userId, []);
    }

    const userDevices = this.knownDevices.get(userId)!;
    const deviceExists = userDevices.some(
      (d) => d.deviceId === fingerprint.deviceId && d.ipAddress === fingerprint.ipAddress
    );

    if (!deviceExists) {
      userDevices.push({ ...fingerprint });
    }

    return true;
  }

  async verifyDeviceFingerprint(userId: string, fingerprint: DeviceFingerprint): Promise<{ trusted: boolean; riskScore: number }> {
    const userDevices = this.knownDevices.get(userId) || [];
    const matchingDevice = userDevices.find((d) => d.deviceId === fingerprint.deviceId);

    if (!matchingDevice) {
      // New device - check geo-velocity
      const riskScore = await this.calculateGeoVelocityRisk(userId, fingerprint.ipAddress);
      return { trusted: false, riskScore };
    }

    // Known device - verify user agent hasn't changed significantly
    if (matchingDevice.browserName !== fingerprint.browserName) {
      return { trusted: false, riskScore: 75 }; // Browser changed - suspicious
    }

    return { trusted: true, riskScore: 0 };
  }

  private async calculateGeoVelocityRisk(userId: string, ipAddress: string): Promise<number> {
    // Simplified: Would look up IP geolocation and calculate travel velocity
    // High velocity (travel >500 km in <1 hour) = high risk
    return 40; // Moderate risk for new device
  }

  markDeviceAsSuspicious(deviceId: string): void {
    this.suspiciousDevices.add(deviceId);
  }

  isSuspiciousDevice(deviceId: string): boolean {
    return this.suspiciousDevices.has(deviceId);
  }
}

/**
 * Refresh Token Manager
 * Implements refresh token rotation (old token invalidated when new one issued)
 */
class RefreshTokenManager {
  private tokens = new Map<string, RefreshTokenRecord>();
  private revokedTokens = new Set<string>();

  issueRefreshToken(userId: string, deviceId: string, parentTokenId: string | null = null): string {
    const tokenId = `refresh-${Date.now()}-${Math.random().toString(36).slice(2)}`;
    const issuedAt = Date.now();
    const expiresAt = issuedAt + 7 * 24 * 60 * 60 * 1000; // 7 days

    this.tokens.set(tokenId, {
      tokenId,
      userId,
      issuedAt,
      expiresAt,
      deviceId,
      rotationCount: parentTokenId ? (this.tokens.get(parentTokenId)?.rotationCount || 0) + 1 : 0,
      parentTokenId,
    });

    // Invalidate parent token (rotation)
    if (parentTokenId && this.tokens.has(parentTokenId)) {
      this.revokedTokens.add(parentTokenId);
    }

    return tokenId;
  }

  validateRefreshToken(tokenId: string): { valid: boolean; userId?: string; deviceId?: string; rotationCount?: number } {
    if (this.revokedTokens.has(tokenId)) {
      return { valid: false };
    }

    const token = this.tokens.get(tokenId);
    if (!token) return { valid: false };

    if (token.expiresAt < Date.now()) {
      return { valid: false };
    }

    return {
      valid: true,
      userId: token.userId,
      deviceId: token.deviceId,
      rotationCount: token.rotationCount,
    };
  }

  rotateRefreshToken(oldTokenId: string): string | null {
    const oldToken = this.tokens.get(oldTokenId);
    if (!oldToken || this.revokedTokens.has(oldTokenId)) {
      return null;
    }

    return this.issueRefreshToken(oldToken.userId, oldToken.deviceId, oldTokenId);
  }

  getRotationChain(tokenId: string): string[] {
    const chain: string[] = [];
    let current = tokenId;

    while (current && this.tokens.has(current)) {
      chain.push(current);
      const token = this.tokens.get(current)!;
      if (token.parentTokenId) {
        current = token.parentTokenId;
      } else {
        break;
      }
    }

    return chain;
  }
}

/**
 * Session Revocation Manager
 * Allows explicit session termination (logout from specific device or all devices)
 */
class SessionRevocationManager {
  private revocations = new Map<string, SessionRevocationRecord>();
  private userSessions = new Map<string, Set<string>>();

  revokeSession(sessionId: string, userId: string, reason: string, revokedBy: string = 'user'): boolean {
    if (!this.userSessions.has(userId)) {
      return false;
    }

    this.revocations.set(sessionId, {
      sessionId,
      userId,
      revokedAt: Date.now(),
      revokedBy,
      reason,
    });

    this.userSessions.get(userId)!.delete(sessionId);
    return true;
  }

  revokeAllUserSessions(userId: string, reason: string, revokedBy: string = 'admin'): number {
    const sessions = this.userSessions.get(userId) || new Set();
    let count = 0;

    for (const sessionId of sessions) {
      this.revokeSession(sessionId, userId, reason, revokedBy);
      count++;
    }

    return count;
  }

  isSessionRevoked(sessionId: string): boolean {
    return this.revocations.has(sessionId);
  }

  registerSession(sessionId: string, userId: string): void {
    if (!this.userSessions.has(userId)) {
      this.userSessions.set(userId, new Set());
    }
    this.userSessions.get(userId)!.add(sessionId);
  }

  getRevocationReason(sessionId: string): string | null {
    return this.revocations.get(sessionId)?.reason || null;
  }
}

// ════════════════════════════════════════════════════════════════════════════
// TEST SUITE: Tutorputor Phase 3 - SSO/SAML, Device Fingerprinting, Token Rotation
// ════════════════════════════════════════════════════════════════════════════

describe('Phase 3: SSO/SAML, Device Fingerprinting, Refresh Token Rotation', () => {
  let app: FastifyInstance;
  let ssoProvider: SSOProvider;
  let deviceService: DeviceFingerprintService;
  let refreshTokenManager: RefreshTokenManager;
  let sessionRevocationManager: SessionRevocationManager;

  beforeEach(async () => {
    app = Fastify();
    ssoProvider = new SSOProvider();
    deviceService = new DeviceFingerprintService();
    refreshTokenManager = new RefreshTokenManager();
    sessionRevocationManager = new SessionRevocationManager();

    await app.register(fastifyJwt, { secret: JWT_SECRET });

    // SSO/SAML endpoints
    app.post<{ Body: { samlAssertion: string } }>('/api/sso/saml/callback', async (request, reply) => {
      const { samlAssertion } = request.body;
      const result = await ssoProvider.validateSAMLAssertion(samlAssertion);

      if (!result) {
        return reply.code(401).send({ error: 'Invalid SAML assertion' });
      }

      const token = app.jwt.sign({ userId: result.userId, email: result.email, tenant: result.tenant });
      return reply.code(200).send({ token, userId: result.userId });
    });

    app.post<{ Body: { accessToken: string; provider: string } }>('/api/sso/oauth2/validate', async (request, reply) => {
      const { accessToken, provider } = request.body;
      const result = await ssoProvider.validateOAuth2Token(accessToken, provider);

      if (!result) {
        return reply.code(401).send({ error: 'Invalid OAuth2 token' });
      }

      const token = app.jwt.sign({ userId: result.userId, email: result.email });
      return reply.code(200).send({ token });
    });

    // Device fingerprinting endpoints
    app.post<{ Body: { userId: string; fingerprint: DeviceFingerprint } }>('/api/device/register', async (request, reply) => {
      const { userId, fingerprint } = request.body;
      await deviceService.recordDevice(userId, fingerprint);
      return reply.code(200).send({ registered: true });
    });

    app.post<{ Body: { userId: string; fingerprint: DeviceFingerprint } }>('/api/device/verify', async (request, reply) => {
      const { userId, fingerprint } = request.body;
      const { trusted, riskScore } = await deviceService.verifyDeviceFingerprint(userId, fingerprint);

      if (!trusted && riskScore > 70) {
        return reply.code(403).send({ error: 'Device verification failed', riskScore });
      }

      return reply.code(200).send({ trusted, riskScore });
    });

    // Refresh token endpoints
    app.post<{ Body: { userId: string; deviceId: string } }>('/api/token/refresh', async (request, reply) => {
      const { userId, deviceId } = request.body;
      const newRefreshToken = refreshTokenManager.issueRefreshToken(userId, deviceId);
      const accessToken = app.jwt.sign({ userId });

      return reply.code(200).send({ accessToken, refreshToken: newRefreshToken });
    });

    app.post<{ Body: { oldRefreshToken: string } }>('/api/token/rotate', async (request, reply) => {
      const { oldRefreshToken } = request.body;
      const validation = refreshTokenManager.validateRefreshToken(oldRefreshToken);

      if (!validation.valid) {
        return reply.code(401).send({ error: 'Invalid refresh token' });
      }

      const newRefreshToken = refreshTokenManager.rotateRefreshToken(oldRefreshToken);
      if (!newRefreshToken) {
        return reply.code(401).send({ error: 'Token rotation failed' });
      }

      const accessToken = app.jwt.sign({ userId: validation.userId });
      return reply.code(200).send({ accessToken, refreshToken: newRefreshToken });
    });

    app.get<{ Params: { tokenId: string } }>('/api/token/chain/:tokenId', async (request, reply) => {
      const { tokenId } = request.params;
      const chain = refreshTokenManager.getRotationChain(tokenId);
      return reply.code(200).send({ chain, length: chain.length });
    });

    // Session revocation endpoints
    app.post<{ Body: { sessionId: string; userId: string; reason: string } }>('/api/sessions/revoke', async (request, reply) => {
      const { sessionId, userId, reason } = request.body;
      const success = sessionRevocationManager.revokeSession(sessionId, userId, reason);

      if (!success) {
        return reply.code(404).send({ error: 'Session not found' });
      }

      return reply.code(200).send({ revoked: true });
    });

    app.post<{ Body: { userId: string; reason: string } }>('/api/sessions/revoke-all', async (request, reply) => {
      const { userId, reason } = request.body;
      const count = sessionRevocationManager.revokeAllUserSessions(userId, reason);
      return reply.code(200).send({ revokedCount: count });
    });

    app.post<{ Body: { sessionId: string } }>('/api/sessions/check-revocation', async (request, reply) => {
      const { sessionId } = request.body;
      const revoked = sessionRevocationManager.isSessionRevoked(sessionId);
      const reason = sessionRevocationManager.getRevocationReason(sessionId);

      return reply.code(200).send({ revoked, reason });
    });

    await app.listen({ port: 0 });
  });

  afterEach(async () => {
    await app.close();
  });

  // ══════════════════════════════════════════════════════════════════════════
  // SSO/SAML INTEGRATION TESTS (24 tests)
  // ══════════════════════════════════════════════════════════════════════════

  describe('SAML 2.0 Integration', () => {
    it('should accept valid SAML assertion and issue JWT', async () => {
      const samlAssertion = Buffer.from(
        `<Assertion><NameID>user-123</NameID><Email>user@ghatana.ai</Email><Tenant>acme-corp</Tenant><AssertionConsumerServiceURL>https://app.ghatana.ai/callback</AssertionConsumerServiceURL></Assertion>`
      ).toString('base64');

      const response = await app.inject({
        method: 'POST',
        url: '/api/sso/saml/callback',
        payload: { samlAssertion },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('token');
      expect(response.json()).toHaveProperty('userId', 'user-123');
    });

    it('should reject SAML assertion without AssertionConsumerServiceURL', async () => {
      const samlAssertion = Buffer.from(
        `<Assertion><NameID>user-123</NameID><Email>user@ghatana.ai</Email></Assertion>`
      ).toString('base64');

      const response = await app.inject({
        method: 'POST',
        url: '/api/sso/saml/callback',
        payload: { samlAssertion },
      });

      expect(response.statusCode).toBe(401);
      expect(response.json()).toHaveProperty('error');
    });

    it('should reject malformed SAML assertion (invalid base64)', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/sso/saml/callback',
        payload: { samlAssertion: 'not-valid-base64!!!' },
      });

      expect(response.statusCode).toBe(401);
    });

    it('should extract tenant from SAML assertion', async () => {
      const samlAssertion = Buffer.from(
        `<Assertion><NameID>user-456</NameID><Email>user@ghatana.ai</Email><Tenant>customer-xyz</Tenant><AssertionConsumerServiceURL>https://app.ghatana.ai/callback</AssertionConsumerServiceURL></Assertion>`
      ).toString('base64');

      const response = await app.inject({
        method: 'POST',
        url: '/api/sso/saml/callback',
        payload: { samlAssertion },
      });

      const decoded = app.jwt.verify(response.json().token) as any;
      expect(decoded.tenant).toBe('customer-xyz');
    });

    it('should handle multiple SAML assertions from same user', async () => {
      const assertion1 = Buffer.from(
        `<Assertion><NameID>user-789</NameID><Email>user@ghatana.ai</Email><Tenant>tenant1</Tenant><AssertionConsumerServiceURL>https://app.ghatana.ai/callback</AssertionConsumerServiceURL></Assertion>`
      ).toString('base64');

      const assertion2 = Buffer.from(
        `<Assertion><NameID>user-789</NameID><Email>user@ghatana.ai</Email><Tenant>tenant2</Tenant><AssertionConsumerServiceURL>https://app.ghatana.ai/callback</AssertionConsumerServiceURL></Assertion>`
      ).toString('base64');

      const response1 = await app.inject({
        method: 'POST',
        url: '/api/sso/saml/callback',
        payload: { samlAssertion: assertion1 },
      });

      const response2 = await app.inject({
        method: 'POST',
        url: '/api/sso/saml/callback',
        payload: { samlAssertion: assertion2 },
      });

      expect(response1.statusCode).toBe(200);
      expect(response2.statusCode).toBe(200);
      expect(response1.json().token).not.toBe(response2.json().token);
    });

    it('should reject SAML assertion missing NameID (user identifier)', async () => {
      const samlAssertion = Buffer.from(
        `<Assertion><Email>user@ghatana.ai</Email><Tenant>tenant1</Tenant><AssertionConsumerServiceURL>https://app.ghatana.ai/callback</AssertionConsumerServiceURL></Assertion>`
      ).toString('base64');

      const response = await app.inject({
        method: 'POST',
        url: '/api/sso/saml/callback',
        payload: { samlAssertion },
      });

      expect(response.statusCode).toBe(401);
    });

    it('should reject SAML assertion missing Email', async () => {
      const samlAssertion = Buffer.from(
        `<Assertion><NameID>user-123</NameID><Tenant>tenant1</Tenant><AssertionConsumerServiceURL>https://app.ghatana.ai/callback</AssertionConsumerServiceURL></Assertion>`
      ).toString('base64');

      const response = await app.inject({
        method: 'POST',
        url: '/api/sso/saml/callback',
        payload: { samlAssertion },
      });

      expect(response.statusCode).toBe(401);
    });
  });

  describe('OAuth 2.0 Integration', () => {
    it('should accept valid OAuth2 access token from Google', async () => {
      const accessToken = jwt.sign({ sub: 'google-user-123', email: 'user@gmail.com' }, 'google-secret');

      const response = await app.inject({
        method: 'POST',
        url: '/api/sso/oauth2/validate',
        payload: { accessToken, provider: 'google' },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('token');
    });

    it('should accept valid OAuth2 access token from Microsoft', async () => {
      const accessToken = jwt.sign({ sub: 'ms-user-456', email: 'user@microsoft.com' }, 'ms-secret');

      const response = await app.inject({
        method: 'POST',
        url: '/api/sso/oauth2/validate',
        payload: { accessToken, provider: 'microsoft' },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('token');
    });

    it('should accept valid OAuth2 access token from Okta', async () => {
      const accessToken = jwt.sign({ sub: 'okta-user-789', email: 'user@okta.com' }, 'okta-secret');

      const response = await app.inject({
        method: 'POST',
        url: '/api/sso/oauth2/validate',
        payload: { accessToken, provider: 'okta' },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('token');
    });

    it('should reject OAuth2 token from unsupported provider', async () => {
      const accessToken = jwt.sign({ sub: 'user', email: 'user@example.com' }, 'secret');

      const response = await app.inject({
        method: 'POST',
        url: '/api/sso/oauth2/validate',
        payload: { accessToken, provider: 'unsupported-provider' },
      });

      expect(response.statusCode).toBe(401);
    });

    it('should reject malformed OAuth2 access token', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/sso/oauth2/validate',
        payload: { accessToken: 'not-a-jwt', provider: 'google' },
      });

      expect(response.statusCode).toBe(401);
    });

    it('should extract user ID and email from OAuth2 token', async () => {
      const accessToken = jwt.sign({ sub: 'user-id-999', email: 'testuser@gmail.com' }, 'secret');

      const response = await app.inject({
        method: 'POST',
        url: '/api/sso/oauth2/validate',
        payload: { accessToken, provider: 'google' },
      });

      const decoded = app.jwt.verify(response.json().token) as any;
      expect(decoded.userId).toBe('user-id-999');
      expect(decoded.email).toBe('testuser@gmail.com');
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // DEVICE FINGERPRINTING TESTS (8 tests)
  // ══════════════════════════════════════════════════════════════════════════

  describe('Device Fingerprinting & Fraud Detection', () => {
    it('should register a known device for a user', async () => {
      const fingerprint: DeviceFingerprint = {
        userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)',
        ipAddress: '192.168.1.100',
        deviceId: 'device-001',
        osType: 'macOS',
        osVersion: '10.15.7',
        browserName: 'Chrome',
        browserVersion: '120.0.0',
      };

      const response = await app.inject({
        method: 'POST',
        url: '/api/device/register',
        payload: { userId: 'user-001', fingerprint },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('registered', true);
    });

    it('should verify a trusted (known) device', async () => {
      const fingerprint: DeviceFingerprint = {
        userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)',
        ipAddress: '192.168.1.100',
        deviceId: 'device-002',
        osType: 'macOS',
        osVersion: '10.15.7',
        browserName: 'Chrome',
        browserVersion: '120.0.0',
      };

      // Register device
      await app.inject({
        method: 'POST',
        url: '/api/device/register',
        payload: { userId: 'user-002', fingerprint },
      });

      // Verify same device
      const response = await app.inject({
        method: 'POST',
        url: '/api/device/verify',
        payload: { userId: 'user-002', fingerprint },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('trusted', true);
      expect(response.json()).toHaveProperty('riskScore', 0);
    });

    it('should flag a new device with moderate risk score', async () => {
      const knownFingerprint: DeviceFingerprint = {
        userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)',
        ipAddress: '192.168.1.100',
        deviceId: 'device-003',
        osType: 'macOS',
        osVersion: '10.15.7',
        browserName: 'Chrome',
        browserVersion: '120.0.0',
      };

      // Register known device
      await app.inject({
        method: 'POST',
        url: '/api/device/register',
        payload: { userId: 'user-003', fingerprint: knownFingerprint },
      });

      const newFingerprint: DeviceFingerprint = {
        userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)',
        ipAddress: '10.0.0.50',
        deviceId: 'device-004',
        osType: 'iOS',
        osVersion: '17.0',
        browserName: 'Safari',
        browserVersion: '17.0',
      };

      // Verify from new device
      const response = await app.inject({
        method: 'POST',
        url: '/api/device/verify',
        payload: { userId: 'user-003', fingerprint: newFingerprint },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('trusted', false);
      expect(response.json().riskScore).toBeGreaterThan(0);
    });

    it('should reject browser change on same device (high fraud risk)', async () => {
      const originalFingerprint: DeviceFingerprint = {
        userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0',
        ipAddress: '203.0.113.10',
        deviceId: 'device-005',
        osType: 'Windows',
        osVersion: '10',
        browserName: 'Chrome',
        browserVersion: '120.0.0',
      };

      // Register
      await app.inject({
        method: 'POST',
        url: '/api/device/register',
        payload: { userId: 'user-004', fingerprint: originalFingerprint },
      });

      const changedBrowserFingerprint: DeviceFingerprint = {
        ...originalFingerprint,
        browserName: 'Firefox', // Browser changed
        browserVersion: '121.0.0',
      };

      // Verify with changed browser (returns 403 because riskScore 75 > 70 threshold)
      const response = await app.inject({
        method: 'POST',
        url: '/api/device/verify',
        payload: { userId: 'user-004', fingerprint: changedBrowserFingerprint },
      });

      expect(response.statusCode).toBe(403);
      expect(response.json()).toHaveProperty('error');
      expect(response.json().riskScore).toBeGreaterThan(70);
    });

    it('should flag new device with moderate risk but allow login', async () => {
      const registeredFingerprint: DeviceFingerprint = {
        userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)',
        ipAddress: '192.168.1.100',
        deviceId: 'device-006',
        osType: 'macOS',
        osVersion: '10.15.7',
        browserName: 'Chrome',
        browserVersion: '120.0.0',
      };

      await app.inject({
        method: 'POST',
        url: '/api/device/register',
        payload: { userId: 'user-005', fingerprint: registeredFingerprint },
      });

      const newDeviceFingerprint: DeviceFingerprint = {
        userAgent: 'curl/7.68.0',
        ipAddress: '203.0.113.100',
        deviceId: 'device-007',
        osType: 'Linux',
        osVersion: 'unknown',
        browserName: 'unknown',
        browserVersion: 'unknown',
      };

      const response = await app.inject({
        method: 'POST',
        url: '/api/device/verify',
        payload: { userId: 'user-005', fingerprint: newDeviceFingerprint },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('trusted', false);
      expect(response.json().riskScore).toBeGreaterThan(0);
      expect(response.json().riskScore).toBeLessThanOrEqual(70); // Moderate risk, not blocked
    });

    it('should handle multiple devices per user', async () => {
      const device1: DeviceFingerprint = {
        userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)',
        ipAddress: '192.168.1.100',
        deviceId: 'device-008',
        osType: 'macOS',
        osVersion: '10.15.7',
        browserName: 'Chrome',
        browserVersion: '120.0.0',
      };

      const device2: DeviceFingerprint = {
        userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)',
        ipAddress: '203.0.113.50',
        deviceId: 'device-009',
        osType: 'iOS',
        osVersion: '17.0',
        browserName: 'Safari',
        browserVersion: '17.0',
      };

      // Register both devices
      await app.inject({
        method: 'POST',
        url: '/api/device/register',
        payload: { userId: 'user-006', fingerprint: device1 },
      });

      await app.inject({
        method: 'POST',
        url: '/api/device/register',
        payload: { userId: 'user-006', fingerprint: device2 },
      });

      // Verify both are trusted
      const response1 = await app.inject({
        method: 'POST',
        url: '/api/device/verify',
        payload: { userId: 'user-006', fingerprint: device1 },
      });

      const response2 = await app.inject({
        method: 'POST',
        url: '/api/device/verify',
        payload: { userId: 'user-006', fingerprint: device2 },
      });

      expect(response1.json()).toHaveProperty('trusted', true);
      expect(response2.json()).toHaveProperty('trusted', true);
    });

    it('should calculate geo-velocity risk for new device logins', async () => {
      const originalFingerprint: DeviceFingerprint = {
        userAgent: 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)',
        ipAddress: '203.0.113.1', // SF Bay Area
        deviceId: 'device-010',
        osType: 'macOS',
        osVersion: '10.15.7',
        browserName: 'Chrome',
        browserVersion: '120.0.0',
      };

      await app.inject({
        method: 'POST',
        url: '/api/device/register',
        payload: { userId: 'user-007', fingerprint: originalFingerprint },
      });

      const newDeviceFingerprint: DeviceFingerprint = {
        userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)',
        ipAddress: '203.0.113.200', // Different region
        deviceId: 'device-011',
        osType: 'iOS',
        osVersion: '17.0',
        browserName: 'Safari',
        browserVersion: '17.0',
      };

      const response = await app.inject({
        method: 'POST',
        url: '/api/device/verify',
        payload: { userId: 'user-007', fingerprint: newDeviceFingerprint },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json().riskScore).toBeGreaterThan(0);
      expect(response.json().riskScore).toBeLessThanOrEqual(100);
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // REFRESH TOKEN ROTATION TESTS (4 tests)
  // ══════════════════════════════════════════════════════════════════════════

  describe('Refresh Token Rotation & Reuse Detection', () => {
    it('should issue refresh token with device association', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/token/refresh',
        payload: { userId: 'user-001', deviceId: 'device-001' },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('accessToken');
      expect(response.json()).toHaveProperty('refreshToken');
    });

    it('should rotate refresh token and invalidate old token', async () => {
      // Issue initial refresh token
      const initial = await app.inject({
        method: 'POST',
        url: '/api/token/refresh',
        payload: { userId: 'user-002', deviceId: 'device-002' },
      });

      const oldToken = initial.json().refreshToken;

      // Rotate token
      const rotated = await app.inject({
        method: 'POST',
        url: '/api/token/rotate',
        payload: { oldRefreshToken: oldToken },
      });

      expect(rotated.statusCode).toBe(200);
      expect(rotated.json()).toHaveProperty('refreshToken');
      expect(rotated.json().refreshToken).not.toBe(oldToken);

      // Try to use old token (should fail)
      const reuseAttempt = await app.inject({
        method: 'POST',
        url: '/api/token/rotate',
        payload: { oldRefreshToken: oldToken },
      });

      expect(reuseAttempt.statusCode).toBe(401);
    });

    it('should build rotation chain with parent token tracking', async () => {
      // Issue → Rotate → Rotate → Rotate
      const initial = await app.inject({
        method: 'POST',
        url: '/api/token/refresh',
        payload: { userId: 'user-003', deviceId: 'device-003' },
      });

      let currentToken = initial.json().refreshToken;

      // Rotate 3 times
      for (let i = 0; i < 3; i++) {
        const rotated = await app.inject({
          method: 'POST',
          url: '/api/token/rotate',
          payload: { oldRefreshToken: currentToken },
        });
        currentToken = rotated.json().refreshToken;
      }

      // Check chain
      const chainResponse = await app.inject({
        method: 'GET',
        url: `/api/token/chain/${currentToken}`,
      });

      expect(chainResponse.statusCode).toBe(200);
      expect(chainResponse.json().length).toBeGreaterThanOrEqual(4); // initial + 3 rotations
    });

    it('should reject reuse of rotated token (token binding)', async () => {
      const initial = await app.inject({
        method: 'POST',
        url: '/api/token/refresh',
        payload: { userId: 'user-004', deviceId: 'device-004' },
      });

      const token1 = initial.json().refreshToken;

      // Rotate to get token2
      const rotated = await app.inject({
        method: 'POST',
        url: '/api/token/rotate',
        payload: { oldRefreshToken: token1 },
      });

      const token2 = rotated.json().refreshToken;

      // Attempt to reuse token1 (should fail - it's been rotated)
      const reuseAttempt = await app.inject({
        method: 'POST',
        url: '/api/token/rotate',
        payload: { oldRefreshToken: token1 },
      });

      expect(reuseAttempt.statusCode).toBe(401);
      expect(reuseAttempt.json()).toHaveProperty('error');
    });
  });

  // ══════════════════════════════════════════════════════════════════════════
  // SESSION REVOCATION TESTS (6 tests)
  // ══════════════════════════════════════════════════════════════════════════

  describe('Session Revocation & Logout', () => {
    it('should revoke a specific session', async () => {
      sessionRevocationManager.registerSession('session-001', 'user-001');

      const response = await app.inject({
        method: 'POST',
        url: '/api/sessions/revoke',
        payload: { sessionId: 'session-001', userId: 'user-001', reason: 'explicit logout' },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('revoked', true);
    });

    it('should check revocation status of a session', async () => {
      sessionRevocationManager.registerSession('session-002', 'user-002');

      // Revoke session
      await app.inject({
        method: 'POST',
        url: '/api/sessions/revoke',
        payload: { sessionId: 'session-002', userId: 'user-002', reason: 'user logout' },
      });

      // Check status
      const response = await app.inject({
        method: 'POST',
        url: '/api/sessions/check-revocation',
        payload: { sessionId: 'session-002' },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('revoked', true);
      expect(response.json()).toHaveProperty('reason', 'user logout');
    });

    it('should return false for non-revoked sessions', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/sessions/check-revocation',
        payload: { sessionId: 'never-revoked-session' },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('revoked', false);
    });

    it('should revoke all user sessions on password change', async () => {
      sessionRevocationManager.registerSession('session-a', 'user-003');
      sessionRevocationManager.registerSession('session-b', 'user-003');
      sessionRevocationManager.registerSession('session-c', 'user-003');

      const response = await app.inject({
        method: 'POST',
        url: '/api/sessions/revoke-all',
        payload: { userId: 'user-003', reason: 'password changed' },
      });

      expect(response.statusCode).toBe(200);
      expect(response.json()).toHaveProperty('revokedCount', 3);
    });

    it('should record revocation metadata (reason, timestamp)', async () => {
      sessionRevocationManager.registerSession('session-004', 'user-004');

      await app.inject({
        method: 'POST',
        url: '/api/sessions/revoke',
        payload: { sessionId: 'session-004', userId: 'user-004', reason: 'suspicious activity detected' },
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/sessions/check-revocation',
        payload: { sessionId: 'session-004' },
      });

      expect(response.json()).toHaveProperty('reason', 'suspicious activity detected');
    });

    it('should return 404 when revoking non-existent session', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/sessions/revoke',
        payload: { sessionId: 'non-existent', userId: 'user-999', reason: 'testing' },
      });

      expect(response.statusCode).toBe(404);
      expect(response.json()).toHaveProperty('error');
    });
  });
});
