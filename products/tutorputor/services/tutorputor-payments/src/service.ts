/**
 * @doc.type service
 * @doc.purpose Subscription and payment management with Stripe integration
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@prisma/client';
import Stripe from 'stripe';
import type {
  TenantId,
  SubscriptionId,
  PaymentMethodId,
  InvoiceId,
  TransactionId,
  Subscription,
  SubscriptionPlan,
  SubscriptionStatus,
  SubscriptionTier,
  BillingInterval,
  PaymentMethod,
  PaymentMethodType,
  BillingAddress,
  Invoice,
  InvoiceLineItem,
  PaymentTransaction,
  SubscriptionUsage,
  SubscriptionChangePreview,
  PaymentWebhookEvent,
  PaginationArgs,
  PaginatedResult,
} from '@ghatana/tutorputor-contracts/v1/types';

import type {
  SubscriptionService,
  PaymentMethodService,
  InvoiceService,
  PaymentTransactionService,
  PaymentWebhookService,
} from '@ghatana/tutorputor-contracts/v1/services';

import type { PlanConfig, ProrationResult, UsageSnapshot, NotificationService } from './types';

// Default plan configurations
const DEFAULT_PLANS: PlanConfig[] = [
  {
    id: 'plan_free',
    name: 'Free',
    tier: 'free',
    description: 'Perfect for individual learners',
    features: [
      'Access to free modules',
      'Basic progress tracking',
      'Community support',
    ],
    limits: {
      maxUsers: 1,
      maxModules: 5,
      maxStorageGB: 1,
      maxClassrooms: 0,
      maxVrSessions: 5,
      analyticsRetentionDays: 7,
      supportLevel: 'community',
      customBranding: false,
      ssoEnabled: false,
      apiAccess: false,
    },
    pricing: {
      monthly: { amountCents: 0, stripePriceId: '' },
      quarterly: { amountCents: 0, stripePriceId: '' },
      annual: { amountCents: 0, stripePriceId: '' },
    },
    trialDays: 0,
    isActive: true,
  },
  {
    id: 'plan_starter',
    name: 'Starter',
    tier: 'starter',
    description: 'For small teams and tutors',
    features: [
      'Up to 25 users',
      'Unlimited modules',
      '10GB storage',
      'Email support',
      'Basic analytics',
    ],
    limits: {
      maxUsers: 25,
      maxModules: -1,
      maxStorageGB: 10,
      maxClassrooms: 5,
      maxVrSessions: 100,
      analyticsRetentionDays: 30,
      supportLevel: 'email',
      customBranding: false,
      ssoEnabled: false,
      apiAccess: false,
    },
    pricing: {
      monthly: { amountCents: 2900, stripePriceId: 'price_starter_monthly' },
      quarterly: { amountCents: 7900, stripePriceId: 'price_starter_quarterly' },
      annual: { amountCents: 29000, stripePriceId: 'price_starter_annual' },
    },
    trialDays: 14,
    isActive: true,
  },
  {
    id: 'plan_professional',
    name: 'Professional',
    tier: 'professional',
    description: 'For growing institutions',
    features: [
      'Up to 100 users',
      'Unlimited modules',
      '50GB storage',
      'Priority support',
      'Advanced analytics',
      'Custom branding',
      'API access',
    ],
    limits: {
      maxUsers: 100,
      maxModules: -1,
      maxStorageGB: 50,
      maxClassrooms: 25,
      maxVrSessions: 500,
      analyticsRetentionDays: 90,
      supportLevel: 'priority',
      customBranding: true,
      ssoEnabled: false,
      apiAccess: true,
    },
    pricing: {
      monthly: { amountCents: 9900, stripePriceId: 'price_pro_monthly' },
      quarterly: { amountCents: 26900, stripePriceId: 'price_pro_quarterly' },
      annual: { amountCents: 99000, stripePriceId: 'price_pro_annual' },
    },
    trialDays: 14,
    isActive: true,
  },
  {
    id: 'plan_institution',
    name: 'Institution',
    tier: 'institution',
    description: 'For schools and universities',
    features: [
      'Up to 500 users',
      'Unlimited everything',
      '200GB storage',
      'Dedicated support',
      'Full analytics',
      'Custom branding',
      'SSO integration',
      'API access',
      'LTI integration',
    ],
    limits: {
      maxUsers: 500,
      maxModules: -1,
      maxStorageGB: 200,
      maxClassrooms: -1,
      maxVrSessions: -1,
      analyticsRetentionDays: 365,
      supportLevel: 'dedicated',
      customBranding: true,
      ssoEnabled: true,
      apiAccess: true,
    },
    pricing: {
      monthly: { amountCents: 29900, stripePriceId: 'price_inst_monthly' },
      quarterly: { amountCents: 80900, stripePriceId: 'price_inst_quarterly' },
      annual: { amountCents: 299000, stripePriceId: 'price_inst_annual' },
    },
    trialDays: 30,
    isActive: true,
  },
  {
    id: 'plan_enterprise',
    name: 'Enterprise',
    tier: 'enterprise',
    description: 'Custom solutions for large organizations',
    features: [
      'Unlimited users',
      'Unlimited everything',
      'Custom storage',
      'Dedicated account manager',
      'Custom analytics',
      'White-label solution',
      'Advanced SSO',
      'Full API access',
      'On-premise options',
      'SLA guarantee',
    ],
    limits: {
      maxUsers: -1,
      maxModules: -1,
      maxStorageGB: -1,
      maxClassrooms: -1,
      maxVrSessions: -1,
      analyticsRetentionDays: -1,
      supportLevel: 'dedicated',
      customBranding: true,
      ssoEnabled: true,
      apiAccess: true,
    },
    pricing: {
      monthly: { amountCents: 0, stripePriceId: '' }, // Custom pricing
      quarterly: { amountCents: 0, stripePriceId: '' },
      annual: { amountCents: 0, stripePriceId: '' },
    },
    trialDays: 30,
    isActive: true,
  },
];

/**
 * Subscription service implementation.
 */
