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
    return apiClient.get<Alert[]>('/alerts', { params });
  }

  /** Acknowledge an alert */
  async acknowledgeAlert(alertId: string): Promise<Alert> {
    return apiClient.post<Alert>(`/alerts/${alertId}/acknowledge`);
  }

  /** Resolve an alert */
  async resolveAlert(alertId: string): Promise<Alert> {
    return apiClient.post<Alert>(`/alerts/${alertId}/resolve`);
  }

  /** Get AI-detected correlated alert groups */
  async getAlertGroups(): Promise<AlertGroup[]> {
    return apiClient.get<AlertGroup[]>('/alerts/groups');
  }

  /** Auto-resolve a correlated alert group */
  async resolveGroup(groupId: string): Promise<void> {
    await apiClient.post<void>(`/alerts/groups/${groupId}/resolve`);
  }

  /** Get AI resolution suggestions for active alerts */
  async getResolutionSuggestions(): Promise<ResolutionSuggestion[]> {
    return apiClient.get<ResolutionSuggestion[]>('/alerts/suggestions');
  }

  /** Apply an AI resolution suggestion */
  async applySuggestion(suggestionId: string): Promise<void> {
    await apiClient.post<void>(`/alerts/suggestions/${suggestionId}/apply`);
  }

  /** Open an SSE stream for live alert events */
  openStream(): EventSource {
    return new EventSource(
      `${import.meta.env.VITE_API_URL ?? '/api/v1'}/events/stream?eventType=alert.triggered`,
    );
  }
}

export const alertsService = new AlertsService();

export default alertsService;
