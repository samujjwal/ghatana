/**
 * @doc.type module
 * @doc.purpose Internal types for payment service
 * @doc.layer product
 * @doc.pattern Types
 */

import type {
  TenantId,
  UserId,
  SubscriptionId,
  PaymentMethodId,
  InvoiceId,
  TransactionId,
  SubscriptionTier,
  BillingInterval,
  SubscriptionStatus,
} from '@ghatana/tutorputor-contracts/v1/types';

/**
 * Stripe customer mapping.
 */
export interface StripeCustomer {
  tenantId: TenantId;
  stripeCustomerId: string;
  email: string;
  name?: string;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Internal subscription record with Stripe IDs.
 */
export interface SubscriptionRecord {
  id: SubscriptionId;
  tenantId: TenantId;
  stripeSubscriptionId: string;
  stripeCustomerId: string;
  stripePriceId: string;
  tier: SubscriptionTier;
  status: SubscriptionStatus;
  billingInterval: BillingInterval;
  currentPeriodStart: Date;
  currentPeriodEnd: Date;
  cancelAtPeriodEnd: boolean;
  canceledAt?: Date;
  trialStart?: Date;
  trialEnd?: Date;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * Payment method record with Stripe mapping.
 */
export interface PaymentMethodRecord {
  id: PaymentMethodId;
  tenantId: TenantId;
  stripePaymentMethodId: string;
  type: 'card' | 'bank_account' | 'paypal';
  isDefault: boolean;
  lastFour?: string;
  brand?: string;
  expMonth?: number;
  expYear?: number;
  createdAt: Date;
}

/**
 * Invoice record with Stripe mapping.
 */
export interface InvoiceRecord {
  id: InvoiceId;
  tenantId: TenantId;
  subscriptionId: SubscriptionId;
  stripeInvoiceId: string;
  number: string;
  status: 'draft' | 'open' | 'paid' | 'void' | 'uncollectible';
  currency: string;
  subtotalCents: number;
  taxCents: number;
  totalCents: number;
  amountPaidCents: number;
  amountDueCents: number;
  dueDate: Date;
  paidAt?: Date;
  hostedInvoiceUrl?: string;
  invoicePdfUrl?: string;
  createdAt: Date;
}

/**
 * Transaction record.
 */
export interface TransactionRecord {
  id: TransactionId;
  tenantId: TenantId;
  invoiceId?: InvoiceId;
  paymentMethodId?: PaymentMethodId;
  stripePaymentIntentId?: string;
  stripeChargeId?: string;
  type: 'charge' | 'refund' | 'adjustment';
  status: 'pending' | 'succeeded' | 'failed' | 'canceled';
  amountCents: number;
  currency: string;
  failureReason?: string;
  receiptUrl?: string;
  createdAt: Date;
  processedAt?: Date;
}

/**
 * Webhook event record.
 */
export interface WebhookEventRecord {
  id: string;
  stripeEventId: string;
  type: string;
  data: Record<string, unknown>;
  processed: boolean;
  processedAt?: Date;
  error?: string;
  createdAt: Date;
}

/**
 * Subscription plan configuration.
 */
export interface PlanConfig {
  id: string;
  name: string;
  tier: SubscriptionTier;
  description: string;
  features: string[];
  limits: {
    maxUsers: number;
    maxModules: number;
    maxStorageGB: number;
    maxClassrooms: number;
    maxVrSessions: number;
    analyticsRetentionDays: number;
    supportLevel: 'community' | 'email' | 'priority' | 'dedicated';
    customBranding: boolean;
    ssoEnabled: boolean;
    apiAccess: boolean;
  };
  pricing: {
    monthly: { amountCents: number; stripePriceId: string };
    quarterly: { amountCents: number; stripePriceId: string };
    annual: { amountCents: number; stripePriceId: string };
  };
  trialDays: number;
  isActive: boolean;
}

/**
 * Usage tracking for plan limits.
 */
export interface UsageSnapshot {
  tenantId: TenantId;
  subscriptionId: SubscriptionId;
  periodStart: Date;
  periodEnd: Date;
  users: number;
  modules: number;
  storageGB: number;
  classrooms: number;
  vrSessions: number;
  capturedAt: Date;
}

/**
 * Proration calculation result.
 */
export interface ProrationResult {
  amountCents: number;
  creditCents: number;
  chargeCents: number;
  effectiveDate: Date;
  lineItems: Array<{
    description: string;
    amountCents: number;
    periodStart: Date;
    periodEnd: Date;
  }>;
}

/**
 * Payment intent for one-time charges.
 */
export interface PaymentIntentData {
  amountCents: number;
  currency: string;
  customerId: string;
  paymentMethodId?: string;
  description: string;
  metadata: Record<string, string>;
}

/**
 * Checkout session for initial subscription.
 */
export interface CheckoutSessionData {
  tenantId: TenantId;
  customerId: string;
  priceId: string;
  successUrl: string;
  cancelUrl: string;
  trialDays?: number;
  metadata: Record<string, string>;
}

/**
 * Billing portal session.
 */
export interface BillingPortalSession {
  url: string;
  expiresAt: Date;
}

/**
 * Notification service for sending transactional emails and alerts.
 *
 * @doc.type interface
 * @doc.purpose Send email notifications for billing events
 * @doc.layer product
 * @doc.pattern Service
 */
export interface NotificationService {
  /**
   * Notify a tenant that their trial period is ending.
   *
   * @param args.tenantId - Tenant identifier
   * @param args.email    - Recipient email address
   * @param args.trialEndDate - ISO date string when trial ends
   * @param args.planName - Name of the plan the trial is for
   */
  sendTrialEndingEmail(args: {
    tenantId: TenantId;
    email: string;
    trialEndDate: string;
    planName: string;
  }): Promise<void>;
}
