/**
 * GraphQL Query and Mutation resolvers for anomaly detection API.
 *
 * <p><b>Purpose</b><br>
 * Provides GraphQL query resolution for:
 * - Retrieving anomalies with complex filtering
 * - Querying threat intelligence
 * - Getting risk assessments
 * - Triggering mutations (acknowledge, resolve, respond)
 *
 * <p><b>Resolver Patterns</b><br>
 * - All resolvers are async functions returning Promises
 * - Errors are caught and returned as GraphQL errors
 * - All database queries use repository layer for data access
 * - Metrics tracked for each resolver invocation
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // In GraphQL server setup
 * import { AnomalyQueryResolver } from "./AnomalyQueryResolver";
 *
 * const resolvers = {
 *   Query: {
 *     getAnomaly: AnomalyQueryResolver.getAnomaly,
 *     getRecentAnomalies: AnomalyQueryResolver.getRecentAnomalies,
 *     // ... other resolvers
 *   },
 *   Mutation: {
 *     acknowledgeAnomaly: AnomalyQueryResolver.acknowledgeAnomaly,
 *     resolveAnomaly: AnomalyQueryResolver.resolveAnomaly,
 *     // ... other mutations
 *   },
 * };
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose GraphQL query and mutation resolution
 * @doc.layer product
 * @doc.pattern Resolver
 */

import { SecurityAnomaly } from "../models/anomaly/SecurityAnomaly.entity";
import { ThreatIntelligence } from "../models/anomaly/ThreatIntelligence.entity";
import { AnomalyBaseline } from "../models/anomaly/AnomalyBaseline.entity";
import { SecurityAnomalyRepository } from "../repositories/SecurityAnomalyRepository";
import { AnomalyDetectionService } from "../services/anomaly/AnomalyDetectionService";
import { ThreatIntelligenceService } from "../services/anomaly/ThreatIntelligenceService";
import { AutomatedResponseService } from "../services/anomaly/AutomatedResponseService";
import { MetricsCollector } from "../observability/MetricsCollector";

/**
 * GraphQL resolver arguments.
 */
export interface ResolverContext {
  readonly services: {
    readonly anomalyDetection: AnomalyDetectionService;
    readonly threatIntelligence: ThreatIntelligenceService;
    readonly automatedResponse: AutomatedResponseService;
  };
  readonly repositories: {
    readonly anomaly: SecurityAnomalyRepository;
  };
  readonly metrics: MetricsCollector;
  readonly userId: string;
  readonly tenantId: string;
}

/**
 * Query resolver for anomaly detection API.
 */
export class AnomalyQueryResolver {
  /**
   * Resolves getAnomaly query.
   *
   * @param _root Parent object (unused)
   * @param args Query arguments (id)
   * @param context Resolver context with services
   * @returns SecurityAnomaly or null if not found
   */
  static async getAnomaly(
    _root: unknown,
    args: { id: string },
    context: ResolverContext
  ): Promise<SecurityAnomaly | null> {
    const timer = context.metrics.startTimer("graphql_get_anomaly_duration_ms");

    try {
      const anomaly = await context.repositories.anomaly.findById(args.id);
      context.metrics.incrementCounter("graphql_get_anomaly_success", 1);
      return anomaly;
    } catch (error) {
      context.metrics.incrementCounter("graphql_get_anomaly_error", 1);
      throw error;
    } finally {
      timer.end();
    }
  }

  /**
   * Resolves getRecentAnomalies query.
   *
   * @param _root Parent object (unused)
   * @param args Query arguments (limit, offset)
   * @param context Resolver context with services
   * @returns Array of recent anomalies
   */
  static async getRecentAnomalies(
    _root: unknown,
    args: { limit?: number; offset?: number },
    context: ResolverContext
  ): Promise<SecurityAnomaly[]> {
    const timer = context.metrics.startTimer(
      "graphql_get_recent_anomalies_duration_ms"
    );

    try {
      const anomalies = await context.repositories.anomaly.findRecent(
        args.limit || 10
      );
      context.metrics.incrementCounter(
        "graphql_get_recent_anomalies_success",
        anomalies.length
      );
      return anomalies;
    } catch (error) {
      context.metrics.incrementCounter("graphql_get_recent_anomalies_error", 1);
      throw error;
    } finally {
      timer.end();
    }
  }

