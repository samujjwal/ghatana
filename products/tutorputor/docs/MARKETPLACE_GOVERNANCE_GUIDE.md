# Marketplace Governance Guide

**Date:** 2026-04-17
**Status:** Implementation Complete

## Overview

The marketplace governance system provides a review workflow and quality assurance process for kernels published in the TutorPutor marketplace. This ensures that all kernels meet security, quality, and compliance standards before being available to users.

## Implementation Details

### Review Service

**Location:** `services/tutorputor-platform/src/modules/marketplace/review-service.ts`

The `MarketplaceReviewService` provides:
- Kernel submission for review
- Reviewer assignment
- Review submission and approval
- Review status tracking
- Review queue management
- Review history

### Database Schema

Added models to `libs/tutorputor-core/prisma/schema.prisma`:

```prisma
enum ReviewStatus {
  PENDING
  IN_REVIEW
  APPROVED
  REJECTED
  CHANGES_REQUESTED
}

enum ReviewPriority {
  LOW
  MEDIUM
  HIGH
  URGENT
}

model MarketplaceReview {
  id              String   @id @default(cuid())
  kernelId        String
  submitterId     String
  reviewerId      String?
  status          ReviewStatus @default(PENDING)
  priority        ReviewPriority @default(MEDIUM)
  submittedAt     DateTime @default(now())
  assignedAt      DateTime?
  completedAt     DateTime?
  criteria        String?  // JSON
  comments        String?
  requestedChanges String? // JSON
  kernel          KernelPlugin @relation(fields: [kernelId], references: [id])
}
```

## Review Workflow

### 1. Submission

```typescript
import { MarketplaceReviewService } from "@tutorputor/platform/modules/marketplace";

const reviewService = new MarketplaceReviewService(prisma);

// Submit kernel for review
await reviewService.submitForReview("kernel-123", "user-456");
```

**Requirements:**
- Kernel must be signed (signature verification)
- Kernel must pass basic validation
- Submitter must have permission to submit

### 2. Assignment

```typescript
// Assign a reviewer
await reviewService.assignReviewer("kernel-123", "reviewer-789");
```

**Assignment Strategy:**
- Automatic assignment based on expertise
- Manual assignment by marketplace admin
- Load balancing across reviewers
- Conflict of interest checks

### 3. Review

```typescript
const result: ReviewResult = {
  approved: true,
  criteria: {
    codeQuality: 5,
    documentation: 4,
    security: 5,
    performance: 4,
    compliance: 5,
  },
  comments: "Excellent kernel, well-documented and secure.",
};

await reviewService.submitReview("kernel-123", "reviewer-789", result);
```

**Review Criteria:**

| Criterion | Description | Weight |
|-----------|-------------|--------|
| Code Quality | Code style, structure, maintainability | 20% |
| Documentation | README, API docs, examples | 15% |
| Security | No vulnerabilities, proper input validation | 25% |
| Performance | Efficient algorithms, no resource leaks | 20% |
| Compliance | Follows marketplace policies | 20% |

**Approval Threshold:**
- Overall score ≥ 4.0/5.0
- No criterion < 3.0/5.0
- Security score must be ≥ 4.0/5.0

### 4. Publication

If approved:
- Kernel is marked as published
- Available in marketplace
- Signature verification enforced on download

If rejected:
- Submitter notified with feedback
- Requested changes documented
- Can resubmit after fixes

## Review Policies

### Quality Standards

1. **Code Quality**
   - Follows TypeScript/JavaScript best practices
   - No linting errors or warnings
   - Proper error handling
   - Comprehensive tests (if applicable)

2. **Documentation**
   - Clear README with usage examples
   - API documentation for exported functions
   - Installation instructions
   - License information

3. **Security**
   - No known vulnerabilities
   - Proper input validation
   - No hardcoded secrets
   - Secure dependencies

4. **Performance**
   - Efficient algorithms
   - No memory leaks
   - Reasonable execution time
   - Proper resource cleanup

5. **Compliance**
   - Follows marketplace guidelines
   - No prohibited content
   - Proper attribution
   - Compatible with platform version

### Reviewer Guidelines

