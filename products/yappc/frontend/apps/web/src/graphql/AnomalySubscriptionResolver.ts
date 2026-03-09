/**
 * GraphQL Subscription resolvers for real-time anomaly updates.
 *
 * <p><b>Purpose</b><br>
 * Provides real-time subscriptions for:
 * - New anomaly detection events
 * - Status changes on anomalies
 * - Incident updates
 * - Critical threat discovery
 * - Response action execution
 *
 * <p><b>Subscription Pattern</b><br>
 * Uses PubSub (Apollo PubSub or similar) for event distribution:
 * 1. Services emit events to PubSub when significant actions occur
 * 2. Subscriptions filter events by criteria
 * 3. WebSocket pushes updates to subscribed clients
 * 4. Clients receive real-time updates without polling
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * // Client-side GraphQL subscription
 * subscription OnAnomalyDetected {
 *   onAnomalyDetected(types: [DDoS_PATTERN, PRIVILEGE_ESCALATION]) {
 *     id
 *     type
 *     severity
 *     description
 *   }
 * }
 *
 * // Updates arrive in real-time as anomalies are detected
 * }</pre>
 *
 * @doc.type class
 * @doc.purpose Real-time subscription resolution
 * @doc.layer product
 * @doc.pattern Subscription
 */

import { SecurityAnomaly } from "../models/anomaly/SecurityAnomaly.entity";
import { ThreatIntelligence } from "../models/anomaly/ThreatIntelligence.entity";

/**
 * PubSub event names for subscription system.
 */
export enum PubSubEvent {
  ANOMALY_DETECTED = "ANOMALY_DETECTED",
  ANOMALY_STATUS_CHANGED = "ANOMALY_STATUS_CHANGED",
  INCIDENT_CREATED = "INCIDENT_CREATED",
  INCIDENT_UPDATED = "INCIDENT_UPDATED",
  CRITICAL_THREAT_DISCOVERED = "CRITICAL_THREAT_DISCOVERED",
  RESPONSE_ACTION_EXECUTED = "RESPONSE_ACTION_EXECUTED",
}

/**
 * Subscription context provided by GraphQL server.
 */
export interface SubscriptionContext {
  readonly pubSub: PubSub;
  readonly userId: string;
  readonly tenantId: string;
}

/**
 * Simplified PubSub interface for type safety.
 */
export interface PubSub {
  subscribe(event: string, onValue?: (value: unknown) => void): AsyncIterable<unknown>;
  publish(event: string, value: unknown): Promise<void>;
}

/**
 * GraphQL subscription resolvers.
 */
export class AnomalySubscriptionResolver {
  /**
   * Resolves onAnomalyDetected subscription.
   *
   * <p><b>Behavior</b>
   * - Emits when new anomalies are detected
   * - Filters by optional type and severity constraints
   * - Real-time stream via WebSocket
   *
   * @param _root Parent object (unused)
   * @param args Subscription arguments (types, minSeverity)
   * @param context Subscription context with PubSub
   * @returns AsyncIterable stream of anomalies
   */
  static async *onAnomalyDetected(
    _root: unknown,
    args: {
      types?: string[];
      minSeverity?: number;
    },
    context: SubscriptionContext
  ): AsyncIterable<SecurityAnomaly> {
    // Subscribe to anomaly detected events
    const eventStream = context.pubSub.subscribe(
      PubSubEvent.ANOMALY_DETECTED
    );

    for await (const event of eventStream) {
      const anomaly = event as SecurityAnomaly;

      // Apply filters if provided
      if (args.types && !args.types.includes(anomaly.type)) {
        continue;
      }

      if (
        args.minSeverity !== undefined &&
        anomaly.severity < args.minSeverity
      ) {
        continue;
      }

      yield anomaly;
    }
  }

  /**
   * Resolves onAnomalyStatusChanged subscription.
   *
   * <p><b>Behavior</b>
   * - Emits when an anomaly's status changes
   * - Filters to specific anomaly ID
   * - Tracks investigation lifecycle
   *
   * @param _root Parent object (unused)
   * @param args Subscription arguments (anomalyId)
   * @param context Subscription context with PubSub
   * @returns AsyncIterable stream of updated anomalies
   */
  static async *onAnomalyStatusChanged(
    _root: unknown,
    args: { anomalyId: string },
    context: SubscriptionContext
  ): AsyncIterable<SecurityAnomaly> {
    // Subscribe to status change events for specific anomaly
    const eventKey = `${PubSubEvent.ANOMALY_STATUS_CHANGED}:${args.anomalyId}`;
    const eventStream = context.pubSub.subscribe(eventKey);

    for await (const event of eventStream) {
      yield event as SecurityAnomaly;
    }
  }

  /**
   * Resolves onIncidentUpdate subscription.
   *
   * <p><b>Behavior</b>
   * - Emits when incident is created or updated
   * - Filters to specific incident ID
   * - Tracks incident lifecycle and actions
   *
   * @param _root Parent object (unused)
   * @param args Subscription arguments (incidentId)
   * @param context Subscription context with PubSub
   * @returns AsyncIterable stream of updated incidents
   */
  static async *onIncidentUpdate(
    _root: unknown,
    args: { incidentId: string },
    context: SubscriptionContext
  ): AsyncIterable<{
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
    // Subscribe to incident update events
    const eventKey = `${PubSubEvent.INCIDENT_UPDATED}:${args.incidentId}`;
    const eventStream = context.pubSub.subscribe(eventKey);

    for await (const event of eventStream) {
      yield event as {
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
      };
    }
  }

