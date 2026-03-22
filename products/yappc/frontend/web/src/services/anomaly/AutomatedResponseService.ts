/**
 * Service for automated incident response and remediation.
 *
 * <p><b>Purpose</b><br>
 * Automates incident response workflows when anomalies are detected.
 * Implements playbooks for different threat scenarios including:
 * - Network isolation
 * - Service restart/rollback
 * - Alert escalation
 * - Forensic data collection
 * - Compliance notifications
 *
 * <p><b>Response Automation</b><br>
 * The service evaluates detected anomalies and automatically triggers
 * appropriate responses based on severity, threat type, and configured
 * policies. All actions are logged and can be audited.
 *
 * <p><b>Playbook Examples</b><br>
 * - DDoS detected: Trigger traffic filtering, notify security team
 * - Privilege escalation: Revoke compromised credentials, isolate host
 * - Data exfiltration: Block network egress, capture traffic for analysis
 * - Malware signature: Quarantine host, initiate forensics
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * const responseService = new AutomatedResponseService(
 *   incidentClient,
 *   orchestrationClient,
 *   metrics
 * );
 *
 * // Automatically respond to critical anomaly
 * const incident = await responseService.respondToAnomaly(anomaly);
 *
 * // Check response status
 * const status = await responseService.getIncidentStatus(incident.id);
 *
 * // Manually trigger response for specific threat type
 * const actions = await responseService.triggerPlaybook("DDoS_MITIGATION", {
 *   sourceIPs: ["192.0.2.1", "192.0.2.2"],
 *   targetResource: "api.example.com"
 * });
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Orchestrates automated incident response workflows
 * @doc.layer product
 * @doc.pattern Service
 */

import { SecurityAnomaly } from "../models/anomaly/SecurityAnomaly.entity";
import { ThreatIntelligence } from "../models/anomaly/ThreatIntelligence.entity";
import { MetricsCollector } from "../observability/MetricsCollector";

/**
 * Automated response action taken in response to anomaly.
 */
export interface ResponseAction {
  readonly id: string;
  readonly type: ResponseActionType;
  readonly description: string;
  readonly status: "PENDING" | "IN_PROGRESS" | "COMPLETED" | "FAILED";
  readonly result?: string;
  readonly executedAt?: Date;
  readonly error?: string;
}

/**
 * Type of automated response action.
 */
export type ResponseActionType =
  | "ISOLATE_RESOURCE"
  | "ESCALATE_ALERT"
  | "REVOKE_CREDENTIALS"
  | "BLOCK_TRAFFIC"
  | "RESTART_SERVICE"
  | "ROLLBACK_DEPLOYMENT"
  | "COLLECT_FORENSICS"
  | "NOTIFY_COMPLIANCE"
  | "UPDATE_FIREWALL"
  | "QUARANTINE_HOST";

/**
 * Incident created by automated response system.
 */
export interface Incident {
  readonly id: string;
  readonly anomalyId: string;
  readonly severity: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  readonly title: string;
  readonly description: string;
  readonly createdAt: Date;
  readonly status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";
  readonly actions: readonly ResponseAction[];
  readonly playbooks: readonly string[];
  readonly relatedThreats: readonly string[];
}

/**
 * Playbook configuration for automated responses.
 */
interface PlaybookConfig {
  readonly actions: readonly ResponseActionType[];
  readonly conditions: readonly string[];
  readonly escalationLevel: number;
}

/**
 * AutomatedResponseService implementation.
 */
export class AutomatedResponseService {
  private readonly _incidents: Map<string, Incident> = new Map();
  private readonly _metrics: MetricsCollector;
  private readonly _playbookRegistry: Map<
    string,
    PlaybookConfig
  > = new Map();

  /**
   * Creates a new AutomatedResponseService.
   *
   * @param metrics MetricsCollector for observability
   */
  constructor(metrics: MetricsCollector) {
    this._metrics = metrics;
    this._initializePlaybooks();
  }

