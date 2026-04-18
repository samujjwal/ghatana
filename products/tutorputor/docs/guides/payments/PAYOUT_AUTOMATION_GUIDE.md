# Payout Automation Guide

**Date:** 2026-04-17
**Status:** Implementation Complete

## Overview

The payout automation service manages automated payouts to marketplace sellers, configurable by schedule and minimum balance requirements.

## Implementation Details

### Payout Service

**Location:** `services/tutorputor-platform/src/modules/payments/payout-service.ts`

The `PayoutService` provides:
- Manual payout creation
- Account balance checking
- Payout schedule configuration
- Payout history tracking
- Payout cancellation
- Automatic batch payout processing

## Configuration

### Environment Variables

```bash
# Stripe Configuration
STRIPE_SECRET_KEY=sk_live_your_secret_key

# Payout Configuration
PAYOUT_MINIMUM_AMOUNT=1000  // $10.00 minimum
PAYOUT_SCHEDULE_INTERVAL=weekly
PAYOUT_SCHEDULE_WEEKLY_ANCHOR=friday
```

## Usage

### Creating a Manual Payout

```typescript
import { getPayoutService } from "@tutorputor/platform/modules/payments";

const payoutService = getPayoutService(process.env.STRIPE_SECRET_KEY!);

// Create payout
const result = await payoutService.createPayout({
  accountId: "acct_1234567890",
  amount: 5000, // $50.00 in cents
  currency: "usd",
  description: "Weekly payout",
  metadata: {
    source: "manual",
  },
});

console.log("Payout ID:", result.payoutId);
console.log("Status:", result.status);
```

### Checking Account Balance

```typescript
const balance = await payoutService.getAccountBalance("acct_1234567890");

console.log("Available:", balance.available);
console.log("Pending:", balance.pending);
console.log("Currency:", balance.currency);
```

### Configuring Payout Schedule

```typescript
// Configure weekly payouts on Fridays
await payoutService.configurePayoutSchedule("acct_1234567890", {
  interval: "weekly",
  weeklyAnchor: 5, // Friday
  minimumAmount: 1000, // $10.00 minimum
});

// Configure monthly payouts on the 15th
await payoutService.configurePayoutSchedule("acct_1234567890", {
  interval: "monthly",
  monthlyAnchor: 15,
  minimumAmount: 1000,
});
```

### Getting Payout History

```typescript
const history = await payoutService.getPayoutHistory("acct_1234567890", 20);

history.forEach((payout) => {
  console.log(`Payout ${payout.id}: $${payout.amount / 100} ${payout.currency}`);
  console.log(`Status: ${payout.status}`);
  console.log(`Arrival: ${payout.arrivalDate}`);
});
```

### Processing Automatic Payouts

```typescript
// Get all enabled seller accounts
const accounts = await prisma.stripeAccount.findMany({
  where: {
    status: "ENABLED",
    payoutsEnabled: true,
  },
});

// Process automatic payouts
const result = await payoutService.processAutomaticPayouts(
  accounts.map((a) => a.accountId),
  1000, // $10.00 minimum
);

console.log("Processed:", result.processed);
console.log("Failed:", result.failed);
console.log("Skipped:", result.skipped);
```

### Cancelling a Payout

```typescript
await payoutService.cancelPayout("po_1234567890", "acct_1234567890");
```

## Payout Status Flow

```
PENDING → IN_TRANSIT → PAID
           ↓
         FAILED
```

- **PENDING**: Payout created, awaiting processing
- **IN_TRANSIT**: Payout in transit to bank account
- **PAID**: Payout successfully completed
- **FAILED**: Payout failed (insufficient funds, bank error)
- **CANCELLED**: Payout cancelled by seller

## Schedule Configuration

### Daily Payouts

```typescript
{
  interval: "daily",
  minimumAmount: 1000,
}
```

### Weekly Payouts

```typescript
{
  interval: "weekly",
  weeklyAnchor: 5, // 1=Monday, 5=Friday
  minimumAmount: 1000,
}
```

### Monthly Payouts

```typescript
{
  interval: "monthly",
  monthlyAnchor: 15, // Day of month (1-31)
  minimumAmount: 1000,
}
```

## Automatic Payout Processing

### Cron Job Setup

```bash
# Run daily at 2 AM UTC
0 2 * * * npm run payouts:process
```

### Processing Logic

1. Retrieve all enabled seller accounts
2. Check available balance for each account
3. Create payout if balance >= minimum
4. Track processed, failed, and skipped counts
5. Log results for monitoring

## Monitoring

### Metrics to Track

- **Payout success rate**: Percentage of successful payouts
- **Payout latency**: Time from creation to arrival
- **Balance accumulation**: Average balance before payout
- **Failed payout reasons**: Common failure causes

### Alerts

- **High failure rate**: > 5% payout failures
- **Large balance**: Accounts with balance > $10,000
- **Payout delay**: Payouts not arriving within expected timeframe

## Security Considerations

### Access Control

- Only admins can trigger manual payouts
- Sellers can only cancel their own pending payouts
- Automatic payouts require system-level permissions

### Fraud Prevention

- Monitor for unusual payout patterns
- Flag accounts with high failure rates
- Require additional verification for large payouts

## Troubleshooting

### Common Issues

**"Insufficient funds"**
- Account balance is too low
- Check pending transactions
- Wait for funds to become available

**"Bank account error"**
- Seller's bank account is invalid
- Ask seller to update bank account
- Check for bank-specific issues

**"Payout failed"**
- Check Stripe dashboard for error details
- Verify account is enabled for payouts
- Check for Stripe account restrictions

## Next Steps

1. Implement database operations for payout tracking
2. Set up cron job for automatic payouts
3. Add payout notification system
4. Implement payout analytics dashboard
5. Add seller payout preferences
6. Set up monitoring and alerting
