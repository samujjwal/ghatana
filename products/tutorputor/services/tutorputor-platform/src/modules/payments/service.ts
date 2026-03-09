import Stripe from 'stripe';
import { PrismaClient } from '@ghatana/tutorputor-db';
import {
    CreateSubscriptionDto,
    PlatformSubscription,
    SubscriptionPlan,
    SubscriptionStatus,
    SubscriptionInterval
} from './types';

const STRIPE_CUSTOMER_EVENT = 'STRIPE_CUSTOMER_MAPPED';
const SUBSCRIPTION_EVENT = 'SUBSCRIPTION_STATE_CHANGE';

// Quick mapping for plans to price IDs (mocked or env based)
const PLAN_PRICES: Record<SubscriptionPlan, { [key in SubscriptionInterval]?: string }> = {
    [SubscriptionPlan.FREE]: { [SubscriptionInterval.MONTHLY]: 'price_free_monthly' },
    [SubscriptionPlan.STARTER]: {
        [SubscriptionInterval.MONTHLY]: process.env.STRIPE_PRICE_STARTER_MONTHLY || 'price_starter_monthly',
        [SubscriptionInterval.YEARLY]: process.env.STRIPE_PRICE_STARTER_YEARLY || 'price_starter_yearly'
    },
    [SubscriptionPlan.PROFESSIONAL]: {
        [SubscriptionInterval.MONTHLY]: process.env.STRIPE_PRICE_PRO_MONTHLY || 'price_pro_monthly',
        [SubscriptionInterval.YEARLY]: process.env.STRIPE_PRICE_PRO_YEARLY || 'price_pro_yearly'
    },
    [SubscriptionPlan.INSTITUTION]: {},
    [SubscriptionPlan.ENTERPRISE]: {}
};

export class PaymentService {
    private stripe: Stripe;

    constructor(
        private prisma: PrismaClient,
        private stripeSecretKey: string
    ) {
        this.stripe = new Stripe(stripeSecretKey, {
            apiVersion: '2024-06-20' as any,
        });
    }

    /**
     * Resolve platform user to Stripe Customer ID.
     * Persists the mapping in LearningEvents if created.
     */
    async getOrCreateCustomer(tenantId: string, userId: string, email: string): Promise<string> {
        // 1. Check if we already have a customer mapped for this user
        const mappingEvent = await this.prisma.learningEvent.findFirst({
            where: {
                tenantId,
                userId,
                eventType: STRIPE_CUSTOMER_EVENT,
            },
            orderBy: { timestamp: 'desc' },
        });

        if (mappingEvent && mappingEvent.payload && (mappingEvent.payload as any).customerId) {
            return (mappingEvent.payload as any).customerId;
        }

        // 2. Create new customer in Stripe
        const customer = await this.stripe.customers.create({
            email,
            metadata: {
                userId,
                tenantId,
            },
        });

        // 3. Save mapping
        await this.prisma.learningEvent.create({
            data: {
                tenantId,
                userId,
                eventType: STRIPE_CUSTOMER_EVENT,
                payload: { customerId: customer.id },
                timestamp: new Date(),
            }
        });

        return customer.id;
    }

    async createSubscription(tenantId: string, userId: string, email: string, dto: CreateSubscriptionDto): Promise<PlatformSubscription> {
        const customerId = await this.getOrCreateCustomer(tenantId, userId, email);

        // Determine price ID
        const interval = dto.interval || SubscriptionInterval.MONTHLY;
        const priceId = PLAN_PRICES[dto.plan][interval];

        if (!priceId) {
            throw new Error(`Price not configured for plan ${dto.plan} (${interval})`);
        }

        // Create subscription in Stripe
        const subscription = await this.stripe.subscriptions.create({
            customer: customerId,
            items: [{ price: priceId }],
            payment_behavior: 'default_incomplete',
            payment_settings: { save_default_payment_method: 'on_subscription' },
            expand: ['latest_invoice.payment_intent'],
            metadata: {
                tenantId,
                userId,
                plan: dto.plan
            }
        });

        // Map status
        const result: PlatformSubscription = {
            id: subscription.id,
            userId,
            stripeCustomerId: customerId,
            plan: dto.plan,
            status: subscription.status as SubscriptionStatus,
            currentPeriodEnd: new Date((subscription as any).current_period_end * 1000),
            cancelAtPeriodEnd: (subscription as any).cancel_at_period_end,
        };

        // Save state event
        await this.prisma.learningEvent.create({
            data: {
                tenantId,
                userId,
                eventType: SUBSCRIPTION_EVENT,
                payload: result as any, // Storing full state
                timestamp: new Date(),
            }
        });

        return result;
    }

    async getSubscription(tenantId: string, userId: string): Promise<PlatformSubscription | null> {
        // Find latest subscription state
        const event = await this.prisma.learningEvent.findFirst({
            where: {
                tenantId,
                userId,
                eventType: SUBSCRIPTION_EVENT,
            },
            orderBy: { timestamp: 'desc' },
        });

        if (!event || !event.payload) {
            return null;
        }

        // Ideally we verify with Stripe if it's stale, but for speed we return the cached state
        // Or we fetch fresh data if we have the ID.
        const cached = event.payload as unknown as PlatformSubscription;

        try {
            const fresh = await this.stripe.subscriptions.retrieve(cached.id);
            const refreshed: PlatformSubscription = {
                ...cached,
                status: fresh.status as SubscriptionStatus,
                currentPeriodEnd: new Date((fresh as any).current_period_end * 1000),
                cancelAtPeriodEnd: (fresh as any).cancel_at_period_end
            };
            return refreshed;
        } catch (e) {
            // If stripe fails (deleted?), return cached or null
            return cached;
        }
    }

    async createPortalSession(tenantId: string, userId: string, returnUrl: string): Promise<string> {
        // We need the customer ID to create a portal session
        // We can look it up from the subscription event or the customer mapping event
        const mappingEvent = await this.prisma.learningEvent.findFirst({
            where: { tenantId, userId, eventType: STRIPE_CUSTOMER_EVENT },
            orderBy: { timestamp: 'desc' }
        });

        if (!mappingEvent || !(mappingEvent.payload as any).customerId) {
            throw new Error("No customer linked to user");
        }

        const session = await this.stripe.billingPortal.sessions.create({
            customer: (mappingEvent.payload as any).customerId,
            return_url: returnUrl,
        });

        return session.url;
    }
}
