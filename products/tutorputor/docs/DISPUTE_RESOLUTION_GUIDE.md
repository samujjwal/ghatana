# Dispute Resolution Guide

**Date:** 2026-04-17
**Status:** Implementation Complete

## Overview

The dispute resolution service manages payment disputes and chargebacks, providing tools for evidence submission, status tracking, and automated response generation.

## Implementation Details

### Dispute Service

**Location:** `services/tutorputor-platform/src/modules/payments/dispute-service.ts`

The `DisputeService` provides:
- Dispute retrieval and listing
- Evidence submission
- Dispute closure
- Attention-needed dispute tracking
- Automatic evidence generation

## Configuration

### Environment Variables

```bash
# Stripe Configuration
STRIPE_SECRET_KEY=sk_live_your_secret_key

# Dispute Configuration
DISPUTE_AUTO_RESPONSE_ENABLED=true
DISPUTE_RESPONSE_DEADLINE_DAYS=7
```

## Usage

### Retrieving Dispute Details

```typescript
import { getDisputeService } from "@tutorputor/platform/modules/payments";

const disputeService = getDisputeService(process.env.STRIPE_SECRET_KEY!);

const dispute = await disputeService.getDispute("dp_1234567890");

console.log("Amount:", dispute.amount);
console.log("Status:", dispute.status);
console.log("Reason:", dispute.reason);
console.log("Due date:", dispute.dueDate);
```

### Listing Disputes

```typescript
// List all disputes for an account
const disputes = await disputeService.listDisputes("acct_1234567890");

// List only disputes needing response
const needsResponse = await disputeService.listDisputes(
  "acct_1234567890",
  DisputeStatus.NEEDS_RESPONSE,
);

disputes.forEach((dispute) => {
  console.log(`Dispute ${dispute.id}: $${dispute.amount / 100}`);
  console.log(`Reason: ${dispute.reason}`);
  console.log(`Status: ${dispute.status}`);
});
```

### Submitting Evidence

```typescript
// Prepare evidence
const evidence: DisputeEvidence = {
  product_description: "1-hour math tutoring session",
  customer_email_address: "customer@example.com",
  customer_purchase_ip: "192.168.1.1",
  receipt: receiptFile,
};

// Submit evidence
const result = await disputeService.submitEvidence("dp_1234567890", evidence);

if (result.success) {
  console.log("Evidence submitted successfully");
} else {
  console.error("Failed to submit evidence:", result.message);
}
```

### Auto-Generating Evidence

```typescript
// Automatically generate evidence from payment intent
const evidence = await disputeService.autoGenerateEvidence(
  "dp_1234567890",
  "pi_1234567890",
);

// Review and add additional evidence if needed
evidence.refund_policy = "Full refund within 24 hours of session";

await disputeService.submitEvidence("dp_1234567890", evidence);
```

### Getting Disputes Needing Attention

```typescript
const urgent = await disputeService.getDisputesNeedingAttention("acct_1234567890");

urgent.forEach((dispute) => {
  console.log(`Dispute ${dispute.id} due in ${dispute.daysUntilDue} days`);
  console.log(`Amount: $${dispute.amount / 100}`);
  console.log(`Reason: ${dispute.reason}`);
});
```

### Closing a Dispute

```typescript
// Accept liability and close dispute
await disputeService.closeDispute("dp_1234567890");
```

## Dispute Status Flow

```
WARNING → NEEDS_RESPONSE → UNDER_REVIEW → WON/LOST
                      ↓
              CHARGE_REFUNDED
```

- **WARNING**: Dispute initiated, not yet requiring response
- **NEEDS_RESPONSE**: Evidence must be submitted by due date
- **UNDER_REVIEW**: Evidence submitted, under review by card issuer
- **WON**: Dispute resolved in favor of merchant
- **LOST**: Dispute resolved in favor of customer
- **CHARGE_REFUNDED**: Charge refunded (evidence not submitted)
- **CHARGE_REFUND_PROTECTED**: Charge protected by Stripe

## Dispute Reasons

Common dispute reasons:
- **duplicate**: Customer claims duplicate charge
- **product_not_received**: Customer claims product/service not received
- **product_unacceptable**: Customer claims product/service unsatisfactory
- **credit_not_processed**: Customer claims refund not processed
- **general**: General dispute

## Evidence Types

### Text Evidence
- `customer_email_address`: Customer's email
- `customer_name`: Customer's name
- `customer_purchase_ip`: Customer's IP address
- `customer_signature`: Customer's signature
- `duplicate_charge_explanation`: Explanation for duplicate charge
- `product_description`: Product/service description
- `refund_policy`: Refund policy text
- `refund_refusal_explanation`: Explanation for refund refusal
- `service_date`: Date of service delivery
- `shipping_tracking_number`: Shipping tracking number

### File Evidence
- `receipt`: Receipt or invoice
- `duplicate_charge_documentation`: Documentation for duplicate charge
- `shipping_documentation`: Shipping documentation
- `uncategorized_file`: Any supporting file

## Automatic Response Workflow

### Cron Job Setup

```bash
# Check for new disputes every hour
0 * * * * npm run disputes:check
```

### Processing Logic

1. Retrieve disputes needing response
2. Auto-generate evidence from payment details
3. Submit evidence automatically
4. Notify seller of dispute
5. Track response success/failure

## Best Practices

### Evidence Submission

- **Submit early**: Submit evidence as soon as possible
- **Be thorough**: Provide all relevant evidence
- **Be specific**: Include dates, amounts, and details
- **Be honest**: Only submit accurate information

### Dispute Prevention

- **Clear descriptions**: Use clear payment descriptions
- **Refund policy**: Have a clear refund policy
- **Customer communication**: Maintain good customer communication
- **Documentation**: Keep records of all transactions

### Monitoring

- **Track dispute rate**: Monitor dispute frequency
- **Analyze reasons**: Identify common dispute reasons
- **Review evidence**: Evaluate evidence effectiveness
- **Update processes**: Improve based on learnings

## Security Considerations

### Data Privacy

- Protect customer information in evidence
- Only share necessary information
- Comply with data protection regulations

### Access Control

- Only authorized users can submit evidence
- Audit trail for all dispute actions
- Secure file uploads

## Troubleshooting

### Common Issues

**"Evidence submission failed"**
- Check file size limits (max 16MB)
- Verify file format is supported
- Check evidence field requirements

**"Dispute not found"**
- Verify dispute ID is correct
- Check account has access to dispute
- Verify dispute hasn't been closed

**"Auto-generation failed"**
- Verify payment intent exists
- Check payment intent has required metadata
- Verify Stripe account access

## Next Steps

1. Implement database operations for dispute tracking
2. Set up cron job for automatic response
3. Add dispute notification system
4. Implement dispute analytics dashboard
5. Add seller dispute management UI
6. Set up monitoring and alerting
7. Create dispute prevention guidelines
