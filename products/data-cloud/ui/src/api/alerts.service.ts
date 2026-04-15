/**
 * Alerts Service
 *
 * REST + SSE client for Data-Cloud operational alerts.
 *
 * @doc.type service
 * @doc.purpose Typed client for alerts management API
 * @doc.layer frontend
 * @doc.pattern Service
 */

import { apiClient } from '../lib/api/client';
import type { AlertRule } from '../components/alerts/AlertRuleForm';

export const ALERTS_UNSUPPORTED_MESSAGE = 'Alert management APIs are not exposed by the current Data Cloud launcher API.';

function unsupportedAlertsOperation<T>(message: string = ALERTS_UNSUPPORTED_MESSAGE): Promise<T> {
  return Promise.reject(new Error(message));
}

function createInertEventSource(): EventSource {
  return {
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
    close: () => undefined,
    dispatchEvent: () => false,
    onerror: null,
    onmessage: null,
    onopen: null,
    readyState: EventSource.CLOSED,
    url: '',
    withCredentials: false,
  } as EventSource;
}

export type AlertSeverity = 'critical' | 'warning' | 'info';
export type AlertStatus = 'active' | 'acknowledged' | 'resolved';

export interface Alert {
  id: string;
  title: string;
  description: string;
  severity: AlertSeverity;
  status: AlertStatus;
  source: string;
  createdAt: string;
  acknowledgedAt?: string;
  resolvedAt?: string;
}

export interface AlertGroup {
  id: string;
  title: string;
  rootCause: string;
  alertIds: string[];
  aiConfidence: number;
  suggestedAction: string;
  suggestedActionType: 'auto' | 'manual';
}

export interface ResolutionSuggestion {
  id: string;
  alertId: string;
  suggestion: string;
  confidence: number;
  canAutoResolve: boolean;
  steps?: string[];
}

export interface AlertQueryParams {
  severity?: AlertSeverity;
  status?: AlertStatus;
  tenantId?: string;
  limit?: number;
  offset?: number;
}

export class AlertsService {
  /** List active and historical alerts */
  async getAlerts(params: AlertQueryParams = {}): Promise<Alert[]> {
    void params;
    return unsupportedAlertsOperation<Alert[]>();
  }

  /** Acknowledge an alert */
  async acknowledgeAlert(alertId: string): Promise<Alert> {
    void alertId;
    return unsupportedAlertsOperation<Alert>();
  }

  /** Resolve an alert */
  async resolveAlert(alertId: string): Promise<Alert> {
    void alertId;
    return unsupportedAlertsOperation<Alert>();
  }

  /** Get AI-detected correlated alert groups */
  async getAlertGroups(): Promise<AlertGroup[]> {
    return unsupportedAlertsOperation<AlertGroup[]>();
  }

  /** Auto-resolve a correlated alert group */
  async resolveGroup(groupId: string): Promise<void> {
    void groupId;
    return unsupportedAlertsOperation<void>();
  }

  /** Get AI resolution suggestions for active alerts */
  async getResolutionSuggestions(): Promise<ResolutionSuggestion[]> {
    return unsupportedAlertsOperation<ResolutionSuggestion[]>();
  }

  /** Apply an AI resolution suggestion */
  async applySuggestion(suggestionId: string): Promise<void> {
    void suggestionId;
    return unsupportedAlertsOperation<void>();
  }

  /** Open an SSE stream for live alert events */
  openStream(): EventSource {
    return createInertEventSource();
  }

  // ==================== Alert Rules (B5) ====================

  /** List all configured alert rules */
  async listAlertRules(): Promise<AlertRule[]> {
    return unsupportedAlertsOperation<AlertRule[]>();
  }

  /** Create a new alert rule */
  async createAlertRule(rule: Omit<AlertRule, 'id'>): Promise<AlertRule> {
    void rule;
    return unsupportedAlertsOperation<AlertRule>();
  }

  /** Update an existing alert rule */
  async updateAlertRule(ruleId: string, rule: Partial<AlertRule>): Promise<AlertRule> {
    void ruleId;
    void rule;
    return unsupportedAlertsOperation<AlertRule>();
  }

  /** Delete an alert rule */
  async deleteAlertRule(ruleId: string): Promise<void> {
    void ruleId;
    return unsupportedAlertsOperation<void>();
  }
}

export const alertsService = new AlertsService();

export default alertsService;