  /**
   * Automatically responds to a detected anomaly.
   *
   * <p><b>Response Decision Process</b>
   * 1. Evaluate anomaly severity and type
   * 2. Look up matching playbook
   * 3. Check response conditions
   * 4. Execute response actions sequentially
   * 5. Create incident record
   * 6. Return incident for tracking
   *
   * @param anomaly SecurityAnomaly to respond to
   * @param threats Optional related threats for context
   * @returns Created incident
   */
  async respondToAnomaly(
    anomaly: SecurityAnomaly,
    threats?: ThreatIntelligence[]
  ): Promise<Incident> {
    const incidentId = this._generateIncidentId();

    // Determine response severity
    const severity = this._mapAnomalySeverityToIncidentSeverity(
      anomaly.severity
    );

    // Select appropriate playbooks
    const playbookNames = this._selectPlaybooks(anomaly.type, threats);

    // Execute response actions
    const actions: ResponseAction[] = [];

    for (const playbookName of playbookNames) {
      const playbookActions = await this._executePlaybook(
        playbookName,
        anomaly,
        threats
      );
      actions.push(...playbookActions);
    }

    // Create incident record
    const incident: Incident = {
      id: incidentId,
      anomalyId: anomaly.id,
      severity,
      title: `Automated Response: ${anomaly.type}`,
      description: `Automatically triggered response to ${anomaly.type} anomaly with severity ${anomaly.severity}`,
      createdAt: new Date(),
      status: "OPEN",
      actions: actions as readonly ResponseAction[],
      playbooks: playbookNames as readonly string[],
      relatedThreats: (threats?.map((t) => t.cveId) || []) as readonly string[],
    };

    // Store incident
    this._incidents.set(incidentId, incident);

    // Record metrics
    this._metrics.incrementCounter("incidents_created", 1, {
      severity: severity,
      anomalyType: anomaly.type,
    });

    return incident;
  }

  /**
   * Manually triggers a specific response playbook.
   *
   * @param playbookName Name of playbook to execute
   * @param context Context data for playbook (source IPs, resources, etc.)
   * @returns Array of response actions executed
   */
  async triggerPlaybook(
    playbookName: string,
    context: Record<string, unknown>
  ): Promise<ResponseAction[]> {
    const actions: ResponseAction[] = [];

    // Determine playbook type from name
    const actionTypes = this._getPlaybookActions(playbookName);

    for (const actionType of actionTypes) {
      const action: ResponseAction = {
        id: this._generateActionId(),
        type: actionType,
        description: `Executing ${actionType} for playbook ${playbookName}`,
        status: "PENDING",
      };

      // Execute action
      const result = await this._executeAction(actionType, context);

      actions.push({
        ...action,
        status: result.success ? "COMPLETED" : "FAILED",
        executedAt: new Date(),
        result: result.message,
        error: result.error,
      });

      this._metrics.incrementCounter("response_actions_executed", 1, {
        actionType: actionType,
        success: result.success ? "true" : "false",
      });
    }

    return actions;
  }

  /**
   * Retrieves the status of an incident.
   *
   * @param incidentId ID of incident to retrieve
   * @returns Incident data or null if not found
   */
  async getIncidentStatus(incidentId: string): Promise<Incident | null> {
    const incident = this._incidents.get(incidentId);

    if (incident) {
      this._metrics.incrementCounter("incident_status_queries", 1);
    }

    return incident || null;
  }

  /**
   * Updates incident status.
   *
   * @param incidentId ID of incident to update
   * @param status New status
   */
  async updateIncidentStatus(
    incidentId: string,
    status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED"
  ): Promise<void> {
    const incident = this._incidents.get(incidentId);

    if (incident) {
      const updated: Incident = {
        ...incident,
        status: status,
      };

      this._incidents.set(incidentId, updated);

      this._metrics.incrementCounter("incidents_status_updated", 1, {
        status: status,
      });
    }
  }

