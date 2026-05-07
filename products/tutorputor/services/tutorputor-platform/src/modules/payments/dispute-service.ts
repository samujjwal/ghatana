/**
 * Dispute Resolution Service
 *
 * Handles payment disputes and chargebacks.
 * Manages dispute workflow, evidence submission, and resolution.
 *
 * @doc.type service
 * @doc.purpose Payment dispute and chargeback management
 * @doc.layer product
 * @doc.pattern Service
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import Stripe from "stripe";

const logger = createStandaloneLogger({ component: "DisputeService" });

/**
 * Dispute status
 */
export enum DisputeStatus {
  WARNING = "WARNING",
  NEEDS_RESPONSE = "NEEDS_RESPONSE",
  UNDER_REVIEW = "UNDER_REVIEW",
  CHARGE_REFUNDED = "CHARGE_REFUNDED",
  CHARGE_REFUND_PROTECTED = "CHARGE_REFUND_PROTECTED",
  LOST = "LOST",
  WON = "WON",
}

/**
 * Evidence type
 */
export type EvidenceType =
  | "customer_email_address"
  | "customer_name"
  | "customer_purchase_ip"
  | "customer_signature"
  | "duplicate_charge_documentation"
  | "duplicate_charge_explanation"
  | "product_description"
  | "receipt"
  | "refund_policy"
  | "refund_refusal_explanation"
  | "service_date"
  | "shipping_carrier"
  | "shipping_date"
  | "shipping_documentation"
  | "shipping_tracking_number"
  | "uncategorized_file"
  | "uncategorized_text";

/**
 * Dispute evidence
 */
export interface DisputeEvidence {
  customer_email_address?: string;
  customer_name?: string;
  customer_purchase_ip?: string;
  customer_signature?: string;
  duplicate_charge_documentation?: string;
  duplicate_charge_explanation?: string;
  product_description?: string;
  receipt?: string;
  refund_policy?: string;
  refund_refusal_explanation?: string;
  service_date?: string;
  shipping_carrier?: string;
  shipping_date?: string;
  shipping_documentation?: string;
  shipping_tracking_number?: string;
  uncategorized_file?: string;
  uncategorized_text?: string;
}

/**
 * Dispute Service
 */
export class DisputeService {
  private stripe: Stripe;

  constructor(secretKey: string) {
    this.stripe = new Stripe(secretKey);
  }

  /**
   * Get dispute details
   */
  async getDispute(disputeId: string): Promise<{
    id: string;
    amount: number;
    currency: string;
    status: DisputeStatus;
    reason: string;
    dueDate: Date;
    evidence: DisputeEvidence;
  }> {
    try {
      const dispute = await this.stripe.disputes.retrieve(disputeId);

      return {
        id: dispute.id,
        amount: dispute.amount,
        currency: dispute.currency,
        status: this.mapDisputeStatus(dispute.status),
        reason: dispute.reason,
        dueDate: new Date((dispute.evidence_details?.due_by || 0) * 1000),
        evidence: dispute.evidence as DisputeEvidence,
      };
    } catch (error) {
      logger.error(
        { disputeId, error: error instanceof Error ? error.message : String(error) },
        "Failed to get dispute",
      );
      throw error;
    }
  }

  /**
   * List disputes for an account
   */
  async listDisputes(
    accountId: string,
    status?: DisputeStatus,
    limit: number = 20,
  ): Promise<Array<{
    id: string;
    amount: number;
    currency: string;
    status: DisputeStatus;
    reason: string;
    createdAt: Date;
  }>> {
    try {
      const disputes = await this.stripe.disputes.list({
        limit,
      }, {
        stripeAccount: accountId,
      });

      const filtered = status
        ? disputes.data.filter((d) => this.mapDisputeStatus(d.status) === status)
        : disputes.data;

      return filtered.map((dispute) => ({
        id: dispute.id,
        amount: dispute.amount,
        currency: dispute.currency,
        status: this.mapDisputeStatus(dispute.status),
        reason: dispute.reason,
        createdAt: new Date(dispute.created * 1000),
      }));
    } catch (error) {
      logger.error(
        { accountId, error: error instanceof Error ? error.message : String(error) },
        "Failed to list disputes",
      );
      throw error;
    }
  }

