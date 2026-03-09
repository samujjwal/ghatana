/**
 * Cost Optimization Service - Generates cost-saving recommendations
 *
 * <p><b>Purpose</b><br>
 * Analyzes cost data and generates actionable recommendations for cost optimization.
 * Applies heuristics and best practices to identify waste and inefficiencies.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const service = new CostOptimizationService(repository);
 * const recommendations = await service.generateRecommendations(
 *   { start: new Date('2024-01'), end: new Date('2024-02') }
 * );
 * console.log(`Found ${recommendations.length} recommendations`);
 * // Sorted by potential savings (highest first)
 * }</pre>
 *
 * <p><b>Recommendation Types</b><br>
 * - Right-sizing: Downgrade oversized instances (20-40% savings)
 * - Reservation: Purchase reserved instances (30-60% savings)
 * - Spot instances: Use for fault-tolerant workloads (70-90% savings)
 * - Cleanup: Remove unused resources (100% savings)
 * - Consolidation: Merge multi-cloud to single provider (10-25% savings)
 *
 * @doc.type class
 * @doc.purpose Generates cost optimization recommendations
 * @doc.layer product
 * @doc.pattern Service
 */

import { CloudCostRepository, DateRange, CloudCostFilters } from '../../repositories/CloudCostRepository';
import { CloudCost } from '../../models/cost/CloudCost.entity';
import { CostRecommendation } from '../../models/cost/CostRecommendation.entity';

/**
 * Recommendation generation options
 */
export interface RecommendationOptions {
  readonly minSavings?: number;
  readonly maxRecommendations?: number;
  readonly includedTypes?: ReadonlyArray<'right-sizing' | 'reservation' | 'spot' | 'cleanup' | 'consolidation'>;
}

/**
 * Recommendation details for creation
 */
export interface RecommendationData {
  readonly title: string;
  readonly description: string;
  readonly savings: number;
  readonly effort: 'LOW' | 'MEDIUM' | 'HIGH';
  readonly implementation: string;
  readonly estimatedMonthsSavings: number;
  readonly resourceIds: ReadonlyArray<string>;
  readonly tags: Record<string, string>;
}

/**
 * CostOptimizationService implementation
 */
export class CostOptimizationService {
  /**
   * Initialize service with repository
   * @param repository Data access layer for costs
   */
  constructor(private readonly repository: CloudCostRepository) {}

  /**
   * Generate recommendations for cost optimization
   * @param period Analysis period
   * @param options Recommendation filtering options
   * @returns Array of recommendations sorted by savings
   */
  async generateRecommendations(
    period: DateRange,
    options?: RecommendationOptions
  ): Promise<CostRecommendation[]> {
    const costs = await this.repository.findByPeriod(period);

    if (costs.length === 0) {
      return [];
    }

    const recommendations: CostRecommendation[] = [];

    // Generate recommendations for enabled types
    const includeTypes = options?.includedTypes || [
      'right-sizing',
      'reservation',
      'spot',
      'cleanup',
      'consolidation',
    ];

    if (includeTypes.includes('right-sizing')) {
      recommendations.push(
        ...this.generateRightSizingRecommendations(costs)
      );
    }

    if (includeTypes.includes('reservation')) {
      recommendations.push(
        ...this.generateReservationRecommendations(costs)
      );
    }

    if (includeTypes.includes('spot')) {
      recommendations.push(
        ...this.generateSpotInstanceRecommendations(costs)
      );
    }

    if (includeTypes.includes('cleanup')) {
      recommendations.push(
        ...this.generateCleanupRecommendations(costs)
      );
    }

    if (includeTypes.includes('consolidation')) {
      recommendations.push(
        ...this.generateConsolidationRecommendations(costs)
      );
    }

    // Filter by minimum savings
    const minSavings = options?.minSavings || 0;
    const filtered = recommendations.filter(r => r.savings >= minSavings);

    // Sort by savings (descending)
    filtered.sort((a, b) => b.savings - a.savings);

    // Limit number of recommendations
    const maxRecommendations = options?.maxRecommendations || 50;

    return filtered.slice(0, maxRecommendations);
  }

