/**
 * Integration Service
 *
 * Manages third-party integrations and plugin lifecycle.
 * Provides registration, configuration, and health checks.
 *
 * @doc.type service
 * @doc.purpose Third-party integration management
 * @doc.layer product
 * @doc.pattern Service Layer
 */

// ============================================================================
// Types
// ============================================================================

export type IntegrationStatus = 'connected' | 'disconnected' | 'error' | 'pending';
export type IntegrationCategory = 'vcs' | 'ci-cd' | 'chat' | 'monitoring' | 'custom';

export interface IntegrationConfig {
  [key: string]: string | number | boolean;
}

export interface Integration {
  id: string;
  name: string;
  category: IntegrationCategory;
  status: IntegrationStatus;
  description: string;
  iconUrl?: string;
  config: IntegrationConfig;
  connectedAt?: string;
  lastSyncAt?: string;
  webhookUrl?: string;
}

export interface IntegrationEvent {
  id: string;
  integrationId: string;
  type: 'sync' | 'webhook' | 'error' | 'config-change';
  message: string;
  timestamp: number;
  metadata?: Record<string, unknown>;
}

export interface IntegrationHealth {
  integrationId: string;
  latency: number;       // ms
  uptime: number;        // 0-1
  errorRate: number;     // 0-1
  lastChecked: number;
}

// ============================================================================
// Registry
// ============================================================================

const STORAGE_KEY = 'yappc-integrations';

function loadIntegrations(): Integration[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as Integration[]) : [];
  } catch {
    return [];
  }
}

function saveIntegrations(integrations: Integration[]): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(integrations));
  } catch {
    // silently degrade
  }
}

// ============================================================================
// Public API
// ============================================================================

export function getIntegrations(): Integration[] {
  return loadIntegrations();
}

export function getIntegration(id: string): Integration | undefined {
  return loadIntegrations().find((i) => i.id === id);
}

export function registerIntegration(
  integration: Omit<Integration, 'id' | 'status' | 'connectedAt'>,
): Integration {
  const all = loadIntegrations();

  const existing = all.find((i) => i.name === integration.name);
  if (existing) {
    throw new Error(`Integration "${integration.name}" already registered`);
  }

  const created: Integration = {
    ...integration,
    id: `int-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    status: 'pending',
  };

  all.push(created);
  saveIntegrations(all);
  return created;
}

export function connectIntegration(id: string, config: IntegrationConfig): Integration {
  const all = loadIntegrations();
  const idx = all.findIndex((i) => i.id === id);
  if (idx === -1) throw new Error(`Integration ${id} not found`);

  all[idx] = {
    ...all[idx],
    config: { ...all[idx].config, ...config },
    status: 'connected',
    connectedAt: new Date().toISOString(),
    lastSyncAt: new Date().toISOString(),
  };

  saveIntegrations(all);
  return all[idx];
}

export function disconnectIntegration(id: string): Integration {
  const all = loadIntegrations();
  const idx = all.findIndex((i) => i.id === id);
  if (idx === -1) throw new Error(`Integration ${id} not found`);

  all[idx] = { ...all[idx], status: 'disconnected' };
  saveIntegrations(all);
  return all[idx];
}

export function removeIntegration(id: string): void {
  const all = loadIntegrations().filter((i) => i.id !== id);
  saveIntegrations(all);
}

export function checkHealth(integration: Integration): IntegrationHealth {
  // Simulated health check — production would call real endpoints
  return {
    integrationId: integration.id,
    latency: integration.status === 'connected' ? Math.round(Math.random() * 200) : -1,
    uptime: integration.status === 'connected' ? 0.99 : 0,
    errorRate: integration.status === 'error' ? 0.15 : 0.01,
    lastChecked: Date.now(),
  };
}

export function resetIntegrations(): void {
  saveIntegrations([]);
}
