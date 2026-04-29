/**
 * Quality Baseline Workflow Integration
 *
 * Automates quality baseline establishment as part of the content approval workflow.
 * Integrates with ContentStudioService to automatically establish baselines when
 * content is approved for publication.
 *
 * @doc.type class
 * @doc.purpose Automate quality baseline establishment in content approval workflow
 * @doc.layer product
 * @doc.pattern Workflow Integration
 */

import type { PrismaClient } from "@tutorputor/core/db";
import { ContentQualityMonitoringService } from "./ContentQualityMonitoringService.js";
import type { Logger } from "pino";

export interface WorkflowConfig {
  autoEstablishBaseline: boolean;
  baselineThreshold: number; // Minimum quality score to establish baseline
  enableRegressionMonitoring: boolean;
}

export interface ContentApprovalEvent {
  contentId: string;
  contentType: string;
  tenantId: string;
  qualityScore: number;
  approvedBy: string;
  approvedAt: Date;
}

export class QualityBaselineWorkflow {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger,
    private readonly config: Partial<WorkflowConfig> = {},
  ) {}

  private get effectiveConfig(): Required<WorkflowConfig> {
    return {
      autoEstablishBaseline: this.config.autoEstablishBaseline ?? true,
      baselineThreshold: this.config.baselineThreshold ?? 0.8,
      enableRegressionMonitoring: this.config.enableRegressionMonitoring ?? true,
    };
  }

  /**
   * Handle content approval event - automatically establish quality baseline
   */
  async handleContentApproval(event: ContentApprovalEvent): Promise<void> {
    const config = this.effectiveConfig;

    if (!config.autoEstablishBaseline) {
      this.logger.info(
        { contentId: event.contentId },
        "Auto baseline establishment disabled",
      );
      return;
    }

    // Only establish baseline for high-quality content
    if (event.qualityScore < config.baselineThreshold) {
      this.logger.info(
        {
          contentId: event.contentId,
          qualityScore: event.qualityScore,
          threshold: config.baselineThreshold,
        },
        "Content quality below threshold, skipping baseline establishment",
      );
      return;
    }

    try {
      const monitoringService = new ContentQualityMonitoringService(this.prisma);
      
      // Calculate current metrics for the approved content
      const metrics = await this.calculateQualityMetrics(event.contentId);
      
      // Establish baseline
      const baseline = await monitoringService.establishBaseline(
        event.contentId,
        event.contentType,
        metrics,
      );

      this.logger.info(
        {
          contentId: event.contentId,
          contentType: event.contentType,
          baselineMetrics: metrics,
        },
        "Quality baseline established automatically on approval",
      );

      // If regression monitoring is enabled, schedule periodic checks
      if (config.enableRegressionMonitoring) {
        await this.scheduleRegressionMonitoring(event.contentId);
      }
    } catch (error) {
      this.logger.error(
        { error, contentId: event.contentId },
        "Failed to establish quality baseline on approval",
      );
      // Don't throw - baseline establishment failure shouldn't block approval
    }
  }

  /**
   * Calculate quality metrics for content
   */
  private async calculateQualityMetrics(
    contentId: string,
  ): Promise<{ clarity: number; accuracy: number; completeness: number; engagement: number }> {
    // Try to fetch content and calculate metrics
    // This is a simplified implementation - in production, use actual evaluation results
    
    let contentText = "";
    
    // Try to fetch as module content block
    const contentBlock = await this.prisma.moduleContentBlock.findUnique({
      where: { id: contentId },
      select: { payload: true },
    });

    if (contentBlock) {
      contentText = typeof contentBlock.payload === "string" 
        ? contentBlock.payload 
        : JSON.stringify(contentBlock.payload);
    } else {
      // Try to fetch as content asset
      const contentAsset = await this.prisma.contentAsset.findUnique({
        where: { id: contentId },
        select: { title: true, searchableText: true, qualityScore: true },
      });

      if (contentAsset) {
        contentText = `${contentAsset.title} ${contentAsset.searchableText || ""}`;
        
        // If quality score exists, use it as a proxy for metrics
        if (contentAsset.qualityScore) {
          const quality = contentAsset.qualityScore / 100;
          return {
            clarity: quality,
            accuracy: quality,
            completeness: quality,
            engagement: quality * 0.9, // Engagement typically slightly lower
          };
        }
      }
    }

    // Calculate metrics from content text
    return this.calculateMetricsFromText(contentText);
  }

  /**
   * Calculate quality metrics from text (simplified)
   */
  private calculateMetricsFromText(text: string): {
    clarity: number;
    accuracy: number;
    completeness: number;
    engagement: number;
  } {
    const wordCount = text.split(/\s+/).length;
    const sentenceCount = text.split(/[.!?]+/).filter(s => s.trim()).length;
    
    // Clarity: based on sentence length and word variety
    const avgSentenceLength = wordCount / Math.max(sentenceCount, 1);
    const clarity = Math.min(1, 1 - Math.abs(avgSentenceLength - 20) / 40);
    
    // Completeness: based on content length (heuristic)
    const completeness = Math.min(1, wordCount / 100);
    
    // Accuracy: default to 1.0 for approved content (would need fact-checking in production)
    const accuracy = 1.0;
    
    // Engagement: based on structure and variety
    const engagement = Math.min(1, (clarity + completeness) / 2);
    
    return { clarity, accuracy, completeness, engagement };
  }

  /**
   * Schedule regression monitoring for content
   */
  private async scheduleRegressionMonitoring(contentId: string): Promise<void> {
    // In production, this would integrate with a job queue (BullMQ)
    // to schedule periodic quality checks
    this.logger.info(
      { contentId },
      "Regression monitoring scheduled (job queue integration required)",
    );
  }

  /**
   * Run baseline establishment for all approved content without baselines
   */
  async establishBaselinesForApprovedContent(
    tenantId?: string,
  ): Promise<{ processed: number; established: number; failed: number }> {
    const where: Record<string, unknown> = {
      status: "PUBLISHED",
    };

    if (tenantId) {
      where["tenantId"] = tenantId;
    }

    const approvedContent = await this.prisma.contentAsset.findMany({
      where,
      select: {
        id: true,
        assetType: true,
        tenantId: true,
        qualityScore: true,
        updatedAt: true,
      },
    });

    const existingBaselines = await this.prisma.qualityBaseline.findMany({
      select: { contentId: true },
    });

    const existingBaselineIds = new Set(existingBaselines.map(b => b.contentId));
    
    let processed = 0;
    let established = 0;
    let failed = 0;

    for (const content of approvedContent) {
      if (existingBaselineIds.has(content.id)) {
        continue;
      }

      processed++;

      try {
        const event: ContentApprovalEvent = {
          contentId: content.id,
          contentType: content.assetType,
          tenantId: content.tenantId,
          qualityScore: content.qualityScore ?? 0.5,
          approvedBy: "system-baseline-workflow",
          approvedAt: content.updatedAt,
        };

        await this.handleContentApproval(event);
        established++;
      } catch (error) {
        this.logger.error(
          { error, contentId: content.id },
          "Failed to establish baseline for content",
        );
        failed++;
      }
    }

    this.logger.info(
      { processed, established, failed },
      "Baseline establishment batch completed",
    );

    return { processed, established, failed };
  }
}
