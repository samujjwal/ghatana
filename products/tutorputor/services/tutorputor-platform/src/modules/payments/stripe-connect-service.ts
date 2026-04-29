/**
 * Stripe Connect Service
 *
 * Handles Stripe Connect onboarding for marketplace sellers.
 * Manages connected accounts and payouts.
 *
 * @doc.type service
 * @doc.purpose Stripe Connect integration for marketplace payments
 * @doc.layer product
 * @doc.pattern Service
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import Stripe from "stripe";
import type { PrismaClient } from "@tutorputor/core/db";

const logger = createStandaloneLogger({ component: "StripeConnectService" });

/**
 * Stripe Connect configuration
 */
export interface StripeConnectConfig {
  secretKey: string;
  webhookSecret?: string;
  platformFeePercent?: number;
}

/**
 * Onboarding link configuration
 */
export interface OnboardingLinkConfig {
  returnUrl: string;
  refreshUrl: string;
  type?: "account_onboarding" | "account_update";
}

/**
 * Account status
 */
export enum AccountStatus {
  PENDING = "PENDING",
  ONBOARDING = "ONBOARDING",
  ENABLED = "ENABLED",
  RESTRICTED = "RESTRICTED",
  DISABLED = "DISABLED",
}

/**
 * Stripe Connect Service
 */
export class StripeConnectService {
  private stripe: Stripe;
  private platformFeePercent: number;
  private prisma: PrismaClient;

  constructor(config: StripeConnectConfig, prisma: PrismaClient) {
    this.stripe = new Stripe(config.secretKey);
    this.platformFeePercent = config.platformFeePercent || 10; // 10% default platform fee
    this.prisma = prisma;
  }

  /**
   * Create a connected account for a seller
   */
  async createConnectedAccount(
    userId: string,
    email: string,
    country: string = "US",
  ): Promise<{ accountId: string; onboardingUrl: string }> {
    logger.info({ userId, email, country }, "Creating Stripe Connect account");

    try {
      // Create Stripe account
      const account = await this.stripe.accounts.create({
        type: "express",
        country,
        email,
        capabilities: {
          transfers: { requested: true },
          card_payments: { requested: true },
        },
        business_type: "individual",
        business_profile: {
          url: `https://tutorputor.com/sellers/${userId}`,
          product_description: "Educational content and tutoring services",
        },
        settings: {
          payouts: {
            schedule: {
              interval: "weekly",
              weekly_anchor: "friday",
            },
          },
        },
      });

      // Store account in database
      await this.saveAccountToDatabase(userId, account.id, AccountStatus.ONBOARDING);

      // Generate onboarding link
      const onboardingUrl = await this.generateOnboardingLink(account.id, {
        returnUrl: `https://tutorputor.com/sellers/${userId}/onboarding/complete`,
        refreshUrl: `https://tutorputor.com/sellers/${userId}/onboarding/refresh`,
      });

      logger.info({ userId, accountId: account.id }, "Stripe Connect account created");

      return {
        accountId: account.id,
        onboardingUrl,
      };
    } catch (error) {
      logger.error(
        { userId, error: error instanceof Error ? error.message : String(error) },
        "Failed to create Stripe Connect account",
      );
      throw error;
    }
  }

  /**
   * Generate onboarding link for an existing account
   */
  async generateOnboardingLink(
    accountId: string,
    config: OnboardingLinkConfig,
  ): Promise<string> {
    try {
      const link = await this.stripe.accountLinks.create({
        account: accountId,
        refresh_url: config.refreshUrl,
        return_url: config.returnUrl,
        type: config.type || "account_onboarding",
      });

      return link.url;
    } catch (error) {
      logger.error(
        { accountId, error: error instanceof Error ? error.message : String(error) },
        "Failed to generate onboarding link",
      );
      throw error;
    }
  }

  /**
   * Get account status
   */
  async getAccountStatus(accountId: string): Promise<{
    status: AccountStatus;
    chargesEnabled: boolean;
    payoutsEnabled: boolean;
    requirements: string[];
  }> {
    try {
      const account = await this.stripe.accounts.retrieve(accountId);

      // Determine status based on account state
      let status: AccountStatus;
      if (account.charges_enabled && account.payouts_enabled) {
        status = AccountStatus.ENABLED;
      } else if (account.details_submitted) {
        status = AccountStatus.RESTRICTED;
      } else {
        status = AccountStatus.ONBOARDING;
      }

      // Extract pending requirements
      const requirements: string[] = [];
      if (account.requirements?.currently_due) {
        requirements.push(...account.requirements.currently_due);
      }

      return {
        status,
        chargesEnabled: account.charges_enabled,
        payoutsEnabled: account.payouts_enabled,
        requirements,
      };
    } catch (error) {
      logger.error(
        { accountId, error: error instanceof Error ? error.message : String(error) },
        "Failed to get account status",
      );
      throw error;
    }
  }

