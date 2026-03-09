/**
 * Billing and usage API routes
 * 
 * Endpoints:
 * - GET /api/billing/usage - Get current usage summary
 * - GET /api/billing/limits - Check all limits
 * - POST /api/billing/upgrade - Initiate Stripe checkout
 * - POST /api/billing/downgrade - Request tier downgrade
 * - GET /api/billing/subscription - Get subscription status
 * - POST /api/billing/webhook - Stripe webhook handler
 */

import type { FastifyInstance } from 'fastify';
import { z } from 'zod';
import {
  getUsageSummary,
  checkMomentLimit,
  checkAIInsightLimit,
  checkMemoryExpansionLimit,
  checkSphereLimit,
  checkStorageLimit,
  type TierName,
} from '../services/billing/usage-limits.js';
import { StripeBillingService } from '../services/billing/stripe-service.js';
import { requireAuth } from '../lib/auth.js';

/**
 * Register billing routes
 */
export async function registerBillingRoutes(fastify: FastifyInstance) {
  /**
   * GET /api/billing/usage
   * Get comprehensive usage summary for current user
   */
  fastify.get(
    '/api/billing/usage',
    { preHandler: [requireAuth] },
    async (request, reply) => {
      const userId = (request.user as any).userId;
      
      // Get user's current subscription tier
      const subscriptionInfo = await StripeBillingService.getSubscriptionInfo(userId);
      const tier = subscriptionInfo.tier;

      const summary = await getUsageSummary(userId, tier);

      return reply.send({
        success: true,
        data: summary,
      });
    }
  );

  /**
   * GET /api/billing/limits
   * Check all usage limits with upgrade prompts
   */
  fastify.get(
    '/api/billing/limits',
    { preHandler: [requireAuth] },
    async (request, reply) => {
      const userId = (request.user as any).userId;
      const subscriptionInfo = await StripeBillingService.getSubscriptionInfo(userId);
      const tier = subscriptionInfo.tier;

      const [moments, aiInsights, memoryExpansions, spheres, storage] =
        await Promise.all([
          checkMomentLimit(userId, tier),
          checkAIInsightLimit(userId, tier),
          checkMemoryExpansionLimit(userId, tier),
          checkSphereLimit(userId, tier),
          checkStorageLimit(userId, tier),
        ]);

      return reply.send({
        success: true,
        data: {
          tier,
          limits: {
            moments,
            aiInsights,
            memoryExpansions,
            spheres,
            storage,
          },
        },
      });
    }
  );

  /**
   * POST /api/billing/upgrade
   * Initiate Stripe checkout session for tier upgrade
   * 
   * Phase 1: Returns upgrade URL placeholder
   * Phase 2: Creates actual Stripe checkout session
   */
  const upgradeSchema = z.object({
    targetTier: z.enum(['pro', 'teams']),
    billingCycle: z.enum(['monthly', 'annual']).default('monthly'),
  });

  fastify.post(
    '/api/billing/upgrade',
    { preHandler: [requireAuth] },
    async (request, reply) => {
      const userId = (request.user as any).userId;
      const userEmail = (request.user as any).email;
      const { targetTier, billingCycle } = upgradeSchema.parse(request.body);

      try {
        const checkoutUrl = await StripeBillingService.createCheckoutSession({
          userId,
          userEmail,
          targetTier,
          billingCycle,
          successUrl: `${process.env.APP_URL || 'http://localhost:2900'}/settings/billing?success=true`,
          cancelUrl: `${process.env.APP_URL || 'http://localhost:2900'}/settings/billing?canceled=true`,
        });

        return reply.send({
          success: true,
          data: {
            checkoutUrl,
            targetTier,
            billingCycle,
          },
        });
      } catch (error) {
        fastify.log.error('Stripe checkout error:', error);
        return reply.status(500).send({
          success: false,
          error: error instanceof Error ? error.message : 'Failed to create checkout session',
        });
      }
    }
  );

  /**
   * POST /api/billing/downgrade
   * Request tier downgrade (takes effect at end of billing period)
   */
  const downgradeSchema = z.object({
    targetTier: z.enum(['free']),
    reason: z.string().optional(),
  });

  fastify.post(
    '/api/billing/downgrade',
    { preHandler: [requireAuth] },
    async (request, reply) => {
      const userId = (request.user as any).userId;
      const { targetTier, reason } = downgradeSchema.parse(request.body);

      try {
        await StripeBillingService.cancelSubscription(userId);

        // Log for analytics
        fastify.log.info('Downgrade requested', {
          userId,
          targetTier,
          reason,
          timestamp: new Date().toISOString(),
        });

        return reply.send({
          success: true,
          data: {
            message: 'Downgrade will take effect at end of billing period',
            targetTier,
          },
        });
      } catch (error) {
        fastify.log.error('Subscription cancellation error:', error);
        return reply.status(500).send({
          success: false,
          error: error instanceof Error ? error.message : 'Failed to cancel subscription',
        });
      }
    }
  );

  /**
   * GET /api/billing/subscription
   * Get current subscription status
   */
  fastify.get(
    '/api/billing/subscription',
    { preHandler: [requireAuth] },
    async (request, reply) => {
      const userId = (request.user as any).userId;

      try {
        const subscriptionInfo = await StripeBillingService.getSubscriptionInfo(userId);
        
        return reply.send({
          success: true,
          data: subscriptionInfo,
        });
      } catch (error) {
        fastify.log.error('Subscription fetch error:', error);
        return reply.status(500).send({
          success: false,
          error: error instanceof Error ? error.message : 'Failed to fetch subscription',
        });
      }
    }
  );

  /**
   * POST /api/billing/webhook
   * Handle Stripe webhook events (subscription created, updated, canceled)
   * 
   * Phase 1: Not implemented
   * Phase 2: Full webhook handling
   */
  fastify.post(
    '/api/billing/webhook',
    { 
      config: { 
        rawBody: true // Preserve raw body for signature verification
      } 
    },
    async (request, reply) => {
      try {
        const signature = request.headers['stripe-signature'];
        
        if (!signature) {
          return reply.status(400).send({
            success: false,
            error: 'Missing stripe-signature header',
          });
        }

        // Verify and construct event
        const event = StripeBillingService.verifyWebhookSignature(
          request.rawBody || request.body,
          signature
        );

        // Handle event
        await StripeBillingService.handleWebhookEvent(event);

        return reply.send({ received: true });
      } catch (error) {
        fastify.log.error('Webhook error:', error);
        return reply.status(400).send({
          success: false,
          error: error instanceof Error ? error.message : 'Webhook processing failed',
        });
      }
    }
  );

  /**
   * GET /api/billing/invoice-history
   * Get past invoices for user
   */
  fastify.get(
    '/api/billing/invoice-history',
    { preHandler: [requireAuth] },
    async (request, reply) => {
      const userId = (request.user as any).userId;

      try {
        // Get user's Stripe customer ID
        const subscriptionInfo = await StripeBillingService.getSubscriptionInfo(userId);
        
        if (!subscriptionInfo.stripeCustomerId) {
          return reply.send({
            success: true,
            data: {
              invoices: [],
              message: 'No billing history available',
            },
          });
        }

        // Fetch invoices from Stripe
        const stripe = (await import('stripe')).default;
        const stripeClient = new stripe(process.env.STRIPE_SECRET_KEY || '', {
          apiVersion: '2024-12-18.acacia',
        });
        
        const invoices = await stripeClient.invoices.list({
          customer: subscriptionInfo.stripeCustomerId,
          limit: 12,
        });

        return reply.send({
          success: true,
          data: {
            invoices: invoices.data.map(invoice => ({
              id: invoice.id,
              date: new Date(invoice.created * 1000).toISOString(),
              amount: invoice.amount_paid / 100,
              currency: invoice.currency,
              status: invoice.status,
              invoicePdf: invoice.invoice_pdf,
              hostedInvoiceUrl: invoice.hosted_invoice_url,
              periodStart: invoice.period_start ? new Date(invoice.period_start * 1000).toISOString() : null,
              periodEnd: invoice.period_end ? new Date(invoice.period_end * 1000).toISOString() : null,
            })),
          },
        });
      } catch (error) {
        fastify.log.error('Invoice history fetch error:', error);
        return reply.status(500).send({
          success: false,
          error: error instanceof Error ? error.message : 'Failed to fetch invoice history',
        });
      }
    }
  );

  /**
   * GET /api/billing/pricing
   * Get current pricing tiers (public endpoint)
   */
  fastify.get('/api/billing/pricing', async (request, reply) => {
    return reply.send({
      success: true,
      data: {
        tiers: [
          {
            name: 'free',
            displayName: 'Free',
            price: 0,
            currency: 'USD',
            billingCycle: null,
            features: [
              '100 moments/month',
              '1 GB storage',
              '10 hours transcription/month',
              '5 AI insights/month',
              '2 memory expansions/month',
              '3 spheres',
              'Unlimited semantic search',
              'Unlimited moment linking',
            ],
          },
          {
            name: 'pro',
            displayName: 'Pro',
            price: 9,
            currency: 'USD',
            billingCycle: 'monthly',
            features: [
              'Unlimited moments',
              '50 GB storage',
              '50 hours transcription/month',
              '50 AI insights/month',
              '20 memory expansions/month',
              '20 spheres',
              '3 collaborators',
              'Priority email support (24h)',
            ],
          },
          {
            name: 'teams',
            displayName: 'Teams',
            price: 29,
            currency: 'USD',
            billingCycle: 'monthly',
            priceUnit: 'per user',
            minimumUsers: 5,
            features: [
              'Unlimited everything',
              '500 GB storage per user',
              '200 hours transcription per user/month',
              'Full API access',
              'SSO/SAML',
              'Advanced collaboration',
              '3-year audit logs',
              'Dedicated support (2h response)',
            ],
          },
        ],
      },
    });
  });
}
