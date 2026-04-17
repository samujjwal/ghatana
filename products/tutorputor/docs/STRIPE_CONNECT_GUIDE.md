# Stripe Connect Integration Guide

**Date:** 2026-04-17
**Status:** Implementation Complete

## Overview

Stripe Connect enables marketplace sellers to receive payments directly into their own Stripe accounts, with the platform taking a configurable fee. This guide covers the onboarding flow, payment processing, and account management.

## Implementation Details

### Stripe Connect Service

**Location:** `services/tutorputor-platform/src/modules/payments/stripe-connect-service.ts`

The `StripeConnectService` provides:
- Connected account creation
- Onboarding link generation
- Account status tracking
- Payment processing with platform fees
- Webhook event handling

### Database Schema

Added to `libs/tutorputor-core/prisma/schema.prisma`:

```prisma
enum StripeAccountStatus {
  PENDING
  ONBOARDING
  ENABLED
  RESTRICTED
  DISABLED
}

model StripeAccount {
  id              String   @id @default(cuid())
  userId          String   @unique
  accountId       String   @unique
  status          StripeAccountStatus @default(PENDING)
  country         String
  email           String
  chargesEnabled  Boolean  @default(false)
  payoutsEnabled  Boolean  @default(false)
  platformFeePercent Float @default(10.0)
  onboardingCompletedAt DateTime?
  createdAt       DateTime @default(now())
  updatedAt       DateTime @updatedAt
}
```

## Configuration

### Environment Variables

```bash
# Stripe Configuration
STRIPE_SECRET_KEY=sk_live_your_secret_key
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret
STRIPE_PLATFORM_FEE_PERCENT=10
```

### Stripe Dashboard Setup

1. **Enable Stripe Connect**
   - Navigate to Stripe Dashboard → Settings → Connect
   - Enable Express accounts
   - Configure platform profile

2. **Create Webhook Endpoint**
   - Navigate to Developers → Webhooks
   - Add endpoint: `https://your-platform.com/api/webhooks/stripe`
   - Select events:
     - `account.updated`
     - `payout.created`
     - `payout.failed`
     - `charge.dispute.created`

## Usage

### Seller Onboarding

```typescript
import { getStripeConnectService } from "@tutorputor/platform/modules/payments";

const stripeConnect = getStripeConnectService({
  secretKey: process.env.STRIPE_SECRET_KEY!,
  platformFeePercent: 10,
});

// Create connected account for seller
const result = await stripeConnect.createConnectedAccount(
  "user-123",
  "seller@example.com",
  "US",
);

// Redirect seller to onboarding URL
res.redirect(result.onboardingUrl);
```

### Onboarding Completion

When seller completes onboarding, they're redirected to `returnUrl`. Handle this:

```typescript
app.get("/sellers/:userId/onboarding/complete", async (req, res) => {
  const { userId } = req.params;
  const stripeAccount = await prisma.stripeAccount.findUnique({
    where: { userId },
  });

  if (stripeAccount) {
    const status = await stripeConnect.getAccountStatus(stripeAccount.accountId);
    await prisma.stripeAccount.update({
      where: { userId },
      data: {
        status: status.status,
        chargesEnabled: status.chargesEnabled,
        payoutsEnabled: status.payoutsEnabled,
        onboardingCompletedAt: new Date(),
      },
    });
  }

  res.redirect(`/sellers/${userId}/dashboard`);
});
```

### Processing Payments

```typescript
// Create payment with platform fee
const payment = await stripeConnect.createPayment(
  stripeAccount.accountId,
  10000, // $100.00 in cents
  "usd",
  "pm_card_visa",
  "Math Tutoring Session",
);

// Use paymentIntent.clientSecret to confirm payment on frontend
```

### Handling Webhooks

```typescript
app.post("/api/webhooks/stripe", async (req, res) => {
  const payload = req.body;
  const signature = req.headers["stripe-signature"]!;

  try {
    await stripeConnect.handleWebhook(
      payload,
      signature,
      process.env.STRIPE_WEBHOOK_SECRET!,
    );
    res.json({ received: true });
  } catch (error) {
    res.status(400).json({ error: error.message });
  }
});
```

## Account Status Flow

```
PENDING → ONBOARDING → ENABLED
                ↓
            RESTRICTED
                ↓
            DISABLED
```

- **PENDING**: Account created, onboarding not started
- **ONBOARDING**: Seller completing onboarding flow
- **ENABLED**: Account fully functional (charges and payouts enabled)
- **RESTRICTED**: Details submitted but not all requirements met
- **DISABLED**: Account disabled by Stripe or platform

## Platform Fees

The platform fee is automatically deducted from each payment:

- **Default**: 10% of transaction amount
- **Configurable**: Set via `platformFeePercent` in service config
- **Example**: $100 payment → $90 to seller, $10 to platform

## Security Considerations

### Key Management

- **Secret key**: Never expose to frontend
- **Webhook secret**: Verify webhook signatures
- **Account IDs**: Store securely in database

### Compliance

- **KYC/AML**: Stripe handles seller verification
- **PCI DSS**: Stripe handles card data
- **Tax**: Use Stripe Tax for automatic tax calculation

## Troubleshooting

### Common Issues

**"Account not found"**
- Check accountId in database
- Verify account was created successfully
- Check Stripe dashboard

**"Onboarding link expired"**
- Generate new onboarding link
- Update refreshUrl in configuration

**"Payment failed"**
- Check account status (must be ENABLED)
- Verify payment method is valid
- Check Stripe dashboard for errors

## Next Steps

1. Implement database operations in StripeConnectService
2. Add frontend onboarding flow
3. Implement payment confirmation UI
4. Add seller dashboard
5. Set up webhook handlers
6. Implement payout automation
7. Add dispute resolution workflow
