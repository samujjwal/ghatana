/**
 * GraphQL Query Resolver - Anomaly Detection
 *
 * <p><b>Purpose</b><br>
 * Implements GraphQL query resolvers for security anomaly detection,
 * threat intelligence, and risk assessment queries.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const resolver = new AnomalyQueryResolver(
 *   anomalyService, riskScoringService, threatIntelService
 * );
 * 
 * const anomalies = await resolver.anomalies({
 *   tenantId: 'tenant-1',
 *   startDate: new Date('2024-01-01'),
 *   endDate: new Date('2024-01-31'),
 *   severity: 'CRITICAL'
 * });
 * }</pre>
 *
 * <p><b>Queries Implemented</b><br>
 * - anomalies: Get anomalies in time range with filtering
 * - anomalyById: Get single anomaly details
 * - criticalAnomalies: Get high/critical severity anomalies
 * - anomaliesByUser: Get anomalies for specific user
 * - threatIntelligence: Get threat intelligence data
 * - threatIntelligencePanel: Get threat summary panel
 * - indicators: Get indicators of compromise
 * - anomalyBaseline: Get baseline for system
 * - anomalyStatistics: Get aggregated statistics
 *
 * @doc.type class
 * @doc.purpose GraphQL query resolver for anomaly detection
 * @doc.layer product
 * @doc.pattern Resolver
 */

import { AnomalyDetectionService } from '../../services/anomaly/AnomalyDetectionService';
import { RiskScoringService } from '../../services/anomaly/RiskScoringService';
import { ThreatIntelligenceService } from '../../services/anomaly/ThreatIntelligenceService';
import { Logger } from '@nestjs/common';

/**
 * Query arguments for fetching anomalies
 */
export interface AnomalyQueryArgs {
  readonly tenantId: string;
  readonly startDate: Date;
  readonly endDate: Date;
  readonly severity?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  readonly status?: 'NEW' | 'INVESTIGATING' | 'CONFIRMED_THREAT' | 'FALSE_POSITIVE' | 'RESOLVED';
  readonly type?: string;
}

/**
 * Query arguments for critical anomalies
 */
export interface CriticalAnomalyArgs {
  readonly tenantId: string;
  readonly limit?: number;
}

/**
 * Query arguments for user anomalies
 */
export interface UserAnomalyArgs {
  readonly tenantId: string;
  readonly userId: string;
}

/**
 * Query arguments for threat intelligence
 */
export interface ThreatIntelArgs {
  readonly tenantId: string;
  readonly threatType?: string;
}

/**
 * Query arguments for indicators
 */
export interface IndicatorQueryArgs {
  readonly tenantId: string;
  readonly indicatorType?: string;
}

/**
 * Query arguments for baseline
 */
export interface BaselineQueryArgs {
  readonly tenantId: string;
  readonly system: string;
}

/**
 * Query arguments for statistics
 */
export interface StatisticsQueryArgs {
  readonly tenantId: string;
  readonly timeRange: 'LAST_HOUR' | 'LAST_DAY' | 'LAST_WEEK' | 'LAST_MONTH';
}

/**
 * Resolver class for anomaly detection GraphQL queries
 */
export class AnomalyQueryResolver {
  private readonly logger = new Logger(AnomalyQueryResolver.name);

  constructor(
    private readonly anomalyService: AnomalyDetectionService,
    private readonly riskScoringService: RiskScoringService,
    private readonly threatIntelService: ThreatIntelligenceService,
  ) {}