  /**
   * Get recommendations by type
   * @param type Recommendation type to retrieve
   * @param period Analysis period
   * @returns Recommendations of specified type
   */
  async getRecommendationsByType(
    type: 'right-sizing' | 'reservation' | 'spot' | 'cleanup' | 'consolidation',
    period: DateRange
  ): Promise<CostRecommendation[]> {
    return this.generateRecommendations(period, { includedTypes: [type] });
  }

  /**
   * Score recommendation impact
   * @param recommendation Recommendation to score
   * @returns Impact score (0-100)
   */
  scoreRecommendation(recommendation: CostRecommendation): number {
    // Score based on savings and implementation effort
    const effortMultiplier =
      recommendation.effort === 'LOW'
        ? 1.0
        : recommendation.effort === 'MEDIUM'
          ? 0.7
          : 0.4;

    const savingsScore = Math.min((recommendation.savings / 1000) * 100, 100);

    return Math.round(savingsScore * effortMultiplier);
  }

  /**
   * Generate right-sizing recommendations
   * Identifies oversized instances that can be downgraded
   */
  private generateRightSizingRecommendations(
    costs: ReadonlyArray<CloudCost>
  ): CostRecommendation[] {
    const recommendations: CostRecommendation[] = [];

    // Group costs by service
    const costsByService = this.groupByService(costs);

    for (const [service, serviceCosts] of Object.entries(costsByService)) {
      if (!['compute', 'database', 'memory'].some(t => service.toLowerCase().includes(t))) {
        continue;
      }

      const totalServiceCost = serviceCosts.reduce((sum, c) => sum + c.cost, 0);
      const avgCost = totalServiceCost / serviceCosts.length;

      // Flag services with high variability as oversized
      const highCostInstances = serviceCosts.filter(c => c.cost > avgCost * 1.5);

      if (highCostInstances.length > 0) {
        const monthlySavings = highCostInstances.reduce(
          (sum, c) => sum + c.cost * 0.25,
          0
        );

        if (monthlySavings > 100) {
          const rec = new CostRecommendation();
          rec.title = `Right-size ${service} instances`;
          rec.description = `Downgrade ${highCostInstances.length} oversized ${service} instances to optimal size. ` +
            `Potential savings: 20-30% on ${service} costs.`;
          rec.savings = monthlySavings;
          rec.annualSavings = monthlySavings * 12;
          rec.effort = 'MEDIUM';
          rec.estimatedMonthsSavings = 12;
          rec.status = 'SUGGESTED';
          rec.resourceIds = highCostInstances
            .map((_, i) => `${service}-instance-${i + 1}`)
            .slice(0, 5);
          rec.implementation = 
            `1. Analyze ${service} instance metrics for the past 30 days\n` +
            `2. Identify instances with consistently low CPU/memory utilization\n` +
            `3. Test downgrade in non-production environment\n` +
            `4. Update ${service} instance types for high-variability instances\n` +
            `5. Monitor performance for 48 hours post-change\n` +
            `6. Adjust if performance degrades`;
          rec.tags = { 'type': 'right-sizing', 'service': service };
          rec.suggestedAt = new Date();
          rec.createdAt = new Date();
          rec.updatedAt = new Date();

          recommendations.push(rec);
        }
      }
    }

    return recommendations;
  }

  /**
   * Generate reservation recommendations
   * Suggests purchasing reserved capacity
   */
  private generateReservationRecommendations(
    costs: ReadonlyArray<CloudCost>
  ): CostRecommendation[] {
    const recommendations: CostRecommendation[] = [];

    // Group costs by provider
    const costsByProvider = this.groupByProvider(costs);

    for (const [provider, providerCosts] of Object.entries(costsByProvider)) {
      const totalProviderCost = providerCosts.reduce((sum, c) => sum + c.cost, 0);
      const monthlyAvg = totalProviderCost / 30;

      // Assume 35% annual discount on reserved capacity
      const monthlyReservationSavings = monthlyAvg * 0.35;

      if (monthlyReservationSavings > 500) {
        const rec = new CostRecommendation();
        rec.title = `Purchase ${provider} reserved capacity`;
        rec.description = `Your ${provider} on-demand costs are ${monthlyAvg.toFixed(0)}/month. ` +
          `Purchasing reserved capacity for stable workloads can save 30-40% annually.`;
        rec.savings = monthlyReservationSavings;
        rec.annualSavings = monthlyReservationSavings * 12;
        rec.effort = 'LOW';
        rec.estimatedMonthsSavings = 12;
        rec.status = 'SUGGESTED';
        rec.resourceIds = [
          `${provider}-compute-reserve`,
          `${provider}-storage-reserve`,
        ];
        rec.implementation = 
          `1. Analyze ${provider} usage patterns for stable workloads\n` +
          `2. Calculate optimal 1-year and 3-year reserved instance mix\n` +
          `3. Purchase reserved instances for compute\n` +
          `4. Purchase reserved storage for stable data\n` +
          `5. Monitor actual vs reserved usage`;
        rec.tags = { 'type': 'reservation', 'provider': provider };
        rec.suggestedAt = new Date();
        rec.createdAt = new Date();
        rec.updatedAt = new Date();

        recommendations.push(rec);
      }
    }

    return recommendations;
  }