export class SubscriptionServiceImpl implements SubscriptionService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly stripe: Stripe,
  ) {}

  async listPlans(args: {
    tenantId: TenantId;
    includeInactive?: boolean;
  }): Promise<SubscriptionPlan[]> {
    const plans = args.includeInactive
      ? DEFAULT_PLANS
      : DEFAULT_PLANS.filter((p) => p.isActive);

    return plans.map((p) => ({
      id: p.id,
      name: p.name,
      tier: p.tier,
      description: p.description,
      features: p.features,
      limits: p.limits,
      pricing: [
        { interval: 'monthly' as const, amountCents: p.pricing.monthly.amountCents, currency: 'usd', stripePriceId: p.pricing.monthly.stripePriceId },
        { interval: 'quarterly' as const, amountCents: p.pricing.quarterly.amountCents, currency: 'usd', stripePriceId: p.pricing.quarterly.stripePriceId },
        { interval: 'annual' as const, amountCents: p.pricing.annual.amountCents, currency: 'usd', stripePriceId: p.pricing.annual.stripePriceId },
      ],
      isActive: p.isActive,
      trialDays: p.trialDays,
    }));
  }

  async getPlan(args: { planId: string }): Promise<SubscriptionPlan | null> {
    const plan = DEFAULT_PLANS.find((p) => p.id === args.planId);
    if (!plan) return null;

    return {
      id: plan.id,
      name: plan.name,
      tier: plan.tier,
      description: plan.description,
      features: plan.features,
      limits: plan.limits,
      pricing: [
        { interval: 'monthly', amountCents: plan.pricing.monthly.amountCents, currency: 'usd', stripePriceId: plan.pricing.monthly.stripePriceId },
        { interval: 'quarterly', amountCents: plan.pricing.quarterly.amountCents, currency: 'usd', stripePriceId: plan.pricing.quarterly.stripePriceId },
        { interval: 'annual', amountCents: plan.pricing.annual.amountCents, currency: 'usd', stripePriceId: plan.pricing.annual.stripePriceId },
      ],
      isActive: plan.isActive,
      trialDays: plan.trialDays,
    };
  }

  async getCurrentSubscription(args: {
    tenantId: TenantId;
  }): Promise<Subscription | null> {
    const sub = await this.prisma.subscription.findFirst({
      where: {
        tenantId: args.tenantId,
        status: { notIn: ['canceled', 'incomplete_expired'] },
      },
      orderBy: { createdAt: 'desc' },
    });

    if (!sub) return null;

    return this.mapToSubscription(sub);
  }

  async createSubscription(args: {
    tenantId: TenantId;
    planId: string;
    billingInterval: BillingInterval;
    paymentMethodId?: PaymentMethodId;
    trialDays?: number;
  }): Promise<Subscription> {
    const plan = DEFAULT_PLANS.find((p) => p.id === args.planId);
    if (!plan) {
      throw new Error(`Plan not found: ${args.planId}`);
    }

    // Get or create Stripe customer
    let customer = await this.prisma.stripeCustomer.findUnique({
      where: { tenantId: args.tenantId },
    });

    if (!customer) {
      const tenant = await this.prisma.tenant.findUnique({
        where: { id: args.tenantId },
      });

      const stripeCustomer = await this.stripe.customers.create({
        email: tenant?.adminEmail ?? undefined,
        name: tenant?.name ?? undefined,
        metadata: { tenantId: args.tenantId },
      });

      customer = await this.prisma.stripeCustomer.create({
        data: {
          tenantId: args.tenantId,
          stripeCustomerId: stripeCustomer.id,
          email: tenant?.adminEmail ?? '',
        },
      });
    }

    // Get price ID for interval
    const priceId = plan.pricing[args.billingInterval].stripePriceId;
    if (!priceId && plan.tier !== 'free') {
      throw new Error(`No price configured for ${plan.name} ${args.billingInterval}`);
    }

    // Handle free tier
    if (plan.tier === 'free') {
      const sub = await this.prisma.subscription.create({
        data: {
          tenantId: args.tenantId,
          planId: plan.id,
          tier: plan.tier,
          status: 'active',
          billingInterval: args.billingInterval,
          currentPeriodStart: new Date(),
          currentPeriodEnd: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000),
        },
      });
      return this.mapToSubscription(sub);
    }

    // Attach payment method if provided
    if (args.paymentMethodId) {
      const pm = await this.prisma.paymentMethod.findUnique({
        where: { id: args.paymentMethodId },
      });
      if (pm?.stripePaymentMethodId) {
        await this.stripe.paymentMethods.attach(pm.stripePaymentMethodId, {
          customer: customer.stripeCustomerId,
        });
        await this.stripe.customers.update(customer.stripeCustomerId, {
          invoice_settings: { default_payment_method: pm.stripePaymentMethodId },
        });
      }
    }

    // Create Stripe subscription
    const trialDays = args.trialDays ?? plan.trialDays;
    const stripeSubscription = await this.stripe.subscriptions.create({
      customer: customer.stripeCustomerId,
      items: [{ price: priceId }],
      trial_period_days: trialDays > 0 ? trialDays : undefined,
      metadata: {
        tenantId: args.tenantId,
        planId: plan.id,
      },
    });

    // Create local subscription record
    const sub = await this.prisma.subscription.create({
      data: {
        tenantId: args.tenantId,
        planId: plan.id,
        tier: plan.tier,
        status: this.mapStripeStatus(stripeSubscription.status),
        billingInterval: args.billingInterval,
        stripeSubscriptionId: stripeSubscription.id,
        stripeCustomerId: customer.stripeCustomerId,
        currentPeriodStart: new Date(stripeSubscription.current_period_start * 1000),
        currentPeriodEnd: new Date(stripeSubscription.current_period_end * 1000),
        trialStart: stripeSubscription.trial_start
          ? new Date(stripeSubscription.trial_start * 1000)
          : null,
        trialEnd: stripeSubscription.trial_end
          ? new Date(stripeSubscription.trial_end * 1000)
          : null,
      },
    });

    return this.mapToSubscription(sub);
  }

  async previewChange(args: {
    tenantId: TenantId;
    subscriptionId: SubscriptionId;
    newPlanId: string;
    newBillingInterval?: BillingInterval;
  }): Promise<SubscriptionChangePreview> {
    const currentSub = await this.prisma.subscription.findUnique({
      where: { id: args.subscriptionId },
    });

    if (!currentSub) {
      throw new Error('Subscription not found');
    }

    const currentPlan = DEFAULT_PLANS.find((p) => p.id === currentSub.planId);
    const newPlan = DEFAULT_PLANS.find((p) => p.id === args.newPlanId);

    if (!currentPlan || !newPlan) {
      throw new Error('Plan not found');
    }

    const interval = args.newBillingInterval ?? currentSub.billingInterval;
    const newPriceId = newPlan.pricing[interval as keyof typeof newPlan.pricing].stripePriceId;

    // Preview proration with Stripe
    let proratedAmount = 0;
    if (currentSub.stripeSubscriptionId && newPriceId) {
      const invoice = await this.stripe.invoices.retrieveUpcoming({
        customer: currentSub.stripeCustomerId!,
        subscription: currentSub.stripeSubscriptionId,
        subscription_items: [
          {
            id: (await this.stripe.subscriptions.retrieve(currentSub.stripeSubscriptionId))
              .items.data[0].id,
            price: newPriceId,
          },
        ],
      });
      proratedAmount = invoice.amount_due;
    }

    return {
      currentPlan: await this.getPlan({ planId: currentSub.planId }) as SubscriptionPlan,
      newPlan: await this.getPlan({ planId: args.newPlanId }) as SubscriptionPlan,
      proratedAmountCents: proratedAmount,
      effectiveDate: new Date().toISOString(),
      immediateCharge: proratedAmount > 0,
    };
  }

  async changePlan(args: {
    tenantId: TenantId;
    subscriptionId: SubscriptionId;
    newPlanId: string;
    newBillingInterval?: BillingInterval;
    prorationBehavior?: 'create_prorations' | 'none' | 'always_invoice';
  }): Promise<Subscription> {
    const currentSub = await this.prisma.subscription.findUnique({
      where: { id: args.subscriptionId },
    });

    if (!currentSub || currentSub.tenantId !== args.tenantId) {
      throw new Error('Subscription not found');
    }

    const newPlan = DEFAULT_PLANS.find((p) => p.id === args.newPlanId);
    if (!newPlan) {
      throw new Error('Plan not found');
    }

    const interval = args.newBillingInterval ?? currentSub.billingInterval;
    const newPriceId = newPlan.pricing[interval as keyof typeof newPlan.pricing].stripePriceId;

    if (currentSub.stripeSubscriptionId && newPriceId) {
      const stripeSub = await this.stripe.subscriptions.retrieve(currentSub.stripeSubscriptionId);
      
      await this.stripe.subscriptions.update(currentSub.stripeSubscriptionId, {
        items: [
          {
            id: stripeSub.items.data[0].id,
            price: newPriceId,
          },
        ],
        proration_behavior: args.prorationBehavior ?? 'create_prorations',
        metadata: {
          tenantId: args.tenantId,
          planId: args.newPlanId,
        },
      });
    }

    const updated = await this.prisma.subscription.update({
      where: { id: args.subscriptionId },
      data: {
        planId: args.newPlanId,
        tier: newPlan.tier,
        billingInterval: interval,
        updatedAt: new Date(),
      },
    });

    return this.mapToSubscription(updated);
  }

  async cancelSubscription(args: {
    tenantId: TenantId;
    subscriptionId: SubscriptionId;
    cancelImmediately?: boolean;
    reason?: string;
  }): Promise<Subscription> {
    const sub = await this.prisma.subscription.findUnique({
      where: { id: args.subscriptionId },
    });

    if (!sub || sub.tenantId !== args.tenantId) {
      throw new Error('Subscription not found');
    }

    if (sub.stripeSubscriptionId) {
      if (args.cancelImmediately) {
        await this.stripe.subscriptions.cancel(sub.stripeSubscriptionId, {
          cancellation_details: { comment: args.reason },
        });
      } else {
        await this.stripe.subscriptions.update(sub.stripeSubscriptionId, {
          cancel_at_period_end: true,
          cancellation_details: { comment: args.reason },
        });
      }
    }

    const updated = await this.prisma.subscription.update({
      where: { id: args.subscriptionId },
      data: {
        status: args.cancelImmediately ? 'canceled' : sub.status,
        cancelAtPeriodEnd: !args.cancelImmediately,
        canceledAt: new Date(),
        updatedAt: new Date(),
      },
    });

    return this.mapToSubscription(updated);
  }

  async resumeSubscription(args: {
    tenantId: TenantId;
    subscriptionId: SubscriptionId;
  }): Promise<Subscription> {
    const sub = await this.prisma.subscription.findUnique({
      where: { id: args.subscriptionId },
    });

    if (!sub || sub.tenantId !== args.tenantId) {
      throw new Error('Subscription not found');
    }

    if (!sub.cancelAtPeriodEnd) {
      throw new Error('Subscription is not scheduled for cancellation');
    }

    if (sub.stripeSubscriptionId) {
      await this.stripe.subscriptions.update(sub.stripeSubscriptionId, {
        cancel_at_period_end: false,
      });
    }

    const updated = await this.prisma.subscription.update({
      where: { id: args.subscriptionId },
      data: {
        cancelAtPeriodEnd: false,
        canceledAt: null,
        updatedAt: new Date(),
      },
    });

    return this.mapToSubscription(updated);
  }

  async pauseSubscription(args: {
    tenantId: TenantId;
    subscriptionId: SubscriptionId;
    resumeAt?: string;
  }): Promise<Subscription> {
    const sub = await this.prisma.subscription.findUnique({
      where: { id: args.subscriptionId },
    });

    if (!sub || sub.tenantId !== args.tenantId) {
      throw new Error('Subscription not found');
    }

    if (sub.stripeSubscriptionId) {
      await this.stripe.subscriptions.update(sub.stripeSubscriptionId, {
        pause_collection: {
          behavior: 'mark_uncollectible',
          resumes_at: args.resumeAt ? Math.floor(new Date(args.resumeAt).getTime() / 1000) : undefined,
        },
      });
    }

    const updated = await this.prisma.subscription.update({
      where: { id: args.subscriptionId },
      data: {
        status: 'paused',
        updatedAt: new Date(),
      },
    });

    return this.mapToSubscription(updated);
  }

  async getUsage(args: {
    tenantId: TenantId;
    subscriptionId: SubscriptionId;
  }): Promise<SubscriptionUsage> {
    const sub = await this.prisma.subscription.findUnique({
      where: { id: args.subscriptionId },
    });

    if (!sub || sub.tenantId !== args.tenantId) {
      throw new Error('Subscription not found');
    }

    const plan = DEFAULT_PLANS.find((p) => p.id === sub.planId);
    if (!plan) {
      throw new Error('Plan not found');
    }

    // Get actual usage counts
    const [userCount, moduleCount, classroomCount] = await Promise.all([
      this.prisma.user.count({ where: { tenantId: args.tenantId } }),
      this.prisma.module.count({ where: { tenantId: args.tenantId } }),
      this.prisma.classroom.count({ where: { tenantId: args.tenantId } }),
    ]);

    // Query actual storage (sum of media_asset.size_bytes) and VR session counts.
    // These tables are populated by the content-studio and xr-experience services.
    // Falls back to 0 if the tables do not yet exist in the current deployment.
    const [storageGB, vrSessions] = await Promise.all([
      this.prisma.$queryRawUnsafe<[{ total_bytes: bigint }]>(
        `SELECT COALESCE(SUM(size_bytes), 0) AS total_bytes FROM media_assets WHERE tenant_id = $1`,
        args.tenantId,
      )
        .then(([row]) => Number(row?.total_bytes ?? 0) / (1024 ** 3))
        .catch(() => 0),
      this.prisma.$queryRawUnsafe<[{ session_count: bigint }]>(
        `SELECT COUNT(*) AS session_count FROM vr_sessions WHERE tenant_id = $1 AND started_at >= $2 AND started_at < $3`,
        args.tenantId,
        sub.currentPeriodStart,
        sub.currentPeriodEnd,
      )
        .then(([row]) => Number(row?.session_count ?? 0))
        .catch(() => 0),
    ]);

    return {
      subscriptionId: args.subscriptionId,
      period: {
        start: sub.currentPeriodStart.toISOString(),
        end: sub.currentPeriodEnd.toISOString(),
      },
      users: { current: userCount, limit: plan.limits.maxUsers },
      modules: { current: moduleCount, limit: plan.limits.maxModules },
      storageGB: { current: storageGB, limit: plan.limits.maxStorageGB },
      classrooms: { current: classroomCount, limit: plan.limits.maxClassrooms },
      vrSessions: { current: vrSessions, limit: plan.limits.maxVrSessions },
    };
  }

  async checkLimit(args: {
    tenantId: TenantId;
    resource: 'users' | 'modules' | 'classrooms' | 'storage' | 'vrSessions';
    increment?: number;
  }): Promise<{ allowed: boolean; current: number; limit: number; message?: string }> {
    const sub = await this.getCurrentSubscription({ tenantId: args.tenantId });
    
    if (!sub) {
      return { allowed: false, current: 0, limit: 0, message: 'No active subscription' };
    }

    const usage = await this.getUsage({
      tenantId: args.tenantId,
      subscriptionId: sub.id,
    });

    const resourceMap = {
      users: usage.users,
      modules: usage.modules,
      classrooms: usage.classrooms,
      storage: usage.storageGB,
      vrSessions: usage.vrSessions,
    };

    const { current, limit } = resourceMap[args.resource];
    const increment = args.increment ?? 1;

    if (limit === -1) {
      return { allowed: true, current, limit };
    }

    const allowed = current + increment <= limit;
    return {
      allowed,
      current,
      limit,
      message: allowed ? undefined : `${args.resource} limit reached (${current}/${limit})`,
    };
  }

  private mapToSubscription(record: any): Subscription {
    return {
      id: record.id as SubscriptionId,
      tenantId: record.tenantId as TenantId,
      planId: record.planId,
      tier: record.tier as SubscriptionTier,
      status: record.status as SubscriptionStatus,
      billingInterval: record.billingInterval as BillingInterval,
      currentPeriodStart: record.currentPeriodStart.toISOString(),
      currentPeriodEnd: record.currentPeriodEnd.toISOString(),
      cancelAtPeriodEnd: record.cancelAtPeriodEnd,
      canceledAt: record.canceledAt?.toISOString(),
      trialStart: record.trialStart?.toISOString(),
      trialEnd: record.trialEnd?.toISOString(),
      stripeSubscriptionId: record.stripeSubscriptionId ?? undefined,
      stripeCustomerId: record.stripeCustomerId ?? undefined,
      createdAt: record.createdAt.toISOString(),
      updatedAt: record.updatedAt.toISOString(),
    };
  }

  private mapStripeStatus(stripeStatus: Stripe.Subscription.Status): SubscriptionStatus {
    const statusMap: Record<string, SubscriptionStatus> = {
      active: 'active',
      trialing: 'trialing',
      past_due: 'past_due',
      canceled: 'canceled',
      paused: 'paused',
      incomplete: 'incomplete',
      incomplete_expired: 'incomplete_expired',
      unpaid: 'past_due',
    };
    return statusMap[stripeStatus] ?? 'incomplete';
  }
}