  /**
   * Get all anomalies for a tenant in specified time range.
   * Supports filtering by severity, status, and type.
   *
   * @param args - Query arguments including tenant, date range, and filters
   * @returns Array of matching anomalies
   */
  async anomalies(args: AnomalyQueryArgs): Promise<unknown[]> {
    this.logger.debug(
      `Fetching anomalies for tenant ${args.tenantId} from ${args.startDate} to ${args.endDate}`,
    );

    try {
      let anomalies = await this.anomalyService.getAnomalies(
        args.tenantId,
        args.startDate,
        args.endDate,
      );

      // Apply filters
      if (args.severity) {
        anomalies = anomalies.filter((a: unknown) => a.severity === args.severity);
      }
      if (args.status) {
        anomalies = anomalies.filter((a: unknown) => a.status === args.status);
      }
      if (args.type) {
        anomalies = anomalies.filter((a: unknown) => a.type === args.type);
      }

      return anomalies;
    } catch (error) {
      this.logger.error(
        `Error fetching anomalies for tenant ${args.tenantId}`,
        error,
      );
      throw error;
    }
  }

  /**
   * Get single anomaly by ID.
   *
   * @param id - Anomaly ID
   * @returns Anomaly details or null if not found
   */
  async anomalyById(id: string): Promise<unknown | null> {
    this.logger.debug(`Fetching anomaly ${id}`);

    try {
      return await this.anomalyService.getAnomalyById(id);
    } catch (error) {
      this.logger.error(`Error fetching anomaly ${id}`, error);
      throw error;
    }
  }

  /**
   * Get critical and high severity anomalies.
   * Useful for dashboard/alerts.
   *
   * @param args - Query arguments
   * @returns Array of critical anomalies
   */
  async criticalAnomalies(args: CriticalAnomalyArgs): Promise<unknown[]> {
    this.logger.debug(
      `Fetching critical anomalies for tenant ${args.tenantId}, limit: ${args.limit}`,
    );

    try {
      const anomalies = await this.anomalyService.getCriticalAnomalies(
        args.tenantId,
      );
      return args.limit ? anomalies.slice(0, args.limit) : anomalies;
    } catch (error) {
      this.logger.error(
        `Error fetching critical anomalies for tenant ${args.tenantId}`,
        error,
      );
      throw error;
    }
  }

  /**
   * Get anomalies associated with specific user.
   * Useful for user-centric investigation.
   *
   * @param args - Query arguments
   * @returns Array of user's anomalies
   */
  async anomaliesByUser(args: UserAnomalyArgs): Promise<unknown[]> {
    this.logger.debug(
      `Fetching anomalies for user ${args.userId} in tenant ${args.tenantId}`,
    );

    try {
      return await this.anomalyService.getAnomaliesByUser(
        args.tenantId,
        args.userId,
      );
    } catch (error) {
      this.logger.error(
        `Error fetching anomalies for user ${args.userId}`,
        error,
      );
      throw error;
    }
  }

  /**
   * Get threat intelligence records.
   * Supports filtering by threat type.
   *
   * @param args - Query arguments
   * @returns Array of threat intelligence records
   */
  async threatIntelligence(args: ThreatIntelArgs): Promise<unknown[]> {
    this.logger.debug(
      `Fetching threat intelligence for tenant ${args.tenantId}, type: ${args.threatType}`,
    );

    try {
      if (args.threatType) {
        return await this.threatIntelService.getThreatIntelligenceByType(
          args.tenantId,
          args.threatType,
        );
      }
      return await this.threatIntelService.getThreatIntelligence(args.tenantId);
    } catch (error) {
      this.logger.error(
        `Error fetching threat intelligence for tenant ${args.tenantId}`,
        error,
      );
      throw error;
    }
  }

  /**
   * Get threat intelligence panel summary.
   * Useful for dashboard display of current threats.
   *
   * @param tenantId - Tenant identifier
   * @returns Panel summary data
   */
  async threatIntelligencePanel(tenantId: string): Promise<unknown> {
    this.logger.debug(`Fetching threat intel panel for tenant ${tenantId}`);

    try {
      return await this.threatIntelService.getThreatIntelligencePanel(
        tenantId,
      );
    } catch (error) {
      this.logger.error(
        `Error fetching threat intel panel for tenant ${tenantId}`,
        error,
      );
      throw error;
    }
  }

