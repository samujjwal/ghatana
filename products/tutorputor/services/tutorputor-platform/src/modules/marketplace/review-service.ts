/**
 * Marketplace Review Service
 *
 * Provides review workflow and governance for kernel marketplace.
 * Ensures quality and security of published kernels.
 *
 * @doc.type service
 * @doc.purpose Marketplace review and governance
 * @doc.layer product
 * @doc.pattern Service
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import type { PrismaClient } from "@tutorputor/core/db";

const logger = createStandaloneLogger({ component: "MarketplaceReviewService" });

/**
 * Review status
 */
export enum ReviewStatus {
  PENDING = "PENDING",
  IN_REVIEW = "IN_REVIEW",
  APPROVED = "APPROVED",
  REJECTED = "REJECTED",
  CHANGES_REQUESTED = "CHANGES_REQUESTED",
}

/**
 * Review priority
 */
export enum ReviewPriority {
  LOW = "LOW",
  MEDIUM = "MEDIUM",
  HIGH = "HIGH",
  URGENT = "URGENT",
}

/**
 * Review criteria
 */
export interface ReviewCriteria {
  codeQuality: number; // 1-5
  documentation: number; // 1-5
  security: number; // 1-5
  performance: number; // 1-5
  compliance: number; // 1-5
}

/**
 * Review result
 */
export interface ReviewResult {
  approved: boolean;
  criteria: ReviewCriteria;
  comments: string;
  requestedChanges?: string[];
}

/**
 * Review assignment
 */
export interface ReviewAssignment {
  kernelId: string;
  reviewerId: string;
  assignedAt: Date;
  status: ReviewStatus;
}

/**
 * Marketplace Review Service
 */
export class MarketplaceReviewService {
  constructor(private readonly prisma: PrismaClient) {}

  /**
   * Submit a kernel for review
   */
  async submitForReview(kernelId: string, submitterId: string): Promise<void> {
    logger.info({ kernelId, submitterId }, "Kernel submitted for review");

    // Check if kernel exists and is signed
    const kernel = await this.prisma.kernelPlugin.findUnique({
      where: { id: kernelId },
    });

    if (!kernel) {
      throw new Error("Kernel not found");
    }

    // Note: signature field may not exist in generated types yet
    // This will work after Prisma migration
    const kernelWithSignature = kernel as { signature?: string | null };
    if (!kernelWithSignature.signature) {
      throw new Error("Kernel must be signed before submission");
    }

    // Create review record
    await this.prisma.marketplaceReview.create({
      data: {
        kernelId: kernel.id,
        submitterId,
        status: ReviewStatus.PENDING,
        priority: ReviewPriority.MEDIUM,
        submittedAt: new Date(),
      },
    });
  }

  /**
   * Assign a reviewer to a kernel
   */
  async assignReviewer(kernelId: string, reviewerId: string): Promise<void> {
    logger.info({ kernelId, reviewerId }, "Reviewer assigned to kernel");

    const review = await this.prisma.marketplaceReview.findFirst({
      where: { kernelId },
    });

    if (!review) {
      throw new Error("Review not found");
    }

    await this.prisma.marketplaceReview.update({
      where: { id: review.id },
      data: {
        reviewerId,
        status: ReviewStatus.IN_REVIEW,
        assignedAt: new Date(),
      },
    });
  }

  /**
   * Submit a review
   */
  async submitReview(kernelId: string, reviewerId: string, result: ReviewResult): Promise<void> {
    logger.info({ kernelId, reviewerId, approved: result.approved }, "Review submitted");

    const review = await this.prisma.marketplaceReview.findFirst({
      where: { kernelId },
    });

    if (!review) {
      throw new Error("Review not found");
    }

    if (review.reviewerId !== reviewerId) {
      throw new Error("Reviewer not assigned to this review");
    }

    await this.prisma.marketplaceReview.update({
      where: { id: review.id },
      data: {
        status: result.approved ? ReviewStatus.APPROVED : ReviewStatus.REJECTED,
        completedAt: new Date(),
        criteria: JSON.stringify(result.criteria),
        comments: result.comments,
        requestedChanges: result.requestedChanges ? JSON.stringify(result.requestedChanges) : null,
      },
    });

    // If approved, publish kernel
    if (result.approved) {
      await this.publishKernel(kernelId);
    }
  }