  /**
   * Submit evidence for a dispute
   */
  async submitEvidence(
    disputeId: string,
    evidence: DisputeEvidence,
  ): Promise<{ success: boolean; message: string }> {
    logger.info({ disputeId }, "Submitting dispute evidence");

    try {
      const textEvidence: Partial<DisputeEvidence> = {};

      for (const [key, value] of Object.entries(evidence) as Array<
        [keyof DisputeEvidence, string | undefined]
      >) {
        if (typeof value === "string") {
          textEvidence[key] = value;
        }
      }

      // Update dispute with evidence
      await this.stripe.disputes.update(disputeId, {
        evidence: textEvidence,
      });

      logger.info({ disputeId }, "Evidence submitted successfully");

      return {
        success: true,
        message: "Evidence submitted successfully",
      };
    } catch (error) {
      logger.error(
        { disputeId, error: error instanceof Error ? error.message : String(error) },
        "Failed to submit evidence",
      );
      return {
        success: false,
        message: error instanceof Error ? error.message : String(error),
      };
    }
  }

  /**
   * Close a dispute (accept liability)
   */
  async closeDispute(disputeId: string): Promise<void> {
    logger.info({ disputeId }, "Closing dispute");

    try {
      await this.stripe.disputes.close(disputeId);

      logger.info({ disputeId }, "Dispute closed successfully");
    } catch (error) {
      logger.error(
        { disputeId, error: error instanceof Error ? error.message : String(error) },
        "Failed to close dispute",
      );
      throw error;
    }
  }

  /**
   * Get disputes that need attention
   */
  async getDisputesNeedingAttention(accountId: string): Promise<Array<{
    id: string;
    amount: number;
    currency: string;
    reason: string;
    dueDate: Date;
    daysUntilDue: number;
  }>> {
    try {
      const disputes = await this.listDisputes(
        accountId,
        DisputeStatus.NEEDS_RESPONSE,
        100,
      );

      const result: Array<{
        id: string;
        amount: number;
        currency: string;
        reason: string;
        dueDate: Date;
        daysUntilDue: number;
      }> = [];

      const now = new Date();

      for (const dispute of disputes) {
        const disputeDetails = await this.getDispute(dispute.id);
        const daysUntilDue = Math.ceil(
          (disputeDetails.dueDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24),
        );

        result.push({
          id: dispute.id,
          amount: dispute.amount,
          currency: dispute.currency,
          reason: dispute.reason,
          dueDate: disputeDetails.dueDate,
          daysUntilDue,
        });
      }

      return result;
    } catch (error) {
      logger.error(
        { accountId, error: error instanceof Error ? error.message : String(error) },
        "Failed to get disputes needing attention",
      );
      throw error;
    }
  }

  /**
   * Auto-generate evidence for a dispute
   */
  async autoGenerateEvidence(
    disputeId: string,
    paymentIntentId: string,
  ): Promise<DisputeEvidence> {
    logger.info({ disputeId, paymentIntentId }, "Auto-generating dispute evidence");

    try {
      const paymentIntent = await this.stripe.paymentIntents.retrieve(paymentIntentId);

      const evidence: DisputeEvidence = {
        product_description: paymentIntent.description || "Educational service",
      };

      // Add customer email if available.
      if (paymentIntent.receipt_email) {
        evidence.customer_email_address = paymentIntent.receipt_email;
      }

      // Add customer IP if available (from metadata)
      if (paymentIntent.metadata?.customer_ip) {
        evidence.customer_purchase_ip = paymentIntent.metadata.customer_ip;
      }

      logger.info({ disputeId }, "Evidence auto-generated successfully");

      return evidence;
    } catch (error) {
      logger.error(
        { disputeId, paymentIntentId, error: error instanceof Error ? error.message : String(error) },
        "Failed to auto-generate evidence",
      );
      throw error;
    }
  }

  /**
   * Map Stripe dispute status to internal status
   */
  private mapDisputeStatus(status: string): DisputeStatus {
    switch (status) {
      case "warning":
        return DisputeStatus.WARNING;
      case "needs_response":
        return DisputeStatus.NEEDS_RESPONSE;
      case "under_review":
        return DisputeStatus.UNDER_REVIEW;
      case "charge_refunded":
        return DisputeStatus.CHARGE_REFUNDED;
      case "charge_refund_protected":
        return DisputeStatus.CHARGE_REFUND_PROTECTED;
      case "lost":
        return DisputeStatus.LOST;
      case "won":
        return DisputeStatus.WON;
      default:
        return DisputeStatus.WARNING;
    }
  }
}

/**
 * Singleton instance
 */
let disputeServiceInstance: DisputeService | null = null;

export function getDisputeService(secretKey?: string): DisputeService {
  if (!disputeServiceInstance) {
    if (!secretKey) {
      throw new Error("DisputeService secretKey required for first initialization");
    }
    disputeServiceInstance = new DisputeService(secretKey);
  }
  return disputeServiceInstance;
}
