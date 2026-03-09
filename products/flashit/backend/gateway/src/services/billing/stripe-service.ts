/**
 * Stripe Billing Service
 * 
 * Handles Stripe subscription management, checkout sessions, and webhooks
 * 
 * @doc.type service
 * @doc.purpose Stripe integration for subscription management
 * @doc.layer application
 * @doc.pattern ServiceFacade
 */

import Stripe from 'stripe';
import { prisma } from '../../lib/prisma.js';
import type { TierName } from './usage-limits.js';

// Lazy initialization
let stripe: Stripe | null = null;

function getStripe(): Stripe {
    if (!stripe) {
        const apiKey = process.env.STRIPE_SECRET_KEY;
        if (!apiKey) {
            throw new Error('STRIPE_SECRET_KEY environment variable is required');
        }
        stripe = new Stripe(apiKey, {
            apiVersion: '2025-12-15.clover',
        });
    }
    return stripe;
}

// Stripe Price IDs (set via environment or config)
export const STRIPE_PRICES = {
    pro_monthly: process.env.STRIPE_PRICE_PRO_MONTHLY || '',
    pro_annual: process.env.STRIPE_PRICE_PRO_ANNUAL || '',
    teams_monthly: process.env.STRIPE_PRICE_TEAMS_MONTHLY || '',
    teams_annual: process.env.STRIPE_PRICE_TEAMS_ANNUAL || '',
};

export interface CreateCheckoutSessionParams {
    userId: string;
    userEmail: string;
    targetTier: 'pro' | 'teams';
    billingCycle: 'monthly' | 'annual';
    successUrl: string;
    cancelUrl: string;
}

export interface SubscriptionInfo {
    tier: TierName;
    status: 'active' | 'canceled' | 'past_due' | 'trialing' | 'incomplete';
    billingCycle: 'monthly' | 'annual' | null;
    currentPeriodEnd: Date | null;
    cancelAtPeriodEnd: boolean;
    stripeCustomerId: string | null;
    stripeSubscriptionId: string | null;
}

export class StripeBillingService {
    /**
     * Create a Stripe checkout session for subscription upgrade
     */
    static async createCheckoutSession(params: CreateCheckoutSessionParams): Promise<string> {
        const stripe = getStripe();

        // Determine price ID
        const priceKey = `${params.targetTier}_${params.billingCycle}` as keyof typeof STRIPE_PRICES;
        const priceId = STRIPE_PRICES[priceKey];

        if (!priceId) {
            throw new Error(`Stripe price not configured for ${priceKey}`);
        }

        // Get or create Stripe customer
        const customer = await this.getOrCreateCustomer(params.userId);

        // Create checkout session
        const session = await stripe.checkout.sessions.create({
            customer: customer.id,
            mode: 'subscription',
            line_items: [{
                price: priceId,
                quantity: 1,
            }],
            success_url: params.successUrl,
            cancel_url: params.cancelUrl,
            allow_promotion_codes: true,
            billing_address_collection: 'auto',
            metadata: {
                userId: params.userId,
                tier: params.targetTier,
                billingCycle: params.billingCycle,
            },
        });

        if (!session.url) {
            throw new Error('Failed to create checkout session URL');
        }

        return session.url;
    }

    /**
     * Get or create Stripe customer for user
     */
    static async getOrCreateCustomer(userId: string): Promise<Stripe.Customer> {
        const stripe = getStripe();

        // Check if user already has a Stripe customer ID
        const user = await prisma.user.findUnique({
            where: { id: userId },
            select: { 
                id: true, 
                email: true, 
                displayName: true,
                stripeCustomerId: true,
            },
        });

        if (!user) {
            throw new Error('User not found');
        }

        // Return existing customer if we have the ID
        if (user.stripeCustomerId) {
            const customer = await stripe.customers.retrieve(user.stripeCustomerId);
            if (!customer.deleted) {
                return customer as Stripe.Customer;
            }
        }

        // Search by email as fallback
        const existingCustomers = await stripe.customers.list({
            email: user.email,
            limit: 1,
        });

        if (existingCustomers.data.length > 0) {
            const customer = existingCustomers.data[0];
            // Backfill ID if missing
            await prisma.user.update({
                where: { id: userId },
                data: { stripeCustomerId: customer.id },
            });
            return customer;
        }

        // Create new customer
        const customer = await stripe.customers.create({
            email: user.email,
            name: user.displayName || undefined,
            metadata: {
                userId: user.id,
            },
        });

        // Save customer.id to user
        await prisma.user.update({
            where: { id: userId },
            data: { stripeCustomerId: customer.id },
        });

        return customer;
    }

