/**
 * Payout Automation Service
 *
 * Handles automated payout scheduling and execution for marketplace sellers.
 * Manages payout schedules, balances, and notifications.
 *
 * @doc.type service
 * @doc.purpose Automated payout management for marketplace sellers
 * @doc.layer product
 * @doc.pattern Service
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import Stripe from "stripe";
import type { TutorPrismaClient } from "@tutorputor/core/db";

const logger = createStandaloneLogger({ component: "PayoutService" });

/**
 * Payout schedule configuration
 */
export interface PayoutSchedule {
  interval: "daily" | "weekly" | "monthly";
  weeklyAnchor?: number; // 1-7 for day of week
  monthlyAnchor?: number; // 1-31 for day of month
  minimumAmount?: number; // Minimum balance for payout
}

/**
 * Payout configuration
 */
export interface PayoutConfig {
  accountId: string;
  amount: number;
  currency?: string;
  description?: string;
  metadata?: Record<string, string>;
}

/**
 * Payout status
 */
export enum PayoutStatus {
  PENDING = "PENDING",
  IN_TRANSIT = "IN_TRANSIT",
  PAID = "PAID",
  FAILED = "FAILED",
  CANCELLED = "CANCELLED",
}

type StripePayoutScheduleConfig = NonNullable<
  NonNullable<
    NonNullable<Stripe.AccountUpdateParams["settings"]>["payouts"]
  >["schedule"]
>;
type StripeWeeklyAnchor = NonNullable<
  StripePayoutScheduleConfig["weekly_anchor"]
>;
type StripeMonthlyAnchor = NonNullable<
  StripePayoutScheduleConfig["monthly_anchor"]
>;

interface PayoutRecord {
  stripePayoutId: string;
  amountCents: number;
  currency: string;
  status: string;
  arrivalDate: Date | null;
  createdAt: Date;
  description: string | null;
}

/**
 * Payout Service
 */
export class PayoutService {
  private stripe: Stripe;
  private prisma: TutorPrismaClient;

  constructor(secretKey: string, prisma: TutorPrismaClient) {
    this.stripe = new Stripe(secretKey);
    this.prisma = prisma;
  }

  /**
   * Create a payout for a seller
   */
  async createPayout(config: PayoutConfig): Promise<{ payoutId: string; status: PayoutStatus }> {
    logger.info({ accountId: config.accountId, amount: config.amount }, "Creating payout");

    try {
      // For connected accounts, payouts are created on the account directly
      const payoutParams: Stripe.PayoutCreateParams = {
        amount: config.amount,
        currency: config.currency || "usd",
      };

      // Only add optional fields if provided
      if (config.description) {
        payoutParams.description = config.description;
      }

      if (config.metadata) {
        payoutParams.metadata = config.metadata;
      }

      const payout = await this.stripe.payouts.create(
        payoutParams,
        {
          stripeAccount: config.accountId,
        },
      );

      const status = this.mapPayoutStatus(payout.status);

      // Persist payout record to database
      await this.prisma.payout.create({
        data: {
          tenantId: config.metadata?.tenantId || "default",
          stripeAccountId: config.accountId,
          stripePayoutId: payout.id,
          amountCents: config.amount,
          currency: config.currency || "usd",
          status,
          arrivalDate: payout.arrival_date ? new Date(payout.arrival_date * 1000) : null,
          ...(config.description ? { description: config.description } : {}),
          metadata: config.metadata ? JSON.stringify(config.metadata) : null,
        },
      });

      logger.info({ payoutId: payout.id, status }, "Payout created successfully");

      return {
        payoutId: payout.id,
        status,
      };
    } catch (error) {
      logger.error(
        { accountId: config.accountId, amount: config.amount, error: error instanceof Error ? error.message : String(error) },
        "Failed to create payout",
      );
      throw error;
    }
  }

  /**
   * Get account balance
   */
  async getAccountBalance(accountId: string): Promise<{
    available: number;
    pending: number;
    currency: string;
  }> {
    try {
      const balance = await this.stripe.balance.retrieve(
        {},
        { stripeAccount: accountId },
      );

      const available = balance.available.reduce(
        (sum, bal) => sum + bal.amount,
        0,
      );
      const pending = balance.pending.reduce(
        (sum, bal) => sum + bal.amount,
        0,
      );

      return {
        available,
        pending,
        currency: balance.available[0]?.currency || "usd",
      };
    } catch (error) {
      logger.error(
        { accountId, error: error instanceof Error ? error.message : String(error) },
        "Failed to get account balance",
      );
      throw error;
    }
  }