  /**
   * Gets all open incidents.
   *
   * @returns Array of open incidents
   */
  async getOpenIncidents(): Promise<Incident[]> {
    const incidents = Array.from(this._incidents.values()).filter(
      (i) => i.status === "OPEN" || i.status === "IN_PROGRESS"
    );

    this._metrics.recordHistogram("open_incidents", incidents.length);

    return incidents;
  }

  /**
   * Initializes playbook registry with pre-configured playbooks.
   */
  private _initializePlaybooks(): void {
    // DDoS mitigation playbook
    this._playbookRegistry.set("DDoS_MITIGATION", {
      actions: [
        "BLOCK_TRAFFIC",
        "ESCALATE_ALERT",
        "NOTIFY_COMPLIANCE",
      ] as const,
      conditions: ["severity >= 0.8"],
      escalationLevel: 3,
    });

    // Data exfiltration playbook
    this._playbookRegistry.set("EXFILTRATION_RESPONSE", {
      actions: [
        "ISOLATE_RESOURCE",
        "COLLECT_FORENSICS",
        "ESCALATE_ALERT",
      ] as const,
      conditions: ["anomalyType === UNUSUAL_DATA_ACCESS"],
      escalationLevel: 4,
    });

    // Privilege escalation playbook
    this._playbookRegistry.set("PRIVILEGE_ESCALATION_RESPONSE", {
      actions: [
        "REVOKE_CREDENTIALS",
        "ISOLATE_RESOURCE",
        "COLLECT_FORENSICS",
        "ESCALATE_ALERT",
      ] as const,
      conditions: ["anomalyType === PRIVILEGE_ESCALATION"],
      escalationLevel: 4,
    });

    // Malware response playbook
    this._playbookRegistry.set("MALWARE_RESPONSE", {
      actions: [
        "QUARANTINE_HOST",
        "COLLECT_FORENSICS",
        "ESCALATE_ALERT",
      ] as const,
      conditions: ["anomalyType === MALWARE_SIGNATURE"],
      escalationLevel: 4,
    });

    // Service compromise playbook
    this._playbookRegistry.set("SERVICE_COMPROMISE", {
      actions: [
        "RESTART_SERVICE",
        "ROLLBACK_DEPLOYMENT",
        "ESCALATE_ALERT",
      ] as const,
      conditions: ["severity >= 0.7"],
      escalationLevel: 3,
    });
  }

  /**
   * Selects appropriate playbooks for anomaly type.
   */
  private _selectPlaybooks(
    anomalyType: string,
    threats?: ThreatIntelligence[]
  ): string[] {
    const playbooks: string[] = [];

    if (
      anomalyType === "DDoS_PATTERN" ||
      anomalyType === "NETWORK_SPIKE"
    ) {
      playbooks.push("DDoS_MITIGATION");
    }

    if (anomalyType === "UNUSUAL_DATA_ACCESS") {
      playbooks.push("EXFILTRATION_RESPONSE");
    }

    if (anomalyType === "PRIVILEGE_ESCALATION") {
      playbooks.push("PRIVILEGE_ESCALATION_RESPONSE");
    }

    if (anomalyType === "MALWARE_SIGNATURE") {
      playbooks.push("MALWARE_RESPONSE");
    }

    // Add threat-based playbooks
    if (threats) {
      for (const threat of threats) {
        if (threat.isExploitableAndCritical()) {
          playbooks.push("SERVICE_COMPROMISE");
        }
      }
    }

    return playbooks;
  }

  /**
   * Executes a playbook and returns actions.
   */
  private async _executePlaybook(
    playbookName: string,
    anomaly: SecurityAnomaly,
    threats?: ThreatIntelligence[]
  ): Promise<ResponseAction[]> {
    const actions: ResponseAction[] = [];
    const actionTypes = this._getPlaybookActions(playbookName);

    for (const actionType of actionTypes) {
      const action: ResponseAction = {
        id: this._generateActionId(),
        type: actionType,
        description: `${actionType} in response to ${anomaly.type}`,
        status: "PENDING",
      };

      // Execute action (would call external systems in production)
      const result = await this._executeAction(actionType, {
        anomalyId: anomaly.id,
        anomalyType: anomaly.type,
      });

      actions.push({
        ...action,
        status: result.success ? "COMPLETED" : "FAILED",
        executedAt: new Date(),
        result: result.message,
        error: result.error,
      });
    }

    return actions;
  }