    /**
     * Get subscription info for user
     */
    static async getSubscriptionInfo(userId: string): Promise<SubscriptionInfo> {
        const stripe = getStripe();
        const db = prisma;

        const user = await db.user.findUnique({
            where: { id: userId },
            select: { 
                id: true, 
                email: true,
                stripeCustomerId: true,
                stripeSubscriptionId: true,
                subscriptionTier: true,
                subscriptionStatus: true,
            },
        });

        if (!user) {
            throw new Error('User not found');
        }

        // If we have subscription data in DB and it's active/recent, return it
        // This reduces Stripe API calls
        if (user.stripeSubscriptionId && user.subscriptionStatus === 'active') {
             // We can trust DB or implement a sync check if needed
             // For now, let's double check with Stripe if we want to be 100% sure, 
             // but typically you trust your webhooks. 
             // Let's rely on DB for Tier, but if ID exists, we can return fast.
        }

        // Fallback: If no Stripe ID, they are free tier
        // If we have ID but verify with Stripe:
        let stripeCustomerId = user.stripeCustomerId;

        // Fallback search by email if no ID (legacy/migration)
        if (!stripeCustomerId) {
             const customers = await stripe.customers.list({
                email: user.email,
                limit: 1,
             });
             if (customers.data.length > 0) {
                stripeCustomerId = customers.data[0].id;
                // Async update DB
                try {
                    await db.user.update({ where: {id: userId}, data: { stripeCustomerId }});
                } catch {}
             }
        }

        if (!stripeCustomerId) {
            return {
                tier: 'free',
                status: 'active',
                billingCycle: null,
                currentPeriodEnd: null,
                cancelAtPeriodEnd: false,
                stripeCustomerId: null,
                stripeSubscriptionId: null,
            };
        }

        const subscriptions = await stripe.subscriptions.list({
            customer: stripeCustomerId,
            status: 'all',
            limit: 1,
        });

        if (subscriptions.data.length === 0) {
            return {
                tier: 'free',
                status: 'active',
                billingCycle: null,
                currentPeriodEnd: null,
                cancelAtPeriodEnd: false,
                stripeCustomerId: stripeCustomerId,
                stripeSubscriptionId: null,
            };
        }

        const subscription = subscriptions.data[0];
        const priceId = subscription.items.data[0]?.price.id;
        
        // Map price ID to tier
        const tier = this.mapPriceIdToTier(priceId);
        const billingCycle = subscription.items.data[0]?.price.recurring?.interval === 'year' ? 'annual' : 'monthly';

        // Type assertion for Stripe properties that exist but TypeScript doesn't recognize
        type StripeSubscriptionFull = Stripe.Subscription & {
            current_period_end: number;
            cancel_at_period_end: boolean;
        };

        const sub = subscription as StripeSubscriptionFull;

        return {
            tier,
            status: subscription.status as SubscriptionInfo['status'],
            billingCycle,
            currentPeriodEnd: new Date(sub.current_period_end * 1000),
            cancelAtPeriodEnd: sub.cancel_at_period_end,
            stripeCustomerId: stripeCustomerId,
            stripeSubscriptionId: subscription.id,
        };
    }

    /**
     * Cancel subscription (at end of period)
     */
    static async cancelSubscription(userId: string): Promise<void> {
        const stripe = getStripe();
        const subscriptionInfo = await this.getSubscriptionInfo(userId);

        if (!subscriptionInfo.stripeSubscriptionId) {
            throw new Error('No active subscription to cancel');
        }

        await stripe.subscriptions.update(subscriptionInfo.stripeSubscriptionId, {
            cancel_at_period_end: true,
        });
    }

    /**
     * Reactivate canceled subscription
     */
    static async reactivateSubscription(userId: string): Promise<void> {
        const stripe = getStripe();
        const subscriptionInfo = await this.getSubscriptionInfo(userId);

        if (!subscriptionInfo.stripeSubscriptionId) {
            throw new Error('No subscription to reactivate');
        }

        await stripe.subscriptions.update(subscriptionInfo.stripeSubscriptionId, {
            cancel_at_period_end: false,
        });
    }