  /**
   * Resolves getAnomalies query with complex filtering.
   *
   * @param _root Parent object (unused)
   * @param args Query arguments (types, statuses, severity, etc.)
   * @param context Resolver context with services
   * @returns Anomaly connection with pagination
   */
  static async getAnomalies(
    _root: unknown,
    args: {
      types?: string[];
      statuses?: string[];
      minSeverity?: number;
      maxSeverity?: number;
      dateRange?: { from: Date; to: Date };
      limit?: number;
      offset?: number;
    },
    context: ResolverContext
  ): Promise<{
    edges: Array<{ node: SecurityAnomaly; cursor: string }>;
    pageInfo: {
      hasNextPage: boolean;
      hasPreviousPage: boolean;
      startCursor: string | null;
      endCursor: string | null;
    };
    totalCount: number;
  }> {
    const timer = context.metrics.startTimer(
      "graphql_get_anomalies_duration_ms"
    );

    try {
      const anomalies = await context.repositories.anomaly.query(
        {
          types: args.types,
          statuses: args.statuses,
          minSeverity: args.minSeverity,
          maxSeverity: args.maxSeverity,
          dateRange: args.dateRange,
        },
        {
          limit: args.limit || 20,
          offset: args.offset || 0,
        }
      );

      const edges = anomalies.map((anomaly, index) => ({
        node: anomaly,
        cursor: Buffer.from(
          JSON.stringify({ offset: (args.offset || 0) + index })
        ).toString("base64"),
      }));

      context.metrics.incrementCounter(
        "graphql_get_anomalies_success",
        anomalies.length
      );

      return {
        edges,
        pageInfo: {
          hasNextPage: anomalies.length >= (args.limit || 20),
          hasPreviousPage: (args.offset || 0) > 0,
          startCursor: edges[0]?.cursor || null,
          endCursor: edges[edges.length - 1]?.cursor || null,
        },
        totalCount: anomalies.length,
      };
    } catch (error) {
      context.metrics.incrementCounter("graphql_get_anomalies_error", 1);
      throw error;
    } finally {
      timer.end();
    }
  }

  /**
   * Resolves getAnomaliesBySeverity query.
   *
   * @param _root Parent object (unused)
   * @param args Query arguments (minSeverity, limit)
   * @param context Resolver context with services
   * @returns Array of anomalies matching severity filter
   */
  static async getAnomaliesBySeverity(
    _root: unknown,
    args: { minSeverity: number; limit?: number },
    context: ResolverContext
  ): Promise<SecurityAnomaly[]> {
    const timer = context.metrics.startTimer(
      "graphql_get_anomalies_by_severity_duration_ms"
    );

    try {
      const anomalies = await context.repositories.anomaly.findBySeverity(
        args.minSeverity,
        { limit: args.limit || 10, offset: 0 }
      );

      context.metrics.incrementCounter(
        "graphql_get_anomalies_by_severity_success",
        anomalies.length
      );

      return anomalies;
    } catch (error) {
      context.metrics.incrementCounter(
        "graphql_get_anomalies_by_severity_error",
        1
      );
      throw error;
    } finally {
      timer.end();
    }
  }

  /**
   * Resolves getThreat query.
   *
   * @param _root Parent object (unused)
   * @param args Query arguments (cveId)
   * @param context Resolver context with services
   * @returns ThreatIntelligence or null if not found
   */
  static async getThreat(
    _root: unknown,
    args: { cveId: string },
    context: ResolverContext
  ): Promise<ThreatIntelligence | null> {
    const timer = context.metrics.startTimer("graphql_get_threat_duration_ms");

    try {
      const threat = await context.services.threatIntelligence.getThreat(
        args.cveId
      );
      context.metrics.incrementCounter("graphql_get_threat_success", 1);
      return threat;
    } catch (error) {
      context.metrics.incrementCounter("graphql_get_threat_error", 1);
      throw error;
    } finally {
      timer.end();
    }
  }

  /**
   * Resolves getThreatsForSoftware query.
   *
   * @param _root Parent object (unused)
   * @param args Query arguments (softwareName, version)
   * @param context Resolver context with services
   * @returns Array of threats affecting software
   */
  static async getThreatsForSoftware(
    _root: unknown,
    args: { softwareName: string; version: string },
    context: ResolverContext
  ): Promise<ThreatIntelligence[]> {
    const timer = context.metrics.startTimer(
      "graphql_get_threats_for_software_duration_ms"
    );

    try {
      const threats =
        await context.services.threatIntelligence.getThreatsForSoftware(
          args.softwareName,
          args.version
        );

      context.metrics.incrementCounter(
        "graphql_get_threats_for_software_success",
        threats.length
      );

      return threats;
    } catch (error) {
      context.metrics.incrementCounter(
        "graphql_get_threats_for_software_error",
        1
      );
      throw error;
    } finally {
      timer.end();
    }
  }