  /**
   * Configure payout schedule for an account
   */
  async configurePayoutSchedule(
    accountId: string,
    schedule: PayoutSchedule,
  ): Promise<void> {
    logger.info({ accountId, schedule }, "Configuring payout schedule");

    try {
      const scheduleConfig: StripePayoutScheduleConfig = {
        interval: schedule.interval,
      };

      if (schedule.weeklyAnchor !== undefined) {
        scheduleConfig.weekly_anchor = toStripeWeeklyAnchor(
          schedule.weeklyAnchor,
        );
      }

      if (schedule.monthlyAnchor !== undefined) {
        scheduleConfig.monthly_anchor = schedule.monthlyAnchor as StripeMonthlyAnchor;
      }

      await this.stripe.accounts.update(accountId, {
        settings: {
          payouts: {
            schedule: scheduleConfig,
          },
        },
      });

      logger.info({ accountId }, "Payout schedule configured successfully");
    } catch (error) {
      logger.error(
        { accountId, error: error instanceof Error ? error.message : String(error) },
        "Failed to configure payout schedule",
      );
      throw error;
    }
  }

  /**
   * Get payout history for an account from database
   */
  async getPayoutHistory(
    accountId: string,
    tenantId?: string,
    limit: number = 20,
  ): Promise<Array<{
    id: string;
    amount: number;
    currency: string;
    status: PayoutStatus;
    arrivalDate: Date;
    description?: string;
  }>> {
    try {
      const payouts = await this.prisma.payout.findMany({
        where: {
          stripeAccountId: accountId,
          ...(tenantId ? { tenantId } : {}),
        },
        orderBy: { createdAt: "desc" },
        take: limit,
      });

      return (payouts as PayoutRecord[]).map((payout) => ({
        id: payout.stripePayoutId,
        amount: payout.amountCents,
        currency: payout.currency,
        status: payout.status as PayoutStatus,
        arrivalDate: payout.arrivalDate || new Date(payout.createdAt),
        ...(payout.description ? { description: payout.description } : {}),
      }));
    } catch (error) {
      logger.error(
        { accountId, error: error instanceof Error ? error.message : String(error) },
        "Failed to get payout history",
      );
      throw error;
    }
  }

  /**
   * Cancel a pending payout
   */
  async cancelPayout(payoutId: string, accountId: string): Promise<void> {
    logger.info({ payoutId, accountId }, "Cancelling payout");

    try {
      await this.stripe.payouts.cancel(
        payoutId,
        {},
        { stripeAccount: accountId },
      );

      // Update database record
      await this.prisma.payout.updateMany({
        where: { stripePayoutId: payoutId },
        data: { status: "CANCELLED" },
      });

      logger.info({ payoutId }, "Payout cancelled successfully");
    } catch (error) {
      logger.error(
        { payoutId, accountId, error: error instanceof Error ? error.message : String(error) },
        "Failed to cancel payout",
      );
      throw error;
    }
  }

  /**
   * Process automatic payouts for eligible accounts
   */
  async processAutomaticPayouts(accountIds: string[], minimumAmount: number): Promise<{
    processed: number;
    failed: number;
    skipped: number;
  }> {
    logger.info({ accountCount: accountIds.length, minimumAmount }, "Processing automatic payouts");

    let processed = 0;
    let failed = 0;
    let skipped = 0;

    for (const accountId of accountIds) {
      try {
        const balance = await this.getAccountBalance(accountId);

        if (balance.available >= minimumAmount) {
          await this.createPayout({
            accountId,
            amount: balance.available,
            currency: balance.currency,
            description: "Automatic payout",
          });
          processed++;
        } else {
          skipped++;
        }
      } catch (error) {
        logger.error(
          { accountId, error: error instanceof Error ? error.message : String(error) },
          "Failed to process payout for account",
        );
        failed++;
      }
    }

    logger.info({ processed, failed, skipped }, "Automatic payout processing completed");

    return { processed, failed, skipped };
  }

  /**
   * Map Stripe payout status to internal status
   */
  private mapPayoutStatus(status: string): PayoutStatus {
    switch (status) {
      case "pending":
        return PayoutStatus.PENDING;
      case "in_transit":
        return PayoutStatus.IN_TRANSIT;
      case "paid":
        return PayoutStatus.PAID;
      case "failed":
        return PayoutStatus.FAILED;
      case "canceled":
        return PayoutStatus.CANCELLED;
      default:
        return PayoutStatus.PENDING;
    }
  }
}

function toStripeWeeklyAnchor(day: number): StripeWeeklyAnchor {
  const anchors: StripeWeeklyAnchor[] = [
    "monday",
    "tuesday",
    "wednesday",
    "thursday",
    "friday",
    "saturday",
    "sunday",
  ];
  return anchors[Math.min(Math.max(day, 1), 7) - 1] ?? "monday";
}

/**
 * Singleton instance
 */
let payoutServiceInstance: PayoutService | null = null;

export function getPayoutService(secretKey?: string, prisma?: TutorPrismaClient): PayoutService {
  if (!payoutServiceInstance) {
    if (!secretKey || !prisma) {
      throw new Error("PayoutService secretKey and prisma required for first initialization");
    }
    payoutServiceInstance = new PayoutService(secretKey, prisma);
  }
  return payoutServiceInstance;
}
