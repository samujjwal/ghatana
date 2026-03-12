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

import axios, { type AxiosInstance } from 'axios';

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
  private client: AxiosInstance;
  private baseURL: string;

  constructor(baseURL: string = '/api') {
    this.baseURL = baseURL;
    this.client = axios.create({
      baseURL,
      headers: { 'Content-Type': 'application/json' },
    });
  }

  /** List active and historical alerts */
  async getAlerts(params: AlertQueryParams = {}): Promise<Alert[]> {
    const { data } = await this.client.get<Alert[]>('/v1/alerts', { params });
    return data;
  }

  /** Acknowledge an alert */
  async acknowledgeAlert(alertId: string): Promise<Alert> {
    const { data } = await this.client.post<Alert>(`/v1/alerts/${alertId}/acknowledge`);
    return data;
  }

  /** Resolve an alert */
  async resolveAlert(alertId: string): Promise<Alert> {
    const { data } = await this.client.post<Alert>(`/v1/alerts/${alertId}/resolve`);
    return data;
  }

  /** Get AI-detected correlated alert groups */
  async getAlertGroups(): Promise<AlertGroup[]> {
    const { data } = await this.client.get<AlertGroup[]>('/v1/alerts/groups');
    return data;
  }

  /** Auto-resolve a correlated alert group */
  async resolveGroup(groupId: string): Promise<void> {
    await this.client.post(`/v1/alerts/groups/${groupId}/resolve`);
  }

  /** Get AI resolution suggestions for active alerts */
  async getResolutionSuggestions(): Promise<ResolutionSuggestion[]> {
    const { data } = await this.client.get<ResolutionSuggestion[]>('/v1/alerts/suggestions');
    return data;
  }

  /** Apply an AI resolution suggestion */
  async applySuggestion(suggestionId: string): Promise<void> {
    await this.client.post(`/v1/alerts/suggestions/${suggestionId}/apply`);
  }

  /** Open an SSE stream for live alert events */
  openStream(): EventSource {
    return new EventSource(`${this.baseURL}/dc/events/stream?eventType=alert.triggered`);
  }
}

export const alertsService = new AlertsService(
  import.meta.env.VITE_DC_API_URL ?? '/api',
);

export default alertsService;