  /**
   * Create a payment with platform fee
   */
  async createPayment(
    accountId: string,
    amount: number,
    currency: string = "usd",
    paymentMethodId: string,
    description?: string,
  ): Promise<{ paymentIntentId: string; clientSecret: string }> {
    logger.info({ accountId, amount, currency }, "Creating payment with platform fee");

    try {
      // Calculate platform fee
      const platformFee = Math.round(amount * (this.platformFeePercent / 100));

      // Build payment intent params
      const paymentIntentParams: Stripe.PaymentIntentCreateParams = {
        amount,
        currency,
        payment_method: paymentMethodId,
        confirm: true,
        application_fee_amount: platformFee,
        transfer_data: {
          destination: accountId,
        },
        metadata: {
          platform_fee: platformFee.toString(),
        },
      };

      // Only add description if provided
      if (description) {
        paymentIntentParams.description = description;
      }

      // Create payment intent with application fee
      const paymentIntent = await this.stripe.paymentIntents.create(paymentIntentParams);

      logger.info({ accountId, paymentIntentId: paymentIntent.id }, "Payment created successfully");

      return {
        paymentIntentId: paymentIntent.id,
        clientSecret: paymentIntent.client_secret!,
      };
    } catch (error) {
      logger.error(
        { accountId, amount, error: error instanceof Error ? error.message : String(error) },
        "Failed to create payment",
      );
      throw error;
    }
  }

  /**
   * Handle Stripe webhook events
   */
  async handleWebhook(
    payload: string,
    signature: string,
    webhookSecret: string,
  ): Promise<void> {
    try {
      const event = this.stripe.webhooks.constructEvent(payload, signature, webhookSecret);

      logger.info({ eventType: event.type }, "Processing Stripe webhook");

      switch (event.type) {
        case "account.updated":
          await this.handleAccountUpdated(event.data.object as Stripe.Account);
          break;
        case "payout.created":
          await this.handlePayoutCreated(event.data.object as Stripe.Payout);
          break;
        case "payout.failed":
          await this.handlePayoutFailed(event.data.object as Stripe.Payout);
          break;
        default:
          logger.info({ eventType: event.type }, "Unhandled webhook event");
      }
    } catch (error) {
      logger.error(
        { error: error instanceof Error ? error.message : String(error) },
        "Failed to handle webhook",
      );
      throw error;
    }
  }

  /**
   * Handle account updated event
   */
  private async handleAccountUpdated(account: Stripe.Account): Promise<void> {
    const userId = await this.getUserIdByAccountId(account.id);

    if (userId) {
      const status = account.charges_enabled && account.payouts_enabled
        ? AccountStatus.ENABLED
        : account.details_submitted
        ? AccountStatus.RESTRICTED
        : AccountStatus.ONBOARDING;

      await this.updateAccountStatus(userId, account.id, status);

      logger.info({ userId, accountId: account.id, status }, "Account status updated");
    }
  }

  /**
   * Handle payout created event — persist payout record to the database.
   */
  private async handlePayoutCreated(payout: Stripe.Payout): Promise<void> {
    logger.info(
      { payoutId: payout.id, amount: payout.amount, currency: payout.currency },
      "Payout created",
    );

    try {
      const destinationId = typeof payout.destination === "string" ? payout.destination : "";
      // Resolve tenant and user from the connected account mapping
      const stripeAccount = destinationId
        ? await this.prisma.stripeAccount.findUnique({
            where: { accountId: destinationId },
          })
        : null;

      const tenantId = stripeAccount ? "tenant-placeholder" : "default";
      const userId = stripeAccount ? stripeAccount.userId : "unknown";

      await this.prisma.payout.create({
        data: {
          tenantId,
          stripeAccountId: destinationId,
          stripePayoutId: payout.id,
          amountCents: payout.amount,
          currency: payout.currency,
          status: payout.status,
          arrivalDate: payout.arrival_date ? new Date(payout.arrival_date * 1000) : null,
          description: payout.description,
          metadata: payout.metadata ? JSON.stringify(payout.metadata) : null,
        },
      });
    } catch (error) {
      logger.error(
        { payoutId: payout.id, error: error instanceof Error ? error.message : String(error) },
        "Failed to persist payout created record",
      );
    }
  }

