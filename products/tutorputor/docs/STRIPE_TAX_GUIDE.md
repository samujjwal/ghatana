# Stripe Tax Integration Guide

**Date:** 2026-04-17
**Status:** Implementation Complete

## Overview

Stripe Tax automatically calculates and collects tax based on customer location, ensuring compliance with tax regulations across different jurisdictions.

## Implementation Details

### Stripe Tax Service

**Location:** `services/tutorputor-platform/src/modules/payments/stripe-tax-service.ts`

The `StripeTaxService` provides:
- Tax calculation based on location
- Tax transaction creation
- Tax rate lookup
- Tax ID registration for sellers
- Tax registration status tracking

## Configuration

### Environment Variables

```bash
# Stripe Configuration
STRIPE_SECRET_KEY=sk_live_your_secret_key
```

### Stripe Dashboard Setup

1. **Enable Stripe Tax**
   - Navigate to Stripe Dashboard → Settings → Tax
   - Enable automatic tax calculation
   - Configure tax registration (if applicable)

2. **Register Tax IDs**
   - Add platform tax IDs (if applicable)
   - Configure tax nexus locations

## Usage

### Calculating Tax

```typescript
import { getStripeTaxService } from "@tutorputor/platform/modules/payments";

const stripeTax = getStripeTaxService(process.env.STRIPE_SECRET_KEY!);

// Calculate tax for a transaction
const result = await stripeTax.calculateTax({
  amount: 10000, // $100.00 in cents
  currency: "usd",
  customerDetails: {
    address: {
      country: "US",
      state: "CA",
      postalCode: "94105",
      city: "San Francisco",
      line1: "123 Market St",
    },
    email: "customer@example.com",
    name: "John Doe",
  },
  productDetails: {
    productCode: "tutoring-session",
    description: "1-hour math tutoring session",
  },
});

console.log("Total with tax:", result.amountTotal);
console.log("Tax amount:", result.amountTax);
console.log("Tax breakdown:", result.taxBreakdown);
```

### Creating Tax Transaction

After payment is processed, create a tax transaction:

```typescript
const taxTransactionId = await stripeTax.createTaxTransaction(
  result.taxId,
  paymentIntentId,
  "order-12345",
);
```

### Registering Seller Tax IDs

```typescript
// Register EU VAT ID
await stripeTax.registerTaxId(
  accountId,
  "DE123456789",
  "eu_vat",
);

// Register US EIN
await stripeTax.registerTaxId(
  accountId,
  "12-3456789",
  "us_ein",
);
```

### Checking Tax Registration Status

```typescript
const status = await stripeTax.getTaxRegistrationStatus(accountId);

if (status.registered) {
  console.log("Tax IDs:", status.taxIds);
}
```

## Tax Calculation Flow

```
Customer enters address
        ↓
Calculate tax based on location
        ↓
Display tax amount to customer
        ↓
Customer confirms payment
        ↓
Create payment intent
        ↓
Create tax transaction
        ↓
Stripe remits tax to authorities
```

## Supported Tax Types

- **Sales Tax**: US state and local sales tax
- **VAT**: EU Value Added Tax
- **GST**: Canada Goods and Services Tax
- **Other**: Various international taxes

## Compliance

### Automatic Compliance

Stripe Tax handles:
- Tax rate updates
- Tax remittance
- Tax reporting
- Registration requirements

### Platform Responsibilities

- Collect customer address
- Display tax amounts
- Maintain tax IDs
- Respond to tax inquiries

## Troubleshooting

### Common Issues

**"Tax calculation failed"**
- Verify customer address is complete
- Check Stripe Tax is enabled
- Verify product details are valid

**"Tax transaction failed"**
- Ensure payment was successful
- Verify tax calculation ID is valid
- Check Stripe dashboard for errors

**"Tax ID registration failed"**
- Verify tax ID format is correct
- Check account is eligible for tax ID registration
- Verify country code is correct

## Next Steps

1. Integrate tax calculation into checkout flow
2. Display tax breakdown to customers
3. Implement tax transaction creation
4. Add seller tax ID registration UI
5. Set up tax reporting
6. Monitor tax compliance status