  /**
   * Get review status for a kernel
   */
  async getReviewStatus(kernelId: string): Promise<{
    status: ReviewStatus;
    submittedAt: Date;
    reviewerId?: string;
    assignedAt?: Date;
    completedAt?: Date;
    criteria?: ReviewCriteria;
    comments?: string;
  }> {
    const review = await this.prisma.marketplaceReview.findFirst({
      where: { kernelId },
    });

    if (!review) {
      throw new Error("Review not found");
    }

    return {
      status: review.status as ReviewStatus,
      submittedAt: review.submittedAt,
      reviewerId: review.reviewerId ?? undefined,
      assignedAt: review.assignedAt ?? undefined,
      completedAt: review.completedAt ?? undefined,
      criteria: review.criteria ? JSON.parse(review.criteria) as ReviewCriteria : undefined,
      comments: review.comments ?? undefined,
    };
  }

  /**
   * Get review queue for a reviewer
   */
  async getReviewQueue(reviewerId: string): Promise<Array<{
    kernelId: string;
    kernelName: string;
    submittedAt: Date;
    priority: ReviewPriority;
  }>> {
    const reviews = await this.prisma.marketplaceReview.findMany({
      where: {
        reviewerId,
        status: {
          in: [ReviewStatus.PENDING, ReviewStatus.IN_REVIEW],
        },
      },
      include: {
        kernel: true,
      },
      orderBy: [
        { priority: "desc" },
        { submittedAt: "asc" },
      ],
    });

    return reviews.map((review) => ({
      kernelId: review.kernelId,
      kernelName: review.kernel.name,
      submittedAt: review.submittedAt,
      priority: review.priority as ReviewPriority,
    }));
  }

  /**
   * Publish a kernel to marketplace
   */
  private async publishKernel(kernelId: string): Promise<void> {
    logger.info({ kernelId }, "Publishing kernel to marketplace");

    await this.prisma.kernelPlugin.update({
      where: { id: kernelId },
      data: {
        publishedAt: new Date(),
      } as any, // publishedAt field may not exist in generated types yet
    });
  }

  /**
   * Get review history for a kernel
   */
  async getReviewHistory(kernelId: string): Promise<Array<{
    reviewerId: string;
    status: ReviewStatus;
    submittedAt: Date;
    completedAt?: Date;
    comments?: string;
  }>> {
    const reviews = await this.prisma.marketplaceReview.findMany({
      where: { kernelId },
      orderBy: { submittedAt: "desc" },
    });

    return reviews.map((review: any) => ({
      reviewerId: review.reviewerId || "unknown",
      status: review.status as ReviewStatus,
      submittedAt: review.submittedAt,
      completedAt: review.completedAt || undefined,
      comments: review.comments || undefined,
    }));
  }
}

/**
 * Prisma schema additions needed:
 *
 * model MarketplaceReview {
 *   id              String   @id @default(cuid())
 *   kernelId        String
 *   submitterId     String
 *   reviewerId      String?
 *   status          ReviewStatus
 *   priority        ReviewPriority @default(MEDIUM)
 *   submittedAt     DateTime @default(now())
 *   assignedAt      DateTime?
 *   completedAt     DateTime?
 *   criteria        String?  // JSON
 *   comments        String?
 *   requestedChanges String? // JSON
 *   kernel          KernelPlugin @relation(fields: [kernelId], references: [id])
 *   
 *   @@index([kernelId])
 *   @@index([reviewerId])
 *   @@index([status])
 * }
 *
 * enum ReviewStatus {
 *   PENDING
 *   IN_REVIEW
 *   APPROVED
 *   REJECTED
 *   CHANGES_REQUESTED
 * }
 *
 * enum ReviewPriority {
 *   LOW
 *   MEDIUM
 *   HIGH
 *   URGENT
 * }
 */