/**
 * Payment method service implementation.
 */
export class PaymentMethodServiceImpl implements PaymentMethodService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly stripe: Stripe,
  ) {}

  async listPaymentMethods(args: { tenantId: TenantId }): Promise<PaymentMethod[]> {
    const methods = await this.prisma.paymentMethod.findMany({
      where: { tenantId: args.tenantId },
      orderBy: [{ isDefault: 'desc' }, { createdAt: 'desc' }],
    });

    return methods.map((m) => this.mapToPaymentMethod(m));
  }

  async getPaymentMethod(args: {
    tenantId: TenantId;
    paymentMethodId: PaymentMethodId;
  }): Promise<PaymentMethod | null> {
    const method = await this.prisma.paymentMethod.findFirst({
      where: {
        id: args.paymentMethodId,
        tenantId: args.tenantId,
      },
    });

    return method ? this.mapToPaymentMethod(method) : null;
  }

  async createSetupIntent(args: {
    tenantId: TenantId;
    paymentMethodType: PaymentMethodType;
  }): Promise<{ clientSecret: string; setupIntentId: string }> {
    const customer = await this.prisma.stripeCustomer.findUnique({
      where: { tenantId: args.tenantId },
    });

    if (!customer) {
      throw new Error('Stripe customer not found');
    }

    const setupIntent = await this.stripe.setupIntents.create({
      customer: customer.stripeCustomerId,
      payment_method_types: [args.paymentMethodType === 'card' ? 'card' : 'us_bank_account'],
      metadata: { tenantId: args.tenantId },
    });

    return {
      clientSecret: setupIntent.client_secret!,
      setupIntentId: setupIntent.id,
    };
  }

  async attachPaymentMethod(args: {
    tenantId: TenantId;
    setupIntentId: string;
    billingAddress?: BillingAddress;
    setAsDefault?: boolean;
  }): Promise<PaymentMethod> {
    const setupIntent = await this.stripe.setupIntents.retrieve(args.setupIntentId);

    if (!setupIntent.payment_method || typeof setupIntent.payment_method !== 'string') {
      throw new Error('Payment method not found in setup intent');
    }

    const stripePaymentMethod = await this.stripe.paymentMethods.retrieve(
      setupIntent.payment_method,
    );

    // If setting as default, unset other defaults
    if (args.setAsDefault) {
      await this.prisma.paymentMethod.updateMany({
        where: { tenantId: args.tenantId, isDefault: true },
        data: { isDefault: false },
      });
    }

    const method = await this.prisma.paymentMethod.create({
      data: {
        tenantId: args.tenantId,
        stripePaymentMethodId: stripePaymentMethod.id,
        type: stripePaymentMethod.type === 'card' ? 'card' : 'bank_account',
        isDefault: args.setAsDefault ?? false,
        lastFour: stripePaymentMethod.card?.last4 ?? stripePaymentMethod.us_bank_account?.last4,
        brand: stripePaymentMethod.card?.brand,
        expMonth: stripePaymentMethod.card?.exp_month,
        expYear: stripePaymentMethod.card?.exp_year,
        billingName: args.billingAddress?.name,
        billingLine1: args.billingAddress?.line1,
        billingLine2: args.billingAddress?.line2,
        billingCity: args.billingAddress?.city,
        billingState: args.billingAddress?.state,
        billingPostalCode: args.billingAddress?.postalCode,
        billingCountry: args.billingAddress?.country,
      },
    });

    return this.mapToPaymentMethod(method);
  }

  async setDefaultPaymentMethod(args: {
    tenantId: TenantId;
    paymentMethodId: PaymentMethodId;
  }): Promise<void> {
    await this.prisma.$transaction([
      this.prisma.paymentMethod.updateMany({
        where: { tenantId: args.tenantId, isDefault: true },
        data: { isDefault: false },
      }),
      this.prisma.paymentMethod.update({
        where: { id: args.paymentMethodId },
        data: { isDefault: true },
      }),
    ]);

    const method = await this.prisma.paymentMethod.findUnique({
      where: { id: args.paymentMethodId },
    });

    if (method?.stripePaymentMethodId) {
      const customer = await this.prisma.stripeCustomer.findUnique({
        where: { tenantId: args.tenantId },
      });

      if (customer) {
        await this.stripe.customers.update(customer.stripeCustomerId, {
          invoice_settings: { default_payment_method: method.stripePaymentMethodId },
        });
      }
    }
  }

  async removePaymentMethod(args: {
    tenantId: TenantId;
    paymentMethodId: PaymentMethodId;
  }): Promise<void> {
    const method = await this.prisma.paymentMethod.findFirst({
      where: { id: args.paymentMethodId, tenantId: args.tenantId },
    });

    if (!method) {
      throw new Error('Payment method not found');
    }

    if (method.isDefault) {
      throw new Error('Cannot remove default payment method');
    }

    if (method.stripePaymentMethodId) {
      await this.stripe.paymentMethods.detach(method.stripePaymentMethodId);
    }

    await this.prisma.paymentMethod.delete({
      where: { id: args.paymentMethodId },
    });
  }

  async updateBillingAddress(args: {
    tenantId: TenantId;
    paymentMethodId: PaymentMethodId;
    billingAddress: BillingAddress;
  }): Promise<PaymentMethod> {
    const method = await this.prisma.paymentMethod.update({
      where: { id: args.paymentMethodId },
      data: {
        billingName: args.billingAddress.name,
        billingLine1: args.billingAddress.line1,
        billingLine2: args.billingAddress.line2,
        billingCity: args.billingAddress.city,
        billingState: args.billingAddress.state,
        billingPostalCode: args.billingAddress.postalCode,
        billingCountry: args.billingAddress.country,
      },
    });

    return this.mapToPaymentMethod(method);
  }

  private mapToPaymentMethod(record: any): PaymentMethod {
    return {
      id: record.id as PaymentMethodId,
      tenantId: record.tenantId as TenantId,
      type: record.type as PaymentMethodType,
      isDefault: record.isDefault,
      card: record.type === 'card'
        ? {
            brand: record.brand as any,
            last4: record.lastFour ?? '',
            expMonth: record.expMonth ?? 0,
            expYear: record.expYear ?? 0,
          }
        : undefined,
      bankAccount: record.type === 'bank_account'
        ? {
            bankName: record.bankName ?? '',
            last4: record.lastFour ?? '',
            accountType: 'checking',
          }
        : undefined,
      billingAddress: record.billingLine1
        ? {
            name: record.billingName ?? '',
            line1: record.billingLine1,
            line2: record.billingLine2 ?? undefined,
            city: record.billingCity ?? '',
            state: record.billingState ?? undefined,
            postalCode: record.billingPostalCode ?? '',
            country: record.billingCountry ?? '',
          }
        : undefined,
      stripePaymentMethodId: record.stripePaymentMethodId ?? undefined,
      createdAt: record.createdAt.toISOString(),
      expiresAt: record.expYear && record.expMonth
        ? new Date(record.expYear, record.expMonth, 0).toISOString()
        : undefined,
    };
  }
}