  /**
   * Generate spot instance recommendations
   * Suggests using spot/preemptible instances for fault-tolerant workloads
   */
  private generateSpotInstanceRecommendations(
    costs: ReadonlyArray<CloudCost>
  ): CostRecommendation[] {
    const recommendations: CostRecommendation[] = [];

    // Group costs by service
    const costsByService = this.groupByService(costs);

    for (const [service, serviceCosts] of Object.entries(costsByService)) {
      // Batch, queue, and analytics workloads are good candidates for spot
      if (!['batch', 'queue', 'analytics', 'spark', 'hadoop'].some(
        t => service.toLowerCase().includes(t)
      )) {
        continue;
      }

      const totalCost = serviceCosts.reduce((sum, c) => sum + c.cost, 0);
      const monthlySavings = totalCost * 0.65; // 65% savings with spot

      if (monthlySavings > 200) {
        const rec = new CostRecommendation();
        rec.title = `Use spot instances for ${service} workloads`;
        rec.description = `${service} workloads appear fault-tolerant and good candidates for spot/preemptible instances. ` +
          `Potential savings: 60-80% on compute costs.`;
        rec.savings = monthlySavings;
        rec.annualSavings = monthlySavings * 12;
        rec.effort = 'MEDIUM';
        rec.estimatedMonthsSavings = 12;
        rec.status = 'SUGGESTED';
        rec.resourceIds = serviceCosts
          .map((_, i) => `${service}-workload-${i + 1}`)
          .slice(0, 5);
        rec.implementation = 
          `1. Verify ${service} workloads support interruption/preemption\n` +
          `2. Add fault tolerance handling (retry, checkpointing)\n` +
          `3. Configure job queue to accept spot instances for ${service}\n` +
          `4. Implement graceful shutdown on preemption signal\n` +
          `5. Monitor interruption rates and adjust as needed`;
        rec.tags = { 'type': 'spot', 'service': service };
        rec.suggestedAt = new Date();
        rec.createdAt = new Date();
        rec.updatedAt = new Date();

        recommendations.push(rec);
      }
    }

    return recommendations;
  }

  /**
   * Generate cleanup recommendations
   * Identifies unused or low-utilization resources
   */
  private generateCleanupRecommendations(
    costs: ReadonlyArray<CloudCost>
  ): CostRecommendation[] {
    const recommendations: CostRecommendation[] = [];

    // Group costs by service
    const costsByService = this.groupByService(costs);

    for (const [service, serviceCosts] of Object.entries(costsByService)) {
      // Group by provider
      const costsByProvider = this.groupByProvider(serviceCosts);

      for (const [provider, providerCosts] of Object.entries(costsByProvider)) {
        // Check for low-cost orphaned resources
        const orphanedCosts = providerCosts.filter(c => c.cost < 10 && c.cost > 0);

        if (orphanedCosts.length > 5) {
          const totalOrphanCost = orphanedCosts.reduce((sum, c) => sum + c.cost, 0);

          const rec = new CostRecommendation();
          rec.title = `Clean up unused ${provider} ${service} resources`;
          rec.description = `Found ${orphanedCosts.length} small/potentially unused ${service} resources in ${provider}. ` +
            `These may be test resources, failed deployments, or orphaned instances.`;
          rec.savings = totalOrphanCost;
          rec.annualSavings = totalOrphanCost * 12;
          rec.effort = 'HIGH';
          rec.estimatedMonthsSavings = 12;
          rec.status = 'SUGGESTED';
          rec.resourceIds = orphanedCosts
            .map((_, i) => `${provider}-${service}-orphan-${i + 1}`)
            .slice(0, 10);
          rec.implementation = 
            `1. List all ${provider} ${service} resources with costs < $10/month\n` +
            `2. Verify each resource is still in use\n` +
            `3. Check last access/modification timestamps\n` +
            `4. Identify and terminate unused resources\n` +
            `5. Verify no dependencies before deletion\n` +
            `6. Archive usage logs for compliance`;
          rec.tags = { 'type': 'cleanup', 'provider': provider, 'service': service };
          rec.suggestedAt = new Date();
          rec.createdAt = new Date();
          rec.updatedAt = new Date();

          recommendations.push(rec);
        }
      }
    }

    return recommendations;
  }

