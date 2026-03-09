/**
 * GraphQL Query Resolver - Cost Analysis
 *
 * <p><b>Purpose</b><br>
 * Implements GraphQL query resolvers for cost analysis, recommendations,
 * forecasting, and alert management.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const resolver = new CostQueryResolver(analysisService, optimizationService,
 *   forecastingService, notificationService);
 * 
 * const analysis = await resolver.costAnalysis({
 *   startDate: new Date('2024-01-01'),
 *   endDate: new Date('2024-01-31'),
 *   providers: ['AWS']
 * });
 * }</pre>
 *
 * <p><b>Queries Implemented</b><br>
 * - costAnalysis: Get cost breakdown for period
 * - costRecommendations: Get optimization recommendations
 * - costForecast: Get cost predictions
 * - budgetPlan: Get budget allocation plan
 * - costAlerts: Get recent alerts
 * - alertRules: Get all configured rules
 * - alertRule: Get specific rule
 *
 * @doc.type class
 * @doc.purpose GraphQL query resolver for cost operations
 * @doc.layer product
 * @doc.pattern Resolver
 */

import { CostAnalysisService } from '../../services/cost/CostAnalysisService';
import { CostOptimizationService } from '../../services/cost/CostOptimizationService';
import { CostForecastingService } from '../../services/cost/CostForecastingService';
import { CostNotificationService, AlertRule } from '../../services/cost/CostNotificationService';
import { CloudCostRepository, DateRange } from '../../repositories/CloudCostRepository';

/**
 * GraphQL query arguments for cost analysis
 */
export interface CostAnalysisArgs {
  readonly startDate: Date;
  readonly endDate: Date;
  readonly providers?: ReadonlyArray<string>;
  readonly services?: ReadonlyArray<string>;
  readonly tags?: Record<string, string>;
}

/**
 * GraphQL query arguments for recommendations
 */
export interface CostRecommendationsArgs {
  readonly limit?: number;
  readonly minSavings?: number;
  readonly types?: ReadonlyArray<string>;
}

/**
 * GraphQL query arguments for forecast
 */
export interface CostForecastArgs {
  readonly monthsToForecast: number;
  readonly bufferPercent?: number;
}

/**
 * CostQueryResolver implementation
 */
export class CostQueryResolver {
  /**
   * Initialize resolver with service dependencies
   *
   * @param analysisService Service for cost analysis
   * @param optimizationService Service for recommendations
   * @param forecastingService Service for cost forecasting
   * @param notificationService Service for alerts
   * @param repository Repository for cost data access
   */
  constructor(
    private readonly analysisService: CostAnalysisService,
    private readonly optimizationService: CostOptimizationService,
    private readonly forecastingService: CostForecastingService,
    private readonly notificationService: CostNotificationService,
    private readonly repository: CloudCostRepository
  ) {}

  /**
   * Resolve costAnalysis query
   * Returns cost breakdown and analysis for specified period
   *
   * @param args Query arguments
   * @returns Cost analysis with aggregations and anomalies
   */
  async costAnalysis(args: CostAnalysisArgs) {
    const filters = args.providers || args.services || args.tags
      ? {
          providers: args.providers,
          services: args.services,
          tags: args.tags,
        }
      : undefined;

    const period: DateRange = {
      start: args.startDate,
      end: args.endDate,
    };

    return this.analysisService.analyzeCosts(period, filters);
  }

  /**
   * Resolve costRecommendations query
   * Returns cost saving recommendations sorted by impact
   *
   * @param args Query arguments
   * @returns Array of cost recommendations
   */
  async costRecommendations(args: CostRecommendationsArgs) {
    const limit = args.limit || 20;
    const minSavings = args.minSavings || 0;
    const typeFilters = args.types as
      | ReadonlyArray<'right-sizing' | 'reservation' | 'spot' | 'cleanup' | 'consolidation'>
      | undefined;

    // Get last 30 days for recommendation analysis
    const endDate = new Date();
    const startDate = new Date();
    startDate.setDate(startDate.getDate() - 30);

    const period: DateRange = {
      start: startDate,
      end: endDate,
    };

    const recommendations = await this.optimizationService.generateRecommendations(
      period,
      {
        minSavings,
        includedTypes: typeFilters,
      }
    );

    return recommendations.slice(0, limit);
  }

  /**
   * Resolve costForecast query
   * Returns cost forecast with confidence intervals
   *
   * @param args Query arguments
   * @returns Cost forecast with monthly projections
   */
  async costForecast(args: CostForecastArgs) {
    const monthsToForecast = Math.min(Math.max(args.monthsToForecast, 1), 36);
    return this.forecastingService.forecastCosts(monthsToForecast);
  }

  /**
   * Resolve budgetPlan query
   * Returns budget allocation plan based on forecast
   *
   * @param args Query arguments
   * @returns Budget plan with risk assessment
   */
  async budgetPlan(args: CostForecastArgs) {
    const monthsToForecast = Math.min(Math.max(args.monthsToForecast, 1), 36);
    const bufferPercent = args.bufferPercent || 10;

    return this.forecastingService.generateBudgetPlan(
      monthsToForecast,
      bufferPercent
    );
  }

  /**
   * Resolve costAlerts query
   * Returns recent cost alerts
   *
   * @param limit Maximum alerts to return
   * @returns Array of recent cost alerts
   */
  async costAlerts(limit?: number): Promise<ReadonlyArray<unknown>> {
    const recentAlerts = this.notificationService.getRecentAlerts(limit || 10);
    return Array.from(recentAlerts);
  }

  /**
   * Resolve alertRules query
   * Returns all configured alert rules
   *
   * @returns Array of alert rules
   */
  async alertRules(): Promise<ReadonlyArray<AlertRule>> {
    const rulesMap = this.notificationService.getAlertRules();
    return Array.from(rulesMap.values());
  }

  /**
   * Resolve alertRule query
   * Returns specific alert rule by ID
   *
   * @param id Rule ID
   * @returns Alert rule or null if not found
   */
  async alertRule(id: string): Promise<AlertRule | null> {
    const rulesMap = this.notificationService.getAlertRules();
    return rulesMap.get(id) || null;
  }
}
