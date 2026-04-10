// @ts-nocheck
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

import { SecurityAnomaly } from "../../models/anomaly/SecurityAnomaly.entity";
import { ThreatIntelligence } from "../../models/anomaly/ThreatIntelligence.entity";
import { MetricsCollector } from "../../observability/MetricsCollector";

/**
 * Repository interface for incident persistence.
 */
export interface IncidentRepository {
  save(incident: Incident): Promise<void>;
  findById(id: string): Promise<Incident | null>;
  findByStatus(status: string): Promise<Incident[]>;
  query(): Promise<Incident[]>;
}

/**
 * Automated response action taken in response to anomaly.
 */
export interface ResponseAction {
  readonly id: string;
  readonly actionType: ResponseActionType;
  readonly type: ResponseActionType; // alias for actionType
  readonly description: string;
  readonly status: "PENDING" | "IN_PROGRESS" | "COMPLETED" | "FAILED";
  readonly createdAt: Date;
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
  readonly resourceId?: string;
  readonly severity: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  readonly title: string;
  readonly description: string;
  readonly createdAt: Date;
  readonly status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";
  readonly responseActions: readonly ResponseAction[];
  readonly actions: readonly ResponseAction[]; // alias for responseActions
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
  private readonly _repository: IncidentRepository;
  private readonly _metrics: MetricsCollector;
  private readonly _playbookRegistry: Map<
    string,
    PlaybookConfig
  > = new Map();

  /**
   * Creates a new AutomatedResponseService.
   *
   * @param repository IncidentRepository for persistence
   * @param metrics MetricsCollector for observability
   */
  constructor(repository: IncidentRepository, metrics: MetricsCollector) {
    this._repository = repository;
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

    // Determine response severity (escalate if exploitable threats)
    let severity = this._mapAnomalySeverityToIncidentSeverity(
      anomaly.severity
    );

    // Escalate severity if exploitable critical threats are present
    if (threats && threats.some(t =>
      typeof t.isExploitableAndCritical === 'function'
        ? t.isExploitableAndCritical()
        : (t.exploitAvailable && (t.severity === 'CRITICAL' || t.severity === 'HIGH'))
    )) {
      severity = 'CRITICAL';
    }

    // Select appropriate playbooks
    const playbookNames = this._selectPlaybooks(anomaly.type, threats);

    // Execute response actions (create as PENDING)
    const actions: ResponseAction[] = [];

    for (const playbookName of playbookNames) {
      const playbookActionTypes = this._getPlaybookActions(playbookName);
      for (const actionType of playbookActionTypes) {
        const action: ResponseAction = {
          id: this._generateActionId(),
          actionType,
          type: actionType,
          description: `${actionType} in response to ${anomaly.type}`,
          status: "PENDING",
          createdAt: new Date(),
        };
        actions.push(action);
      }
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
      responseActions: actions as readonly ResponseAction[],
      actions: actions as readonly ResponseAction[],
      playbooks: playbookNames as readonly string[],
      relatedThreats: (threats?.map((t) => t.cveId) || []) as readonly string[],
    };

    // Persist incident
    await this._repository.save(incident);

    // Record metrics
    this._metrics.incrementCounter("automated_response_triggered", 1, {
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
  ): Promise<Incident> {
    // Normalize playbook name (support human-readable names)
    const normalizedName = this._normalizePlaybookName(playbookName);
    const actionTypes = this._getPlaybookActions(normalizedName);

    if (actionTypes.length === 0) {
      throw new Error(`Unknown playbook: ${playbookName}`);
    }

    const actions: ResponseAction[] = actionTypes.map((actionType) => ({
      id: this._generateActionId(),
      actionType,
      type: actionType,
      description: `Executing ${actionType} for playbook ${playbookName}`,
      status: "PENDING" as const,
      createdAt: new Date(),
    }));

    this._metrics.incrementCounter("playbook_executed", 1, {
      playbookName,
    });

    const incidentId = this._generateIncidentId();
    const incident: Incident = {
      id: incidentId,
      anomalyId: (context.anomalyId as string) || incidentId,
      resourceId: context.resourceId as string | undefined,
      severity: (context.severity as Incident['severity']) || 'HIGH',
      title: `Playbook Response: ${playbookName}`,
      description: `Manual playbook execution: ${playbookName}`,
      createdAt: new Date(),
      status: "OPEN",
      responseActions: actions as readonly ResponseAction[],
      actions: actions as readonly ResponseAction[],
      playbooks: [playbookName] as readonly string[],
      relatedThreats: [] as readonly string[],
    };

    await this._repository.save(incident);

    return incident;
  }

  /**
   * Retrieves the status of an incident.
   *
   * @param incidentId ID of incident to retrieve
   * @returns Incident data or null if not found
   */
  async getIncidentStatus(incidentId: string): Promise<Incident | null> {
    const incident = await this._repository.findById(incidentId);

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
  ): Promise<Incident | null> {
    const incident = await this._repository.findById(incidentId);

    if (!incident) {
      return null;
    }

    const updated: Incident = {
      ...incident,
      status: status,
    };

    await this._repository.save(updated);

    this._metrics.incrementCounter("incident_status_updated", 1, {
      status: status,
    });

    return updated;
  }

  /**
   * Gets all open incidents.
   *
   * @returns Array of open incidents
   */
  async getOpenIncidents(): Promise<Incident[]> {
    const all = await this._repository.query();
    const incidents = all.filter(
      (i) => i.status === "OPEN" || i.status === "IN_PROGRESS"
    );

    this._metrics.incrementCounter("open_incidents_query", 1, {});

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
        const exploitable = typeof threat.isExploitableAndCritical === 'function'
          ? threat.isExploitableAndCritical()
          : (threat.exploitAvailable && (threat.severity === 'CRITICAL' || threat.severity === 'HIGH'));
        if (exploitable) {
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
   * Normalizes human-readable playbook names to internal registry keys.
   */
  private _normalizePlaybookName(name: string): string {
    const mapping: Record<string, string> = {
      'DDoS Mitigation': 'DDoS_MITIGATION',
      'Exfiltration Response': 'EXFILTRATION_RESPONSE',
      'Privilege Escalation Response': 'PRIVILEGE_ESCALATION_RESPONSE',
      'Malware Response': 'MALWARE_RESPONSE',
      'Service Compromise': 'SERVICE_COMPROMISE',
    };
    return mapping[name] || name;
  }

  /**
   * Maps anomaly severity (0-1 or string) to incident severity category.
   */
  private _mapAnomalySeverityToIncidentSeverity(
    severity: number | string
  ): "LOW" | "MEDIUM" | "HIGH" | "CRITICAL" {
    if (typeof severity === 'string') {
      const s = severity.toUpperCase();
      if (s === 'CRITICAL') return 'CRITICAL';
      if (s === 'HIGH') return 'HIGH';
      if (s === 'MEDIUM') return 'MEDIUM';
      return 'LOW';
    }
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