  /**
   * Generate consolidation recommendations
   * Suggests consolidating multi-cloud to single provider
   */
  private generateConsolidationRecommendations(
    costs: ReadonlyArray<CloudCost>
  ): CostRecommendation[] {
    const recommendations: CostRecommendation[] = [];

    // Group costs by provider
    const costsByProvider = this.groupByProvider(costs);
    const providers = Object.keys(costsByProvider);

    if (providers.length < 2) {
      return [];
    }

    // Calculate total costs per provider
    const providerTotals = Object.entries(costsByProvider).map(([provider, costs]) => ({
      provider,
      total: costs.reduce((sum, c) => sum + c.cost, 0),
    }));

    // Sort by total cost
    providerTotals.sort((a, b) => b.total - a.total);

    // If not dominated by single provider, recommend consolidation
    const topProviderShare = providerTotals[0].total / 
      providerTotals.reduce((sum, p) => sum + p.total, 0);

    if (topProviderShare < 0.7 && providers.length >= 2) {
      const minorProviders = providerTotals.slice(1);
      const monthlySavings = minorProviders.reduce(
        (sum, p) => sum + p.total * 0.15,
        0
      );

      const rec = new CostRecommendation();
      rec.title = `Consolidate to ${providerTotals[0].provider}`;
      rec.description = `Currently using ${providers.length} cloud providers. Consolidating to primary provider ` +
        `(${providerTotals[0].provider}) can reduce administrative overhead and improve volume discounts.`;
      rec.savings = monthlySavings;
      rec.annualSavings = monthlySavings * 12;
      rec.effort = 'HIGH';
      rec.estimatedMonthsSavings = 12;
      rec.status = 'SUGGESTED';
      rec.resourceIds = minorProviders.map(p => `${p.provider}-all-resources`);
      rec.implementation = 
        `1. Perform cost-benefit analysis for consolidation\n` +
        `2. Identify workloads on secondary providers\n` +
        `3. Plan migration strategy (phased approach recommended)\n` +
        `4. Setup equivalent services in primary provider\n` +
        `5. Migrate data and configurations\n` +
        `6. Run parallel environment for 30 days\n` +
        `7. Switch traffic to primary provider\n` +
        `8. Monitor and optimize for 60 days post-migration\n` +
        `9. Decommission secondary provider accounts`;
      rec.tags = { 'type': 'consolidation', 'provider': providerTotals[0].provider };
      rec.suggestedAt = new Date();
      rec.createdAt = new Date();
      rec.updatedAt = new Date();

      recommendations.push(rec);
    }

    return recommendations;
  }

  /**
   * Group costs by service
   */
  private groupByService(
    costs: ReadonlyArray<CloudCost>
  ): Record<string, CloudCost[]> {
    const grouped: Record<string, CloudCost[]> = {};

    for (const cost of costs) {
      if (!grouped[cost.service]) {
        grouped[cost.service] = [];
      }
      grouped[cost.service].push(cost);
    }

    return grouped;
  }

  /**
   * Group costs by provider
   */
  private groupByProvider(
    costs: ReadonlyArray<CloudCost>
  ): Record<string, CloudCost[]> {
    const grouped: Record<string, CloudCost[]> = {};

    for (const cost of costs) {
      if (!grouped[cost.provider]) {
        grouped[cost.provider] = [];
      }
      grouped[cost.provider].push(cost);
    }

    return grouped;
  }
}