/**
 * Invoice service implementation.
 */
export class InvoiceServiceImpl implements InvoiceService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly stripe: Stripe,
  ) {}

  async listInvoices(args: {
    tenantId: TenantId;
    status?: Invoice['status'];
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<Invoice>> {
    const where: any = { tenantId: args.tenantId };
    if (args.status) {
      where.status = args.status;
    }

    const [invoices, total] = await Promise.all([
      this.prisma.invoice.findMany({
        where,
        orderBy: { createdAt: 'desc' },
        take: args.pagination.limit,
        skip: args.pagination.cursor ? 1 : 0,
        cursor: args.pagination.cursor ? { id: args.pagination.cursor } : undefined,
        include: { lineItems: true },
      }),
      this.prisma.invoice.count({ where }),
    ]);

    return {
      items: invoices.map((i) => this.mapToInvoice(i)),
      nextCursor: invoices.length === args.pagination.limit
        ? invoices[invoices.length - 1].id
        : undefined,
      totalCount: total,
      hasMore: invoices.length === args.pagination.limit,
    };
  }

  async getInvoice(args: {
    tenantId: TenantId;
    invoiceId: InvoiceId;
  }): Promise<Invoice | null> {
    const invoice = await this.prisma.invoice.findFirst({
      where: { id: args.invoiceId, tenantId: args.tenantId },
      include: { lineItems: true },
    });

    return invoice ? this.mapToInvoice(invoice) : null;
  }

  async getUpcomingInvoice(args: {
    tenantId: TenantId;
    subscriptionId: SubscriptionId;
  }): Promise<Invoice | null> {
    const sub = await this.prisma.subscription.findUnique({
      where: { id: args.subscriptionId },
    });

    if (!sub?.stripeSubscriptionId) return null;

    try {
      const upcoming = await this.stripe.invoices.retrieveUpcoming({
        subscription: sub.stripeSubscriptionId,
      });

      return {
        id: 'upcoming' as InvoiceId,
        tenantId: args.tenantId,
        subscriptionId: args.subscriptionId,
        number: 'UPCOMING',
        status: 'draft',
        currency: upcoming.currency,
        subtotalCents: upcoming.subtotal,
        taxCents: upcoming.tax ?? 0,
        totalCents: upcoming.total,
        amountPaidCents: upcoming.amount_paid,
        amountDueCents: upcoming.amount_due,
        dueDate: upcoming.due_date
          ? new Date(upcoming.due_date * 1000).toISOString()
          : new Date().toISOString(),
        lineItems: upcoming.lines.data.map((line) => ({
          description: line.description ?? '',
          quantity: line.quantity ?? 1,
          unitAmountCents: line.price?.unit_amount ?? 0,
          amountCents: line.amount,
          periodStart: line.period?.start
            ? new Date(line.period.start * 1000).toISOString()
            : undefined,
          periodEnd: line.period?.end
            ? new Date(line.period.end * 1000).toISOString()
            : undefined,
        })),
        createdAt: new Date().toISOString(),
      };
    } catch {
      return null;
    }
  }

  async payInvoice(args: {
    tenantId: TenantId;
    invoiceId: InvoiceId;
    paymentMethodId?: PaymentMethodId;
  }): Promise<Invoice> {
    const invoice = await this.prisma.invoice.findFirst({
      where: { id: args.invoiceId, tenantId: args.tenantId },
      include: { lineItems: true },
    });

    if (!invoice) {
      throw new Error('Invoice not found');
    }

    if (invoice.stripeInvoiceId) {
      let paymentMethodId: string | undefined;
      if (args.paymentMethodId) {
        const pm = await this.prisma.paymentMethod.findUnique({
          where: { id: args.paymentMethodId },
        });
        paymentMethodId = pm?.stripePaymentMethodId ?? undefined;
      }

      await this.stripe.invoices.pay(invoice.stripeInvoiceId, {
        payment_method: paymentMethodId,
      });
    }

    const updated = await this.prisma.invoice.update({
      where: { id: args.invoiceId },
      data: {
        status: 'paid',
        paidAt: new Date(),
      },
      include: { lineItems: true },
    });

    return this.mapToInvoice(updated);
  }

  async getInvoicePdf(args: {
    tenantId: TenantId;
    invoiceId: InvoiceId;
  }): Promise<{ url: string; expiresAt: string }> {
    const invoice = await this.prisma.invoice.findFirst({
      where: { id: args.invoiceId, tenantId: args.tenantId },
    });

    if (!invoice?.invoicePdfUrl) {
      throw new Error('Invoice PDF not available');
    }

    return {
      url: invoice.invoicePdfUrl,
      expiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
    };
  }

  async sendInvoiceEmail(args: {
    tenantId: TenantId;
    invoiceId: InvoiceId;
    email: string;
  }): Promise<void> {
    const invoice = await this.prisma.invoice.findFirst({
      where: { id: args.invoiceId, tenantId: args.tenantId },
    });

    if (!invoice?.stripeInvoiceId) {
      throw new Error('Invoice not found');
    }

    await this.stripe.invoices.sendInvoice(invoice.stripeInvoiceId);
  }

  private mapToInvoice(record: any): Invoice {
    return {
      id: record.id as InvoiceId,
      tenantId: record.tenantId as TenantId,
      subscriptionId: record.subscriptionId as SubscriptionId,
      number: record.number,
      status: record.status,
      currency: record.currency,
      subtotalCents: record.subtotalCents,
      taxCents: record.taxCents,
      totalCents: record.totalCents,
      amountPaidCents: record.amountPaidCents,
      amountDueCents: record.amountDueCents,
      dueDate: record.dueDate.toISOString(),
      paidAt: record.paidAt?.toISOString(),
      lineItems: record.lineItems?.map((li: any) => ({
        description: li.description,
        quantity: li.quantity,
        unitAmountCents: li.unitAmountCents,
        amountCents: li.amountCents,
        periodStart: li.periodStart?.toISOString(),
        periodEnd: li.periodEnd?.toISOString(),
      })) ?? [],
      stripeInvoiceId: record.stripeInvoiceId ?? undefined,
      hostedInvoiceUrl: record.hostedInvoiceUrl ?? undefined,
      invoicePdfUrl: record.invoicePdfUrl ?? undefined,
      createdAt: record.createdAt.toISOString(),
    };
  }
}

/**
 * Payment webhook service implementation.
 */
export class PaymentWebhookServiceImpl implements PaymentWebhookService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly stripe: Stripe,
    private readonly webhookSecret: string,
    private readonly notificationService?: NotificationService,
  ) {}

  async processWebhook(args: {
    payload: string;
    signature: string;
    provider: 'stripe';
  }): Promise<{ processed: boolean; eventType: string; error?: string }> {
    let event: Stripe.Event;

    try {
      event = this.stripe.webhooks.constructEvent(
        args.payload,
        args.signature,
        this.webhookSecret,
      );
    } catch (err) {
      return {
        processed: false,
        eventType: 'unknown',
        error: `Webhook signature verification failed: ${err}`,
      };
    }

    // Store event for audit trail
    await this.prisma.webhookEvent.create({
      data: {
        stripeEventId: event.id,
        type: event.type,
        data: event.data.object as any,
        processed: false,
      },
    });

    try {
      await this.handleStripeEvent(event);

      await this.prisma.webhookEvent.update({
        where: { stripeEventId: event.id },
        data: { processed: true, processedAt: new Date() },
      });

      return { processed: true, eventType: event.type };
    } catch (err) {
      await this.prisma.webhookEvent.update({
        where: { stripeEventId: event.id },
        data: { error: String(err) },
      });

      return {
        processed: false,
        eventType: event.type,
        error: String(err),
      };
    }
  }

  async listWebhookEvents(args: {
    tenantId?: TenantId;
    eventType?: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<PaymentWebhookEvent>> {
    const where: any = {};
    if (args.eventType) {
      where.type = args.eventType;
    }

    const [events, total] = await Promise.all([
      this.prisma.webhookEvent.findMany({
        where,
        orderBy: { createdAt: 'desc' },
        take: args.pagination.limit,
        skip: args.pagination.cursor ? 1 : 0,
        cursor: args.pagination.cursor ? { id: args.pagination.cursor } : undefined,
      }),
      this.prisma.webhookEvent.count({ where }),
    ]);

    return {
      items: events.map((e) => ({
        id: e.id,
        type: e.type as any,
        data: e.data as Record<string, unknown>,
        stripeEventId: e.stripeEventId,
        processedAt: e.processedAt?.toISOString(),
        createdAt: e.createdAt.toISOString(),
      })),
      nextCursor: events.length === args.pagination.limit
        ? events[events.length - 1].id
        : undefined,
      totalCount: total,
      hasMore: events.length === args.pagination.limit,
    };
  }

  async retryWebhook(args: {
    eventId: string;
  }): Promise<{ success: boolean; error?: string }> {
    const event = await this.prisma.webhookEvent.findUnique({
      where: { id: args.eventId },
    });

    if (!event) {
      return { success: false, error: 'Event not found' };
    }

    try {
      const stripeEvent = await this.stripe.events.retrieve(event.stripeEventId);
      await this.handleStripeEvent(stripeEvent);

      await this.prisma.webhookEvent.update({
        where: { id: args.eventId },
        data: { processed: true, processedAt: new Date(), error: null },
      });

      return { success: true };
    } catch (err) {
      return { success: false, error: String(err) };
    }
  }

  private async handleStripeEvent(event: Stripe.Event): Promise<void> {
    switch (event.type) {
      case 'customer.subscription.created':
      case 'customer.subscription.updated':
        await this.handleSubscriptionChange(event.data.object as Stripe.Subscription);
        break;

      case 'customer.subscription.deleted':
        await this.handleSubscriptionDeleted(event.data.object as Stripe.Subscription);
        break;

      case 'invoice.created':
      case 'invoice.finalized':
        await this.handleInvoiceCreated(event.data.object as Stripe.Invoice);
        break;

      case 'invoice.paid':
        await this.handleInvoicePaid(event.data.object as Stripe.Invoice);
        break;

      case 'invoice.payment_failed':
        await this.handleInvoicePaymentFailed(event.data.object as Stripe.Invoice);
        break;

      case 'payment_method.attached':
        await this.handlePaymentMethodAttached(event.data.object as Stripe.PaymentMethod);
        break;

      case 'payment_method.detached':
        await this.handlePaymentMethodDetached(event.data.object as Stripe.PaymentMethod);
        break;

      case 'customer.subscription.trial_will_end':
        await this.handleTrialWillEnd(event.data.object as Stripe.Subscription);
        break;
    }
  }

  private async handleSubscriptionChange(stripeSub: Stripe.Subscription): Promise<void> {
    const tenantId = stripeSub.metadata.tenantId as TenantId;
    if (!tenantId) return;

    const statusMap: Record<string, string> = {
      active: 'active',
      trialing: 'trialing',
      past_due: 'past_due',
      canceled: 'canceled',
      incomplete: 'incomplete',
      incomplete_expired: 'incomplete_expired',
      paused: 'paused',
      unpaid: 'past_due',
    };

    await this.prisma.subscription.updateMany({
      where: { stripeSubscriptionId: stripeSub.id },
      data: {
        status: statusMap[stripeSub.status] ?? 'incomplete',
        currentPeriodStart: new Date(stripeSub.current_period_start * 1000),
        currentPeriodEnd: new Date(stripeSub.current_period_end * 1000),
        cancelAtPeriodEnd: stripeSub.cancel_at_period_end,
        canceledAt: stripeSub.canceled_at
          ? new Date(stripeSub.canceled_at * 1000)
          : null,
        updatedAt: new Date(),
      },
    });
  }

  private async handleSubscriptionDeleted(stripeSub: Stripe.Subscription): Promise<void> {
    await this.prisma.subscription.updateMany({
      where: { stripeSubscriptionId: stripeSub.id },
      data: {
        status: 'canceled',
        canceledAt: new Date(),
        updatedAt: new Date(),
      },
    });
  }

  private async handleInvoiceCreated(stripeInvoice: Stripe.Invoice): Promise<void> {
    const sub = await this.prisma.subscription.findFirst({
      where: { stripeSubscriptionId: stripeInvoice.subscription as string },
    });

    if (!sub) return;

    await this.prisma.invoice.upsert({
      where: { stripeInvoiceId: stripeInvoice.id },
      create: {
        tenantId: sub.tenantId,
        subscriptionId: sub.id,
        stripeInvoiceId: stripeInvoice.id,
        number: stripeInvoice.number ?? `INV-${Date.now()}`,
        status: stripeInvoice.status ?? 'draft',
        currency: stripeInvoice.currency,
        subtotalCents: stripeInvoice.subtotal,
        taxCents: stripeInvoice.tax ?? 0,
        totalCents: stripeInvoice.total,
        amountPaidCents: stripeInvoice.amount_paid,
        amountDueCents: stripeInvoice.amount_due,
        dueDate: stripeInvoice.due_date
          ? new Date(stripeInvoice.due_date * 1000)
          : new Date(),
        hostedInvoiceUrl: stripeInvoice.hosted_invoice_url ?? null,
        invoicePdfUrl: stripeInvoice.invoice_pdf ?? null,
      },
      update: {
        status: stripeInvoice.status ?? 'draft',
        amountPaidCents: stripeInvoice.amount_paid,
        amountDueCents: stripeInvoice.amount_due,
        hostedInvoiceUrl: stripeInvoice.hosted_invoice_url ?? null,
        invoicePdfUrl: stripeInvoice.invoice_pdf ?? null,
      },
    });
  }

  private async handleInvoicePaid(stripeInvoice: Stripe.Invoice): Promise<void> {
    await this.prisma.invoice.updateMany({
      where: { stripeInvoiceId: stripeInvoice.id },
      data: {
        status: 'paid',
        paidAt: new Date(),
        amountPaidCents: stripeInvoice.amount_paid,
        amountDueCents: stripeInvoice.amount_due,
      },
    });
  }

  private async handleInvoicePaymentFailed(stripeInvoice: Stripe.Invoice): Promise<void> {
    await this.prisma.invoice.updateMany({
      where: { stripeInvoiceId: stripeInvoice.id },
      data: {
        status: 'open',
      },
    });

    // Mark subscription as past_due if not already
    if (stripeInvoice.subscription) {
      await this.prisma.subscription.updateMany({
        where: {
          stripeSubscriptionId: stripeInvoice.subscription as string,
          status: 'active',
        },
        data: { status: 'past_due' },
      });
    }
  }

  private async handlePaymentMethodAttached(pm: Stripe.PaymentMethod): Promise<void> {
    if (!pm.customer || typeof pm.customer !== 'string') return;

    const customer = await this.prisma.stripeCustomer.findFirst({
      where: { stripeCustomerId: pm.customer },
    });

    if (!customer) return;

    await this.prisma.paymentMethod.upsert({
      where: { stripePaymentMethodId: pm.id },
      create: {
        tenantId: customer.tenantId,
        stripePaymentMethodId: pm.id,
        type: pm.type === 'card' ? 'card' : 'bank_account',
        isDefault: false,
        lastFour: pm.card?.last4 ?? pm.us_bank_account?.last4,
        brand: pm.card?.brand,
        expMonth: pm.card?.exp_month,
        expYear: pm.card?.exp_year,
      },
      update: {},
    });
  }

  private async handlePaymentMethodDetached(pm: Stripe.PaymentMethod): Promise<void> {
    await this.prisma.paymentMethod.deleteMany({
      where: { stripePaymentMethodId: pm.id },
    });
  }

  private async handleTrialWillEnd(stripeSub: Stripe.Subscription): Promise<void> {
    const trialEndDate = stripeSub.trial_end
      ? new Date(stripeSub.trial_end * 1000).toISOString()
      : new Date().toISOString();

    // Resolve tenant and email from Stripe customer record
    const customer = await this.prisma.stripeCustomer
      .findUnique({ where: { stripeCustomerId: String(stripeSub.customer) } })
      .catch(() => null);

    if (!customer) {
      console.warn(`handleTrialWillEnd: no local customer for stripe sub ${stripeSub.id}`);
      return;
    }

    // Look up the plan name from the first subscription item
    const priceId = stripeSub.items.data[0]?.price.id ?? '';
    const plan = DEFAULT_PLANS.find(
      (p) =>
        p.pricing.monthly.stripePriceId === priceId ||
        p.pricing.quarterly.stripePriceId === priceId ||
        p.pricing.annual.stripePriceId === priceId,
    );
    const planName = plan?.name ?? 'your current plan';

    if (this.notificationService) {
      await this.notificationService.sendTrialEndingEmail({
        tenantId: customer.tenantId,
        email: customer.email,
        trialEndDate,
        planName,
      });
    } else {
      // Fallback: structured log so downstream observability can pick it up
      console.log(JSON.stringify({
        event: 'trial.will_end',
        tenantId: customer.tenantId,
        email: customer.email,
        trialEndDate,
        planName,
        subscriptionId: stripeSub.id,
      }));
    }
  }
}
