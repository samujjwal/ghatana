/**
 * Billing Route Tests
 * 
 * Tests for billing and subscription management endpoints.
 * Covers usage tracking, limits, upgrades, downgrades, and webhooks.
 * 
 * @doc.type test
 * @doc.purpose Test billing API routes
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */

import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest';
import Fastify, { FastifyInstance } from 'fastify';
import jwt from '@fastify/jwt';
import { registerBillingRoutes } from '../billing';
import { prisma } from '../../lib/prisma';
import { StripeBillingService } from '../../services/billing/stripe-service';

// Mock Stripe service
vi.mock('../../services/billing/stripe-service', () => ({
  StripeBillingService: {
    getSubscriptionInfo: vi.fn(),
    createCheckoutSession: vi.fn(),
    cancelSubscription: vi.fn(),
    verifyWebhookSignature: vi.fn(),
    handleWebhookEvent: vi.fn(),
  },
}));

describe('Billing Routes', () => {
  let app: FastifyInstance;
  const testEmail = `billing-test-${Date.now()}@example.com`;
  let testUserId: string;
  let authToken: string;

  beforeAll(async () => {
    // Create Fastify instance
    app = Fastify();
    await app.register(jwt, {
      secret: process.env.JWT_SECRET || 'test-secret-key',
    });

    // Register billing routes
    await registerBillingRoutes(app);
    await app.ready();

    // Create test user
    const user = await prisma.user.create({
      data: {
        email: testEmail,
        passwordHash: 'test-hash',
        displayName: 'Billing Test User',
      },
    });
    testUserId = user.id;

    // Create default sphere
    await prisma.sphere.create({
      data: {
        userId: testUserId,
        name: 'Personal',
        type: 'PERSONAL',
        visibility: 'PRIVATE',
        sphereAccess: {
          create: {
            userId: testUserId,
            role: 'OWNER',
            grantedBy: testUserId,
          },
        },
      },
    });

    // Generate auth token
    authToken = app.jwt.sign({
      userId: testUserId,
      email: testEmail,
    });
  });

  afterAll(async () => {
    // Cleanup
    await prisma.moment.deleteMany({ where: { userId: testUserId } });
    await prisma.sphereAccess.deleteMany({ where: { userId: testUserId } });
    await prisma.sphere.deleteMany({ where: { userId: testUserId } });
    await prisma.auditEvent.deleteMany({ where: { userId: testUserId } });
    await prisma.user.delete({ where: { id: testUserId } });
    await app.close();
  });

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('GET /api/billing/usage', () => {
    it('should return usage summary for free tier', async () => {
      vi.mocked(StripeBillingService.getSubscriptionInfo).mockResolvedValue({
        tier: 'free',
        status: 'active',
        customerId: null,
        subscriptionId: null,
        currentPeriodEnd: null,
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/billing/usage',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(true);
      expect(body.data).toHaveProperty('tier');
      expect(body.data.tier).toBe('free');
      expect(body.data).toHaveProperty('usage');
      expect(body.data.usage).toHaveProperty('momentsUsed');
      expect(body.data.usage).toHaveProperty('momentsLimit');
    });

    it('should return usage summary for pro tier', async () => {
      vi.mocked(StripeBillingService.getSubscriptionInfo).mockResolvedValue({
        tier: 'pro',
        status: 'active',
        customerId: 'cus_test123',
        subscriptionId: 'sub_test123',
        currentPeriodEnd: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/billing/usage',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.data.tier).toBe('pro');
    });

    it('should require authentication', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/billing/usage',
      });

      expect(response.statusCode).toBe(401);
    });

    it('should reject invalid token', async () => {
      const response = await app.inject({
        method: 'GET',
        url: '/api/billing/usage',
        headers: {
          authorization: 'Bearer invalid-token',
        },
      });

      expect(response.statusCode).toBe(401);
    });
  });

  describe('GET /api/billing/limits', () => {
    it('should return all usage limits with upgrade suggestions', async () => {
      vi.mocked(StripeBillingService.getSubscriptionInfo).mockResolvedValue({
        tier: 'free',
        status: 'active',
        customerId: null,
        subscriptionId: null,
        currentPeriodEnd: null,
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/billing/limits',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(true);
      expect(body.data.tier).toBe('free');
      expect(body.data.limits).toHaveProperty('moments');
      expect(body.data.limits).toHaveProperty('aiInsights');
      expect(body.data.limits).toHaveProperty('spheres');
      expect(body.data.limits).toHaveProperty('storage');
      
      // Check limit structure
      expect(body.data.limits.moments).toHaveProperty('used');
      expect(body.data.limits.moments).toHaveProperty('limit');
      expect(body.data.limits.moments).toHaveProperty('canCreate');
    });

    it('should show no upgrade needed for teams tier', async () => {
      vi.mocked(StripeBillingService.getSubscriptionInfo).mockResolvedValue({
        tier: 'teams',
        status: 'active',
        customerId: 'cus_test123',
        subscriptionId: 'sub_test123',
        currentPeriodEnd: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/billing/limits',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.data.tier).toBe('teams');
    });
  });

  describe('POST /api/billing/upgrade', () => {
    it('should create Stripe checkout session for pro tier', async () => {
      const mockCheckoutUrl = 'https://checkout.stripe.com/test123';
      vi.mocked(StripeBillingService.createCheckoutSession).mockResolvedValue(mockCheckoutUrl);

      const response = await app.inject({
        method: 'POST',
        url: '/api/billing/upgrade',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          targetTier: 'pro',
          billingCycle: 'monthly',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(true);
      expect(body.data.checkoutUrl).toBe(mockCheckoutUrl);
      expect(body.data.targetTier).toBe('pro');
      expect(body.data.billingCycle).toBe('monthly');

      // Verify Stripe was called correctly
      expect(StripeBillingService.createCheckoutSession).toHaveBeenCalledWith({
        userId: testUserId,
        userEmail: testEmail,
        targetTier: 'pro',
        billingCycle: 'monthly',
        successUrl: expect.stringContaining('/settings/billing?success=true'),
        cancelUrl: expect.stringContaining('/settings/billing?canceled=true'),
      });
    });

    it('should support annual billing cycle', async () => {
      vi.mocked(StripeBillingService.createCheckoutSession).mockResolvedValue('https://checkout.stripe.com/annual');

      const response = await app.inject({
        method: 'POST',
        url: '/api/billing/upgrade',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          targetTier: 'teams',
          billingCycle: 'annual',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.data.billingCycle).toBe('annual');
    });

    it('should reject invalid tier', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/billing/upgrade',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          targetTier: 'invalid',
          billingCycle: 'monthly',
        },
      });

      expect(response.statusCode).toBe(400);
    });

    it('should reject invalid billing cycle', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/billing/upgrade',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          targetTier: 'pro',
          billingCycle: 'invalid',
        },
      });

      expect(response.statusCode).toBe(400);
    });

    it('should handle Stripe errors gracefully', async () => {
      vi.mocked(StripeBillingService.createCheckoutSession).mockRejectedValue(
        new Error('Stripe API error')
      );

      const response = await app.inject({
        method: 'POST',
        url: '/api/billing/upgrade',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          targetTier: 'pro',
          billingCycle: 'monthly',
        },
      });

      expect(response.statusCode).toBe(500);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
      expect(body.error).toContain('Stripe API error');
    });
  });

  describe('POST /api/billing/downgrade', () => {
    it('should cancel subscription and schedule downgrade', async () => {
      vi.mocked(StripeBillingService.cancelSubscription).mockResolvedValue(undefined);

      const response = await app.inject({
        method: 'POST',
        url: '/api/billing/downgrade',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          targetTier: 'free',
          reason: 'Not using enough features',
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(true);
      expect(body.data.message).toContain('end of billing period');
      expect(body.data.targetTier).toBe('free');

      // Verify cancellation was called
      expect(StripeBillingService.cancelSubscription).toHaveBeenCalledWith(testUserId);
    });

    it('should accept downgrade without reason', async () => {
      vi.mocked(StripeBillingService.cancelSubscription).mockResolvedValue(undefined);

      const response = await app.inject({
        method: 'POST',
        url: '/api/billing/downgrade',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          targetTier: 'free',
        },
      });

      expect(response.statusCode).toBe(200);
    });

    it('should handle cancellation errors', async () => {
      vi.mocked(StripeBillingService.cancelSubscription).mockRejectedValue(
        new Error('Subscription not found')
      );

      const response = await app.inject({
        method: 'POST',
        url: '/api/billing/downgrade',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
        payload: {
          targetTier: 'free',
        },
      });

      expect(response.statusCode).toBe(500);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
    });
  });

  describe('GET /api/billing/subscription', () => {
    it('should return current subscription info', async () => {
      const mockSubscription = {
        tier: 'pro' as const,
        status: 'active',
        customerId: 'cus_test123',
        subscriptionId: 'sub_test123',
        currentPeriodEnd: new Date('2026-02-09'),
      };
      vi.mocked(StripeBillingService.getSubscriptionInfo).mockResolvedValue(mockSubscription);

      const response = await app.inject({
        method: 'GET',
        url: '/api/billing/subscription',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(true);
      expect(body.data.tier).toBe('pro');
      expect(body.data.status).toBe('active');
      expect(body.data.customerId).toBe('cus_test123');
      expect(body.data.subscriptionId).toBe('sub_test123');
    });

    it('should return free tier for users without subscription', async () => {
      vi.mocked(StripeBillingService.getSubscriptionInfo).mockResolvedValue({
        tier: 'free',
        status: 'active',
        customerId: null,
        subscriptionId: null,
        currentPeriodEnd: null,
      });

      const response = await app.inject({
        method: 'GET',
        url: '/api/billing/subscription',
        headers: {
          authorization: `Bearer ${authToken}`,
        },
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.data.tier).toBe('free');
      expect(body.data.customerId).toBeNull();
    });
  });

  describe('POST /api/billing/webhook', () => {
    it('should process valid webhook with signature', async () => {
      const mockEvent = {
        id: 'evt_test123',
        type: 'customer.subscription.created',
        data: { object: {} },
      };
      vi.mocked(StripeBillingService.verifyWebhookSignature).mockReturnValue(mockEvent as any);
      vi.mocked(StripeBillingService.handleWebhookEvent).mockResolvedValue(undefined);

      const response = await app.inject({
        method: 'POST',
        url: '/api/billing/webhook',
        headers: {
          'stripe-signature': 't=123,v1=abc',
        },
        payload: mockEvent,
      });

      expect(response.statusCode).toBe(200);
      const body = JSON.parse(response.body);
      expect(body.received).toBe(true);

      // Verify webhook was processed
      expect(StripeBillingService.verifyWebhookSignature).toHaveBeenCalled();
      expect(StripeBillingService.handleWebhookEvent).toHaveBeenCalledWith(mockEvent);
    });

    it('should reject webhook without signature', async () => {
      const response = await app.inject({
        method: 'POST',
        url: '/api/billing/webhook',
        payload: {
          id: 'evt_test123',
          type: 'customer.subscription.created',
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
      expect(body.error).toContain('signature');
    });

    it('should reject invalid signature', async () => {
      vi.mocked(StripeBillingService.verifyWebhookSignature).mockImplementation(() => {
        throw new Error('Invalid signature');
      });

      const response = await app.inject({
        method: 'POST',
        url: '/api/billing/webhook',
        headers: {
          'stripe-signature': 'invalid',
        },
        payload: {
          id: 'evt_test123',
          type: 'customer.subscription.created',
        },
      });

      expect(response.statusCode).toBe(400);
      const body = JSON.parse(response.body);
      expect(body.success).toBe(false);
    });

    it('should handle webhook processing errors', async () => {
      const mockEvent = {
        id: 'evt_test123',
        type: 'customer.subscription.created',
      };
      vi.mocked(StripeBillingService.verifyWebhookSignature).mockReturnValue(mockEvent as any);
      vi.mocked(StripeBillingService.handleWebhookEvent).mockRejectedValue(
        new Error('Processing failed')
      );

      const response = await app.inject({
        method: 'POST',
        url: '/api/billing/webhook',
        headers: {
          'stripe-signature': 't=123,v1=abc',
        },
        payload: mockEvent,
      });

      expect(response.statusCode).toBe(400);
    });
  });
});