  /**
   * Handle payout failed event — persist payout record and notify the seller.
   */
  private async handlePayoutFailed(payout: Stripe.Payout): Promise<void> {
    logger.error(
      { payoutId: payout.id, amount: payout.amount, failureMessage: payout.failure_message },
      "Payout failed",
    );

    try {
      const destinationId = typeof payout.destination === "string" ? payout.destination : "";
      const stripeAccount = destinationId
        ? await this.prisma.stripeAccount.findUnique({
            where: { accountId: destinationId },
          })
        : null;

      const tenantId = stripeAccount ? "tenant-placeholder" : "default";
      const userId = stripeAccount ? stripeAccount.userId : "unknown";

      // Upsert the payout record so it reflects the failed status
      await this.prisma.payout.upsert({
        where: { stripePayoutId: payout.id },
        create: {
          tenantId,
          stripeAccountId: destinationId,
          stripePayoutId: payout.id,
          amountCents: payout.amount,
          currency: payout.currency,
          status: payout.status,
          arrivalDate: payout.arrival_date ? new Date(payout.arrival_date * 1000) : null,
          description: payout.description,
          metadata: payout.metadata ? JSON.stringify(payout.metadata) : null,
        },
        update: {
          status: payout.status,
        },
      });

      // Create a payout failure notification
      await this.prisma.payoutNotification.create({
        data: {
          tenantId,
          payoutId: payout.id,
          stripePayoutId: payout.id,
          userId,
          notificationType: "payout_failed",
          status: "PENDING",
          subject: "Payout Failed",
          body: `Your payout of ${payout.amount} ${payout.currency} failed: ${payout.failure_message ?? "Unknown reason"}`,
          failureReason: payout.failure_message ?? "Unknown",
          actionRequired: true,
        },
      });
    } catch (error) {
      logger.error(
        { payoutId: payout.id, error: error instanceof Error ? error.message : String(error) },
        "Failed to persist payout failure record or notification",
      );
    }
  }

  /**
   * Database operations — backed by Prisma
   */
  private async saveAccountToDatabase(
    userId: string,
    accountId: string,
    status: AccountStatus,
  ): Promise<void> {
    await this.prisma.stripeAccount.upsert({
      where: { accountId },
      create: {
        userId,
        accountId,
        status: this.mapAccountStatus(status),
        country: "US",
        email: "",
        chargesEnabled: false,
        payoutsEnabled: false,
        platformFeePercent: this.platformFeePercent,
      },
      update: {
        userId,
        status: this.mapAccountStatus(status),
      },
    });
  }

  private async getUserIdByAccountId(accountId: string): Promise<string | null> {
    const account = await this.prisma.stripeAccount.findUnique({ where: { accountId } });
    return account?.userId ?? null;
  }

  private async updateAccountStatus(
    userId: string,
    accountId: string,
    status: AccountStatus,
  ): Promise<void> {
    await this.prisma.stripeAccount.update({
      where: { accountId },
      data: { status: this.mapAccountStatus(status) },
    });
  }

  private mapAccountStatus(status: AccountStatus): "PENDING" | "ONBOARDING" | "ENABLED" | "RESTRICTED" | "DISABLED" {
    switch (status) {
      case AccountStatus.ONBOARDING:
        return "ONBOARDING";
      case AccountStatus.ENABLED:
        return "ENABLED";
      case AccountStatus.RESTRICTED:
        return "RESTRICTED";
      case AccountStatus.DISABLED:
        return "DISABLED";
      default:
        return "PENDING";
    }
  }
}

/**
 * Singleton instance
 */
let stripeConnectServiceInstance: StripeConnectService | null = null;

export function getStripeConnectService(config?: StripeConnectConfig, prisma?: PrismaClient): StripeConnectService {
  if (!stripeConnectServiceInstance) {
    if (!config) {
      throw new Error("StripeConnectService config required for first initialization");
    }
    if (!prisma) {
      throw new Error("StripeConnectService prisma client required for first initialization");
    }
    stripeConnectServiceInstance = new StripeConnectService(config, prisma);
  }
  return stripeConnectServiceInstance;
}