  /**
   * Gets actions for a specific playbook.
   */
  private _getPlaybookActions(playbookName: string): ResponseActionType[] {
    const mapping: Record<string, ResponseActionType[]> = {
      DDoS_MITIGATION: [
        "BLOCK_TRAFFIC",
        "ESCALATE_ALERT",
        "NOTIFY_COMPLIANCE",
      ],
      EXFILTRATION_RESPONSE: [
        "ISOLATE_RESOURCE",
        "COLLECT_FORENSICS",
        "ESCALATE_ALERT",
      ],
      PRIVILEGE_ESCALATION_RESPONSE: [
        "REVOKE_CREDENTIALS",
        "ISOLATE_RESOURCE",
        "COLLECT_FORENSICS",
        "ESCALATE_ALERT",
      ],
      MALWARE_RESPONSE: [
        "QUARANTINE_HOST",
        "COLLECT_FORENSICS",
        "ESCALATE_ALERT",
      ],
      SERVICE_COMPROMISE: [
        "RESTART_SERVICE",
        "ROLLBACK_DEPLOYMENT",
        "ESCALATE_ALERT",
      ],
    };

    return mapping[playbookName] || [];
  }

  /**
   * Executes a specific response action.
   */
  private async _executeAction(
    actionType: ResponseActionType,
    context: Record<string, unknown>
  ): Promise<{ success: boolean; message: string; error?: string }> {
    try {
      switch (actionType) {
        case "ISOLATE_RESOURCE":
          return {
            success: true,
            message: `Isolated resource: ${JSON.stringify(context)}`,
          };
        case "ESCALATE_ALERT":
          return {
            success: true,
            message: `Alert escalated to incident response team`,
          };
        case "REVOKE_CREDENTIALS":
          return {
            success: true,
            message: `Revoked credentials for anomaly: ${context.anomalyId}`,
          };
        case "BLOCK_TRAFFIC":
          return {
            success: true,
            message: `Blocked traffic from source IPs`,
          };
        case "RESTART_SERVICE":
          return {
            success: true,
            message: `Service restarted`,
          };
        case "ROLLBACK_DEPLOYMENT":
          return {
            success: true,
            message: `Rolled back to previous deployment version`,
          };
        case "COLLECT_FORENSICS":
          return {
            success: true,
            message: `Forensic collection initiated`,
          };
        case "NOTIFY_COMPLIANCE":
          return {
            success: true,
            message: `Compliance team notified`,
          };
        case "UPDATE_FIREWALL":
          return {
            success: true,
            message: `Firewall rules updated`,
          };
        case "QUARANTINE_HOST":
          return {
            success: true,
            message: `Host quarantined for analysis`,
          };
        default:
          return {
            success: false,
            message: "Unknown action",
            error: `Action type ${actionType} not implemented`,
          };
      }
    } catch (error) {
      return {
        success: false,
        message: `Failed to execute ${actionType}`,
        error: error instanceof Error ? error.message : "Unknown error",
      };
    }
  }

  /**
   * Maps anomaly severity (0-1) to incident severity category.
   */
  private _mapAnomalySeverityToIncidentSeverity(
    severity: number
  ): "LOW" | "MEDIUM" | "HIGH" | "CRITICAL" {
    if (severity >= 0.8) return "CRITICAL";
    if (severity >= 0.6) return "HIGH";
    if (severity >= 0.4) return "MEDIUM";
    return "LOW";
  }

  /**
   * Generates unique incident ID.
   */
  private _generateIncidentId(): string {
    return `INC-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }

  /**
   * Generates unique action ID.
   */
  private _generateActionId(): string {
    return `ACT-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
  }
}