  /**
   * Get indicators of compromise.
   * Supports filtering by indicator type.
   *
   * @param args - Query arguments
   * @returns Array of indicators
   */
  async indicators(args: IndicatorQueryArgs): Promise<unknown[]> {
    this.logger.debug(
      `Fetching indicators for tenant ${args.tenantId}, type: ${args.indicatorType}`,
    );

    try {
      if (args.indicatorType) {
        return await this.threatIntelService.getIndicatorsByType(
          args.tenantId,
          args.indicatorType,
        );
      }
      return await this.threatIntelService.getIndicators(args.tenantId);
    } catch (error) {
      this.logger.error(
        `Error fetching indicators for tenant ${args.tenantId}`,
        error,
      );
      throw error;
    }
  }

  /**
   * Get anomaly baseline for a system.
   * Baselines define normal behavior for anomaly detection.
   *
   * @param args - Query arguments
   * @returns Baseline configuration
   */
  async anomalyBaseline(args: BaselineQueryArgs): Promise<unknown | null> {
    this.logger.debug(
      `Fetching baseline for tenant ${args.tenantId}, system ${args.system}`,
    );

    try {
      return await this.anomalyService.getAnomalyBaseline(
        args.tenantId,
        args.system,
      );
    } catch (error) {
      this.logger.error(
        `Error fetching baseline for system ${args.system}`,
        error,
      );
      throw error;
    }
  }

  /**
   * Get all anomaly baselines for a tenant.
   *
   * @param tenantId - Tenant identifier
   * @returns Array of baselines
   */
  async anomalyBaselines(tenantId: string): Promise<unknown[]> {
    this.logger.debug(`Fetching all baselines for tenant ${tenantId}`);

    try {
      return await this.anomalyService.getAnomalyBaselines(tenantId);
    } catch (error) {
      this.logger.error(
        `Error fetching baselines for tenant ${tenantId}`,
        error,
      );
      throw error;
    }
  }

  /**
   * Get anomaly statistics for time range.
   * Provides aggregated metrics for reporting.
   *
   * @param args - Query arguments
   * @returns Statistics summary
   */
  async anomalyStatistics(args: StatisticsQueryArgs): Promise<unknown> {
    this.logger.debug(
      `Fetching statistics for tenant ${args.tenantId}, range: ${args.timeRange}`,
    );

    try {
      const startDate = this.getStartDateFromTimeRange(args.timeRange);
      const endDate = new Date();

      const anomalies = await this.anomalyService.getAnomalies(
        args.tenantId,
        startDate,
        endDate,
      );

      return {
        totalAnomalies: anomalies.length,
        criticalCount: anomalies.filter((a: unknown) => a.severity === 'CRITICAL')
          .length,
        highCount: anomalies.filter((a: unknown) => a.severity === 'HIGH').length,
        mediumCount: anomalies.filter((a: unknown) => a.severity === 'MEDIUM')
          .length,
        lowCount: anomalies.filter((a: unknown) => a.severity === 'LOW').length,
      };
    } catch (error) {
      this.logger.error(
        `Error fetching statistics for tenant ${args.tenantId}`,
        error,
      );
      throw error;
    }
  }

  /**
   * Helper to convert time range to start date.
   */
  private getStartDateFromTimeRange(
    timeRange: 'LAST_HOUR' | 'LAST_DAY' | 'LAST_WEEK' | 'LAST_MONTH',
  ): Date {
    const now = new Date();
    switch (timeRange) {
      case 'LAST_HOUR':
        return new Date(now.getTime() - 60 * 60 * 1000);
      case 'LAST_DAY':
        return new Date(now.getTime() - 24 * 60 * 60 * 1000);
      case 'LAST_WEEK':
        return new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
      case 'LAST_MONTH':
        return new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
      default:
        return new Date(now.getTime() - 24 * 60 * 60 * 1000);
    }
  }
}