  /**
   * Resolves getCriticalThreats query.
   *
   * @param _root Parent object (unused)
   * @param _args Query arguments (none)
   * @param context Resolver context with services
   * @returns Array of critical exploitable threats
   */
  static async getCriticalThreats(
    _root: unknown,
    _args: unknown,
    context: ResolverContext
  ): Promise<ThreatIntelligence[]> {
    const timer = context.metrics.startTimer(
      "graphql_get_critical_threats_duration_ms"
    );

    try {
      const threats =
        await context.services.threatIntelligence.getCriticalThreats();

      context.metrics.incrementCounter(
        "graphql_get_critical_threats_success",
        threats.length
      );

      return threats;
    } catch (error) {
      context.metrics.incrementCounter("graphql_get_critical_threats_error", 1);
      throw error;
    } finally {
      timer.end();
    }
  }

  /**
   * Resolves enrichAnomaly query.
   *
   * @param _root Parent object (unused)
   * @param args Query arguments (anomalyId, softwareName)
   * @param context Resolver context with services
   * @returns Enriched anomaly with threat context
   */
  static async enrichAnomaly(
    _root: unknown,
    args: { anomalyId: string; softwareName?: string },
    context: ResolverContext
  ): Promise<{
    anomaly: SecurityAnomaly;
    threats: ThreatIntelligence[];
    riskEscalation: number;
    recommendations: string[];
  } | null> {
    const timer = context.metrics.startTimer(
      "graphql_enrich_anomaly_duration_ms"
    );

    try {
      const anomaly = await context.repositories.anomaly.findById(
        args.anomalyId
      );

      if (!anomaly) {
        return null;
      }

      const enriched =
        await context.services.threatIntelligence.enrichAnomaly(
          anomaly,
          args.softwareName
        );

      context.metrics.incrementCounter("graphql_enrich_anomaly_success", 1);

      return enriched;
    } catch (error) {
      context.metrics.incrementCounter("graphql_enrich_anomaly_error", 1);
      throw error;
    } finally {
      timer.end();
    }
  }

  /**
   * Resolves getRiskAssessment query.
   *
   * @param _root Parent object (unused)
   * @param args Query arguments (anomalyId)
   * @param context Resolver context with services
   * @returns Risk assessment for anomaly
   */
  static async getRiskAssessment(
    _root: unknown,
    args: { anomalyId: string },
    context: ResolverContext
  ): Promise<{
    anomalyId: string;
    currentRiskScore: number;
    severityFactor: number;
    deviationFactor: number;
    frequencyFactor: number;
    riskCategory: string;
    recommendations: string[];
    updatedAt: Date;
  } | null> {
    const timer = context.metrics.startTimer(
      "graphql_get_risk_assessment_duration_ms"
    );

    try {
      const anomaly = await context.repositories.anomaly.findById(
        args.anomalyId
      );

      if (!anomaly) {
        return null;
      }

      // Calculate risk score
      const riskScore = context.services.anomalyDetection.scoreAnomalyRisk(
        anomaly,
        1 // NOTE: Get frequency from analytics
      );

      const category =
        anomaly.severity >= 0.8
          ? "CRITICAL"
          : anomaly.severity >= 0.6
            ? "HIGH"
            : anomaly.severity >= 0.4
              ? "MEDIUM"
              : "LOW";

      context.metrics.incrementCounter("graphql_get_risk_assessment_success", 1);

      return {
        anomalyId: args.anomalyId,
        currentRiskScore: riskScore,
        severityFactor: anomaly.severity,
        deviationFactor: anomaly.deviationPercentage() / 100,
        frequencyFactor: 0.2, // NOTE: Get frequency
        riskCategory: category,
        recommendations: [
          "Review investigation notes",
          "Check related threats",
          "Execute remediation playbooks",
        ],
        updatedAt: new Date(),
      };
    } catch (error) {
      context.metrics.incrementCounter("graphql_get_risk_assessment_error", 1);
      throw error;
    } finally {
      timer.end();
    }
  }

  /**
   * Resolves getOpenIncidents query.
   *
   * @param _root Parent object (unused)
   * @param _args Query arguments (none)
   * @param context Resolver context with services
   * @returns Array of open incidents
   */
  static async getOpenIncidents(
    _root: unknown,
    _args: unknown,
    context: ResolverContext
  ): Promise<Array<{
    id: string;
    anomalyId: string;
    severity: string;
    title: string;
    description: string;
    createdAt: Date;
    status: string;
    actions: Array<unknown>;
    playbooks: string[];
    relatedThreats: string[];
  }>> {
    const timer = context.metrics.startTimer(
      "graphql_get_open_incidents_duration_ms"
    );

    try {
      const incidents =
        await context.services.automatedResponse.getOpenIncidents();

      context.metrics.incrementCounter(
        "graphql_get_open_incidents_success",
        incidents.length
      );

      return incidents;
    } catch (error) {
      context.metrics.incrementCounter("graphql_get_open_incidents_error", 1);
      throw error;
    } finally {
      timer.end();
    }
  }
}