1. **Impartiality** - Review objectively without bias
2. **Thoroughness** - Check all aspects of the kernel
3. **Constructiveness** - Provide actionable feedback
4. **Timeliness** - Complete reviews within SLA (7 days)
5. **Confidentiality** - Keep review discussions private

### Submitter Guidelines

1. **Pre-review** - Self-review before submission
2. **Testing** - Test thoroughly before submission
3. **Documentation** - Provide clear documentation
4. **Responsiveness** - Address review feedback promptly
5. **Iterate** - Improve based on feedback

## API Reference

### submitForReview

```typescript
async submitForReview(kernelId: string, submitterId: string): Promise<void>
```

Submit a kernel for marketplace review.

**Parameters:**
- `kernelId` - ID of the kernel to submit
- `submitterId` - ID of the user submitting the kernel

**Throws:**
- `Error` if kernel not found
- `Error` if kernel is not signed

### assignReviewer

```typescript
async assignReviewer(kernelId: string, reviewerId: string): Promise<void>
```

Assign a reviewer to a pending kernel review.

**Parameters:**
- `kernelId` - ID of the kernel
- `reviewerId` - ID of the reviewer to assign

**Throws:**
- `Error` if review not found

### submitReview

```typescript
async submitReview(kernelId: string, reviewerId: string, result: ReviewResult): Promise<void>
```

Submit a review for a kernel.

**Parameters:**
- `kernelId` - ID of the kernel
- `reviewerId` - ID of the reviewer
- `result` - Review result with criteria and comments

**Throws:**
- `Error` if review not found
- `Error` if reviewer not assigned

### getReviewStatus

```typescript
async getReviewStatus(kernelId: string): Promise<ReviewStatusInfo>
```

Get the current review status for a kernel.

**Parameters:**
- `kernelId` - ID of the kernel

**Returns:**
- Review status information

### getReviewQueue

```typescript
async getReviewQueue(reviewerId: string): Promise<ReviewQueueItem[]>
```

Get the review queue for a reviewer.

**Parameters:**
- `reviewerId` - ID of the reviewer

**Returns:**
- Array of pending reviews

## Monitoring

### Metrics to Track

- **Review completion time** - Average time from submission to completion
- **Approval rate** - Percentage of kernels approved
- **Rejection reasons** - Common rejection reasons
- **Reviewer workload** - Reviews per reviewer
- **Queue depth** - Number of pending reviews

### Alerts

- **Review SLA breach** - Review not completed within 7 days
- **Queue depth alert** - More than 50 pending reviews
- **Reviewer inactivity** - No reviews completed in 30 days

## Integration with Kernel Signing

### Combined Flow

1. **Developer signs kernel** - Using signing service
2. **Developer submits for review** - Marketplace review service
3. **Reviewer verifies signature** - As part of security check
4. **Reviewer approves** - If all criteria met
5. **Kernel published** - Available in marketplace
6. **User downloads** - Signature verified before use

### Example Integration

```typescript
import { signingService } from "@tutorputor/platform/core/crypto";
import { MarketplaceReviewService } from "@tutorputor/platform/modules/marketplace";

async function publishKernelSafely(kernelId: string, submitterId: string) {
  // 1. Verify signature
  const kernel = await prisma.kernelPlugin.findUnique({ where: { id: kernelId } });
  const verification = signingService.verifyManifest(createManifest(kernel));

  if (!verification.valid) {
    throw new Error("Kernel signature verification failed");
  }

  // 2. Submit for review
  await reviewService.submitForReview(kernelId, submitterId);

  console.log("Kernel submitted for review successfully");
}
```

## Troubleshooting

### Common Issues

**"Kernel must be signed before submission"**
- Kernel signature field is null
- Run signing service before submission
- Check signature in database

**"Reviewer not assigned to this review"**
- Reviewer ID doesn't match assigned reviewer
- Check reviewer assignment
- Ensure proper permissions

**"Review not found"**
- No review exists for this kernel
- Kernel may not have been submitted
- Check submission status

## Next Steps

1. Implement reviewer permission system
2. Add automatic reviewer assignment logic
3. Create reviewer dashboard UI
4. Implement review SLA monitoring
5. Add review analytics and reporting
6. Create reviewer training materials