    /**
     * Handle Stripe webhook event
     */
    static async handleWebhookEvent(event: Stripe.Event): Promise<void> {
        const db = prisma;

        switch (event.type) {
            case 'customer.subscription.created':
            case 'customer.subscription.updated': {
                const subscription = event.data.object as Stripe.Subscription;
                const customerId = subscription.customer as string;
                const priceId = subscription.items.data[0]?.price.id;
                const tier = this.mapPriceIdToTier(priceId);

                // Find user by Stripe customer ID
                const customer = await getStripe().customers.retrieve(customerId);
                if (customer.deleted) break;

                // Type assertion for Stripe properties
                type StripeSubscriptionFull = Stripe.Subscription & {
                    current_period_end: number;
                };
                const sub = subscription as StripeSubscriptionFull;

                // Update user subscription in database
                await db.user.update({
                    where: { stripeCustomerId: customerId },
                    data: {
                        stripeSubscriptionId: subscription.id,
                        subscriptionTier: tier,
                        subscriptionStatus: subscription.status,
                        subscriptionEndsAt: new Date(sub.current_period_end * 1000),
                        // Note: cancellation status is inferred from subscriptionEndsAt compared to now 
                        // or stripe status itself, sticking to available fields
                    },
                });

                console.log(`Subscription ${subscription.id} updated for customer ${customerId} to tier ${tier}`);
                break;
            }

            case 'customer.subscription.deleted': {
                const subscription = event.data.object as Stripe.Subscription;
                const customerId = subscription.customer as string;

                // Downgrade user to free tier
                await db.user.update({
                    where: { stripeCustomerId: customerId },
                    data: {
                        stripeSubscriptionId: null,
                        subscriptionTier: 'free',
                        subscriptionStatus: 'canceled',
                        subscriptionEndsAt: null,
                    },
                });

                console.log(`Subscription ${subscription.id} canceled for customer ${customerId}`);
                break;
            }

            case 'invoice.payment_failed': {
                const invoice = event.data.object as Stripe.Invoice;
                const customerId = invoice.customer as string;

                // Update subscription status to past_due
                await db.user.update({
                    where: { stripeCustomerId: customerId },
                    data: {
                        subscriptionStatus: 'past_due',
                    },
                });

                // Get user for email notification
                const user = await db.user.findUnique({
                    where: { stripeCustomerId: customerId },
                    select: { email: true, displayName: true },
                });

                if (user) {
                    // Send payment failure notification
                    try {
                        const { sendEmail } = await import('../../lib/email.js');
                        await sendEmail({
                            to: user.email,
                            subject: 'Payment Failed - Action Required',
                            body: `Hi ${user.displayName || 'there'},\n\nYour recent payment for Flashit subscription failed. Please update your payment method to avoid service interruption.\n\nInvoice ID: ${invoice.id}\nAmount: $${(invoice.amount_due / 100).toFixed(2)}\n\nUpdate your payment method in your account settings.`,
                            html: `<p>Hi ${user.displayName || 'there'},</p><p>Your recent payment for Flashit subscription failed. Please update your payment method to avoid service interruption.</p><p><strong>Invoice ID:</strong> ${invoice.id}<br><strong>Amount:</strong> $${(invoice.amount_due / 100).toFixed(2)}</p><p><a href="${process.env.APP_URL || 'http://localhost:2900'}/settings/billing">Update Payment Method</a></p>`,
                        });
                    } catch (emailError) {
                        console.error('Failed to send payment failure email:', emailError);
                    }
                }

                console.error(`Payment failed for customer ${customerId}, invoice ${invoice.id}`);
                break;
            }

            case 'invoice.payment_succeeded': {
                const invoice = event.data.object as Stripe.Invoice;
                console.log(`Payment succeeded for invoice ${invoice.id}`);
                break;
            }
        }
    }

    /**
     * Map Stripe price ID to tier
     */
    private static mapPriceIdToTier(priceId?: string): TierName {
        if (!priceId) return 'free';

        if (priceId === STRIPE_PRICES.pro_monthly || priceId === STRIPE_PRICES.pro_annual) {
            return 'pro';
        }

        if (priceId === STRIPE_PRICES.teams_monthly || priceId === STRIPE_PRICES.teams_annual) {
            return 'teams';
        }

        return 'free';
    }

    /**
     * Verify webhook signature
     */
    static verifyWebhookSignature(payload: string | Buffer, signature: string): Stripe.Event {
        const stripe = getStripe();
        const webhookSecret = process.env.STRIPE_WEBHOOK_SECRET;

        if (!webhookSecret) {
            throw new Error('STRIPE_WEBHOOK_SECRET environment variable is required');
        }

        return stripe.webhooks.constructEvent(payload, signature, webhookSecret);
    }
}
