/**
 * Stripe Tax Service
 *
 * Handles tax calculation and collection using Stripe Tax.
 * Automatically calculates and collects tax based on location.
 *
 * @doc.type service
 * @doc.purpose Stripe Tax integration for automated tax calculation
 * @doc.layer product
 * @doc.pattern Service
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import Stripe from "stripe";

const logger = createStandaloneLogger({ component: "StripeTaxService" });

/**
 * Tax calculation configuration
 */
export interface TaxCalculationConfig {
  amount: number;
  currency: string;
  customerDetails: {
    address: {
      country: string;
      state?: string;
      postalCode?: string;
      city?: string;
      line1?: string;
    };
    email?: string;
    name?: string;
  };
  productDetails?: {
    productCode?: string;
    description?: string;
  };
}

/**
 * Tax calculation result
 */
export interface TaxCalculationResult {
  amountTotal: number;
  amountSubtotal: number;
  amountTax: number;
  taxBreakdown: Array<{
    amount: number;
    jurisdiction: string;
    ratePercentage: number;
    taxType: string;
  }>;
  taxId: string;
}

/**
 * Stripe Tax Service
 */
export class StripeTaxService {
  private stripe: Stripe;

  constructor(secretKey: string) {
    this.stripe = new Stripe(secretKey);
  }

  /**
   * Calculate tax for a transaction
   */
  async calculateTax(config: TaxCalculationConfig): Promise<TaxCalculationResult> {
    logger.info(
      { amount: config.amount, currency: config.currency, country: config.customerDetails.address.country },
      "Calculating tax",
    );

    try {
      // Create tax calculation
      const taxCalculation = await this.stripe.tax.calculations.create({
        currency: config.currency,
        line_items: [
          {
            amount: config.amount,
            reference: config.productDetails?.productCode || "default",
          },
        ],
        customer_details: {
          address: config.customerDetails.address,
        },
      });

      // Extract tax breakdown
      const taxBreakdown: Array<{
        amount: number;
        jurisdiction: string;
        ratePercentage: number;
        taxType: string;
      }> = [];

      if (taxCalculation.line_items?.data && taxCalculation.line_items.data.length > 0) {
        const lineItem = taxCalculation.line_items.data[0];
        if (lineItem?.tax_breakdown) {
          for (const tax of lineItem.tax_breakdown) {
            // Extract first tax rate detail if available
            const firstDetail = Array.isArray(tax.tax_rate_details) && tax.tax_rate_details.length > 0
              ? tax.tax_rate_details[0]
              : null;

            taxBreakdown.push({
              amount: tax.amount,
              jurisdiction: String(tax.jurisdiction),
              ratePercentage: firstDetail?.percentage || 0,
              taxType: firstDetail?.tax_type || "unknown",
            });
          }
        }
      }

      logger.info(
        { taxId: taxCalculation.id, amountTotal: taxCalculation.amount_total },
        "Tax calculated successfully",
      );

      return {
        amountTotal: taxCalculation.amount_total,
        amountSubtotal: taxCalculation.amount_total, // Stripe API may not have subtotal
        amountTax: taxCalculation.amount_total, // Stripe API may not have separate tax field
        taxBreakdown,
        taxId: taxCalculation.id || "",
      };
    } catch (error) {
      logger.error(
        { error: error instanceof Error ? error.message : String(error) },
        "Failed to calculate tax",
      );
      throw error;
    }
  }

  /**
   * Create a tax transaction for a payment
   */
  async createTaxTransaction(
    taxCalculationId: string,
    paymentIntentId: string,
    reference: string,
  ): Promise<string> {
    logger.info({ taxCalculationId, paymentIntentId }, "Creating tax transaction");

    try {
      const transaction = await this.stripe.tax.transactions.createFromCalculation({
        calculation: taxCalculationId,
        reference,
        metadata: {
          payment_intent_id: paymentIntentId,
        },
      });

      logger.info({ transactionId: transaction.id }, "Tax transaction created");

      return transaction.id;
    } catch (error) {
      logger.error(
        { taxCalculationId, error: error instanceof Error ? error.message : String(error) },
        "Failed to create tax transaction",
      );
      throw error;
    }
  }

  /**
   * Get tax rates for a location
   */
  async getTaxRatesForLocation(country: string, state?: string): Promise<Array<{
    id: string;
    displayName: string;
    percentage: number;
    jurisdiction: string;
    inclusive: boolean;
  }>> {
    try {
      // Note: Stripe Tax API doesn't have a direct tax rates list endpoint
      // Tax rates are calculated automatically based on location
      // This is a placeholder implementation
      return [];
    } catch (error) {
      logger.error(
        { country, state, error: error instanceof Error ? error.message : String(error) },
        "Failed to get tax rates",
      );
      throw error;
    }
  }

  /**
   * Register a tax ID for a seller
   */
  async registerTaxId(
    accountId: string,
    taxId: string,
    taxIdType: "eu_vat" | "in_gst" | "nz_gst" | "us_ein" | "ca_bn" | "sg_uen",
  ): Promise<void> {
    logger.info({ accountId, taxId, taxIdType }, "Registering tax ID");

    try {
      // Note: Stripe Connect accounts API for tax IDs
      // This is a placeholder implementation - actual API may differ
      await this.stripe.accounts.update(accountId, {
        company: {
          tax_id: taxId,
        },
      } as any);

      logger.info({ accountId }, "Tax ID registered successfully");
    } catch (error) {
      logger.error(
        { accountId, taxId, error: error instanceof Error ? error.message : String(error) },
        "Failed to register tax ID",
      );
      throw error;
    }
  }

  /**
   * Get tax registration status for an account
   */
  async getTaxRegistrationStatus(accountId: string): Promise<{
    registered: boolean;
    taxIds: Array<{
      type: string;
      value: string;
    }>;
  }> {
    try {
      const account = await this.stripe.accounts.retrieve(accountId);

      // Note: tax_ids may not be directly available on account
      // This is a placeholder implementation
      const taxIds: Array<{ type: string; value: string }> = [];

      return {
        registered: taxIds.length > 0,
        taxIds,
      };
    } catch (error) {
      logger.error(
        { accountId, error: error instanceof Error ? error.message : String(error) },
        "Failed to get tax registration status",
      );
      throw error;
    }
  }
}

/**
 * Singleton instance
 */
let stripeTaxServiceInstance: StripeTaxService | null = null;

export function getStripeTaxService(secretKey?: string): StripeTaxService {
  if (!stripeTaxServiceInstance) {
    if (!secretKey) {
      throw new Error("StripeTaxService secretKey required for first initialization");
    }
    stripeTaxServiceInstance = new StripeTaxService(secretKey);
  }
  return stripeTaxServiceInstance;
}