/**
 * Mutation resolver for anomaly detection API.
 */
export class AnomalyMutationResolver {
  /**
   * Resolves acknowledgeAnomaly mutation.
   *
   * @param _root Parent object (unused)
   * @param args Mutation arguments (anomalyId, notes)
   * @param context Resolver context with services
   * @returns Updated SecurityAnomaly
   */
  static async acknowledgeAnomaly(
    _root: unknown,
    args: { anomalyId: string; notes: string },
    context: ResolverContext
  ): Promise<SecurityAnomaly> {
    const timer = context.metrics.startTimer(
      "graphql_acknowledge_anomaly_duration_ms"
    );

    try {
      const result =
        await context.services.anomalyDetection.acknowledgeAnomaly(
          args.anomalyId,
          args.notes
        );

      context.metrics.incrementCounter("graphql_acknowledge_anomaly_success", 1);

      return result;
    } catch (error) {
      context.metrics.incrementCounter("graphql_acknowledge_anomaly_error", 1);
      throw error;
    } finally {
      timer.end();
    }
  }

  /**
   * Resolves resolveAnomaly mutation.
   *
   * @param _root Parent object (unused)
   * @param args Mutation arguments (anomalyId, remediationStep)
   * @param context Resolver context with services
   * @returns Updated SecurityAnomaly
   */
  static async resolveAnomaly(
    _root: unknown,
    args: { anomalyId: string; remediationStep: string },
    context: ResolverContext
  ): Promise<SecurityAnomaly> {
    const timer = context.metrics.startTimer(
      "graphql_resolve_anomaly_duration_ms"
    );

    try {
      const result = await context.services.anomalyDetection.resolveAnomaly(
        args.anomalyId,
        args.remediationStep
      );

      context.metrics.incrementCounter("graphql_resolve_anomaly_success", 1);

      return result;
    } catch (error) {
      context.metrics.incrementCounter("graphql_resolve_anomaly_error", 1);
      throw error;
    } finally {
      timer.end();
    }
  }

  /**
   * Resolves respondToAnomaly mutation.
   *
   * @param _root Parent object (unused)
   * @param args Mutation arguments (anomalyId)
   * @param context Resolver context with services
   * @returns Created Incident
   */
  static async respondToAnomaly(
    _root: unknown,
    args: { anomalyId: string },
    context: ResolverContext
  ): Promise<{
    id: string;
    anomalyId: string;
    severity: string;
    title: string;
    description: string;
    createdAt: Date;
    status: string;
    actions: Array<unknown>;
    playbooks: string[];
    relatedThreats: string[];
  }> {
    const timer = context.metrics.startTimer(
      "graphql_respond_to_anomaly_duration_ms"
    );

    try {
      const anomaly = await context.repositories.anomaly.findById(
        args.anomalyId
      );

      if (!anomaly) {
        throw new Error(`Anomaly not found: ${args.anomalyId}`);
      }

      const incident =
        await context.services.automatedResponse.respondToAnomaly(anomaly);

      context.metrics.incrementCounter("graphql_respond_to_anomaly_success", 1);

      return incident;
    } catch (error) {
      context.metrics.incrementCounter("graphql_respond_to_anomaly_error", 1);
      throw error;
    } finally {
      timer.end();
    }
  }

  /**
   * Resolves updateThreatIntelligence mutation.
   *
   * @param _root Parent object (unused)
   * @param _args Mutation arguments (none)
   * @param context Resolver context with services
   * @returns Count of threats updated
   */
  static async updateThreatIntelligence(
    _root: unknown,
    _args: unknown,
    context: ResolverContext
  ): Promise<number> {
    const timer = context.metrics.startTimer(
      "graphql_update_threat_intelligence_duration_ms"
    );

    try {
      const count =
        await context.services.threatIntelligence.updateThreatIntelligence();

      context.metrics.incrementCounter(
        "graphql_update_threat_intelligence_success",
        count
      );

      return count;
    } catch (error) {
      context.metrics.incrementCounter(
        "graphql_update_threat_intelligence_error",
        1
      );
      throw error;
    } finally {
      timer.end();
    }
  }
}