  /**
   * Resolves onCriticalThreatDiscovered subscription.
   *
   * <p><b>Behavior</b>
   * - Emits when critical exploitable threats are discovered
   * - Real-time security alert stream
   * - No filtering (all critical threats streamed)
   *
   * @param _root Parent object (unused)
   * @param _args Subscription arguments (none)
   * @param context Subscription context with PubSub
   * @returns AsyncIterable stream of critical threats
   */
  static async *onCriticalThreatDiscovered(
    _root: unknown,
    _args: unknown,
    context: SubscriptionContext
  ): AsyncIterable<ThreatIntelligence> {
    // Subscribe to critical threat discovery events
    const eventStream = context.pubSub.subscribe(
      PubSubEvent.CRITICAL_THREAT_DISCOVERED
    );

    for await (const event of eventStream) {
      yield event as ThreatIntelligence;
    }
  }

  /**
   * Resolves onResponseActionExecuted subscription.
   *
   * <p><b>Behavior</b>
   * - Emits when automated response actions are executed
   * - Filters to specific anomaly ID
   * - Tracks remediation progress
   *
   * @param _root Parent object (unused)
   * @param args Subscription arguments (anomalyId)
   * @param context Subscription context with PubSub
   * @returns AsyncIterable stream of response actions
   */
  static async *onResponseActionExecuted(
    _root: unknown,
    args: { anomalyId: string },
    context: SubscriptionContext
  ): AsyncIterable<{
    id: string;
    type: string;
    description: string;
    status: string;
    result?: string;
    executedAt?: Date;
    error?: string;
  }> {
    // Subscribe to response action events
    const eventKey = `${PubSubEvent.RESPONSE_ACTION_EXECUTED}:${args.anomalyId}`;
    const eventStream = context.pubSub.subscribe(eventKey);

    for await (const event of eventStream) {
      yield event as {
        id: string;
        type: string;
        description: string;
        status: string;
        result?: string;
        executedAt?: Date;
        error?: string;
      };
    }
  }
}

/**
 * Helper for publishing events to subscription subscribers.
 *
 * <p>Services use these methods to emit events that subscribers receive.
 */
export class SubscriptionPublisher {
  /**
   * Publishes a new anomaly detection event.
   *
   * @param pubSub PubSub instance
   * @param anomaly Detected anomaly
   */
  static async publishAnomalyDetected(
    pubSub: PubSub,
    anomaly: SecurityAnomaly
  ): Promise<void> {
    await pubSub.publish(PubSubEvent.ANOMALY_DETECTED, anomaly);
  }

  /**
   * Publishes an anomaly status change event.
   *
   * @param pubSub PubSub instance
   * @param anomalyId ID of anomaly that changed
   * @param anomaly Updated anomaly
   */
  static async publishAnomalyStatusChanged(
    pubSub: PubSub,
    anomalyId: string,
    anomaly: SecurityAnomaly
  ): Promise<void> {
    const eventKey = `${PubSubEvent.ANOMALY_STATUS_CHANGED}:${anomalyId}`;
    await pubSub.publish(eventKey, anomaly);
  }

  /**
   * Publishes an incident creation event.
   *
   * @param pubSub PubSub instance
   * @param incident Created incident
   */
  static async publishIncidentCreated(
    pubSub: PubSub,
    incident: {
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
    }
  ): Promise<void> {
    await pubSub.publish(PubSubEvent.INCIDENT_CREATED, incident);
  }

  /**
   * Publishes an incident update event.
   *
   * @param pubSub PubSub instance
   * @param incidentId ID of incident that updated
   * @param incident Updated incident
   */
  static async publishIncidentUpdated(
    pubSub: PubSub,
    incidentId: string,
    incident: {
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
    }
  ): Promise<void> {
    const eventKey = `${PubSubEvent.INCIDENT_UPDATED}:${incidentId}`;
    await pubSub.publish(eventKey, incident);
  }

  /**
   * Publishes a critical threat discovery event.
   *
   * @param pubSub PubSub instance
   * @param threat Discovered critical threat
   */
  static async publishCriticalThreatDiscovered(
    pubSub: PubSub,
    threat: ThreatIntelligence
  ): Promise<void> {
    await pubSub.publish(PubSubEvent.CRITICAL_THREAT_DISCOVERED, threat);
  }

  /**
   * Publishes a response action execution event.
   *
   * @param pubSub PubSub instance
   * @param anomalyId ID of anomaly being responded to
   * @param action Response action executed
   */
  static async publishResponseActionExecuted(
    pubSub: PubSub,
    anomalyId: string,
    action: {
      id: string;
      type: string;
      description: string;
      status: string;
      result?: string;
      executedAt?: Date;
      error?: string;
    }
  ): Promise<void> {
    const eventKey = `${PubSubEvent.RESPONSE_ACTION_EXECUTED}:${anomalyId}`;
    await pubSub.publish(eventKey, action);
  }
}
