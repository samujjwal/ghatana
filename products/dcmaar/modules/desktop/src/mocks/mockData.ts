/**
 * Centralised fixtures for mocked backend data.
 * These mocks allow polished UI development without live services.
 */

import { subHours, subMinutes, addMinutes } from 'date-fns';

const now = new Date();

const iso = (date: Date) => date.toISOString();

const range = (count: number) => Array.from({ length: count }, (_, index) => index);

export const dashboardMetrics = {
  ingestThroughput: range(24).map((offset) => ({
    timestamp: iso(subHours(now, 24 - offset)),
    value: 900 + Math.round(Math.sin(offset / 3) * 120 + Math.random() * 40),
    unit: 'events/min',
  })),
  errorRate: range(24).map((offset) => ({
    timestamp: iso(subHours(now, 24 - offset)),
    value: Math.max(0, Math.round(Math.sin(offset / 4) * 4 + Math.random() * 2)),
    unit: 'errors/min',
  })),
  cpu: range(12).map((offset) => ({
    timestamp: iso(subHours(now, 12 - offset)),
    value: 40 + Math.round(Math.sin(offset / 2) * 20 + Math.random() * 5),
    unit: '%',
  })),
};

export const agentStatus = {
  connected: true,
  name: 'Edge Agent alpha-1',
  version: '2.8.17',
  lastHeartbeat: iso(subMinutes(now, 2)),
  uptimeSeconds: 86_400 * 5 + 3200,
  queue: {
    depth: 312,
    capacity: 1_000,
    highWatermark: 820,
    lowWatermark: 120,
  },
  exporters: [
    {
      id: 'elasticsearch',
      name: 'Elasticsearch Exporter',
      status: 'healthy' as const,
      lastSuccess: iso(subMinutes(now, 1)),
      lastError: null,
      latencyMsP95: 420,
    },
    {
      id: 'splunk',
      name: 'Splunk HEC',
      status: 'degraded' as const,
      lastSuccess: iso(subMinutes(now, 8)),
      lastError: 'Timeout: collector unreachable (45s)',
      latencyMsP95: 1_280,
    },
  ],
  plugins: [
    {
      id: 'sys-metrics',
      name: 'System Metrics',
      status: 'healthy' as const,
      version: '1.4.3',
      lastHeartbeat: iso(subMinutes(now, 3)),
    },
    {
      id: 'log-parser',
      name: 'Log Parser',
      status: 'active' as const,
      version: '0.9.9',
      lastHeartbeat: iso(subMinutes(now, 7)),
    },
  ],
};

export const extensionStatus = {
  connected: true,
  version: '1.2.0',
  latencyMs: 120,
  events: range(25).map((index) => ({
    id: `evt-${index + 1}`,
    type: index % 3 === 0 ? 'form_submit' : index % 2 === 0 ? 'click' : 'page_view',
    title: index % 3 === 0 ? 'Checkout form submission' : 'Admin dashboard navigation',
    url: 'https://console.example.com/ops',
    timestamp: iso(subMinutes(now, index * 2)),
    element: index % 2 === 0 ? '#confirm' : undefined,
  })),
};

export const recentEvents = range(20).map((index) => ({
  id: `INC-${1640 + index}`,
  timestamp: iso(subMinutes(now, index * 5)),
  severity: index % 5 === 0 ? 'critical' : index % 3 === 0 ? 'warning' : 'info',
  type: index % 4 === 0 ? 'ingest' : index % 2 === 0 ? 'performance' : 'policy',
  title:
    index % 4 === 0
      ? 'Agent ingest queue nearing capacity'
      : index % 2 === 0
        ? 'API latency increased'
        : 'Policy exception logged',
  message:
    index % 4 === 0
      ? 'Queue depth breached 80% of capacity. Exporter backlog rising.'
      : index % 2 === 0
        ? 'P95 latency for /metrics increased to 1.2s.'
        : 'Command execution requires additional approval.',
  source: index % 3 === 0 ? 'agent/alpha-1' : 'command-center',
  correlationId: `corr-${1345 + index}`,
  commandId: index % 4 === 0 ? `cmd-${280 + index}` : undefined,
}));

export const metricsCatalogue = [
  {
    id: 'cpu_usage',
    name: 'CPU Utilisation',
    unit: '%',
    description: 'Average CPU utilisation across managed agents.',
    tags: ['performance', 'agent'],
  },
  {
    id: 'ingest_throughput',
    name: 'Ingest Throughput',
    unit: 'events/min',
    description: 'Processed telemetry events per minute.',
    tags: ['ingest', 'throughput'],
  },
  {
    id: 'error_rate',
    name: 'Error Rate',
    unit: 'errors/min',
    description: 'Application level error rate over time.',
    tags: ['reliability'],
  },
];

export const metricsSeries = metricsCatalogue.reduce<Record<string, Array<{ timestamp: string; value: number }>>>(
  (acc, metric, index) => {
    const points = range(72).map((point) => {
      const value =
        metric.id === 'cpu_usage'
          ? 45 + Math.sin(point / 3 + index) * 20 + Math.random() * 6
          : metric.id === 'ingest_throughput'
            ? 850 + Math.sin(point / 4 + index) * 180 + Math.random() * 50
            : Math.max(0, 5 + Math.sin(point / 6 + index) * 3 + Math.random() * 1.2);

      return {
        timestamp: iso(subMinutes(now, (72 - point) * 20)),
        value: Number(value.toFixed(2)),
      };
    });

    acc[metric.id] = points;
    return acc;
  },
  {},
);

export const commandCatalogue = range(6).map((index) => ({
  id: `cmd-${280 + index}`,
  name:
    index === 0
      ? 'Restart telemetry exporter'
      : index === 1
        ? 'Purge ingest queue'
        : index === 2
          ? 'Scale collector pool'
          : index === 3
            ? 'Enable debug logging'
            : index === 4
              ? 'Rotate access token'
              : 'Run health diagnostics',
  category: index % 2 === 0 ? 'Remediation' : 'Maintenance',
  riskLevel: index % 3 === 0 ? 'medium' : index % 4 === 0 ? 'high' : 'low',
  estimatedDurationMinutes: index % 3 === 0 ? 8 : 3,
  requiresApproval: index % 3 === 0,
  description:
    index === 0
      ? 'Gracefully restarts exporter service and drains inflight batches.'
      : index === 1
        ? 'Purges queued telemetry messages and re-requests from sources.'
        : index === 2
          ? 'Adds additional collectors to absorb increased throughput.'
          : index === 3
            ? 'Temporarily enables verbose logging for targeted analysis.'
            : index === 4
              ? 'Rotates service access token with automatic propagation.'
              : 'Executes end-to-end diagnostics workflow on target agent.',
  tags: ['automation', 'playbook'],
  parameters: [
    {
      id: 'targetAgent',
      label: 'Target Agent',
      type: 'select' as const,
      options: ['alpha-1', 'alpha-2', 'edge-west'],
      required: true,
    },
    {
      id: 'confirmation',
      label: 'Confirmation',
      type: 'toggle' as const,
      required: index % 3 === 0,
      helperText: 'Acknowledge impact to live traffic.',
    },
  ],
}));

export const commandExecutions = range(8).map((index) => ({
  id: `exec-${index + 1}`,
  commandId: commandCatalogue[index % commandCatalogue.length].id,
  operator: index % 2 === 0 ? 'anna.ops' : 'raul.sre',
  status: index % 3 === 0 ? 'failed' : index % 4 === 0 ? 'pending' : 'succeeded',
  startedAt: iso(subMinutes(now, index * 14 + 5)),
  completedAt: index % 3 === 0 ? null : iso(subMinutes(now, index * 14)),
  target: index % 2 === 0 ? 'agent/alpha-1' : 'cluster/collector-west',
  correlationId: `corr-${1480 + index}`,
  logs: [
    'Validating prerequisites…',
    'Dispatching command to edge agent…',
    index % 3 === 0 ? 'Exporter restart timed out after 90s.' : 'Exporter restarted successfully.',
  ],
}));

export const copilotRecommendations = [
  {
    id: 'rec-1',
    title: 'Mitigate rising ingest latency',
    confidence: 0.86,
    summary:
      'Exporter queue depth reached 82%. Recommend scaling collectors and flushing backlog with controlled drains.',
    risk: 'medium',
    actions: [commandCatalogue[2], commandCatalogue[1]],
    updatedAt: iso(subMinutes(now, 6)),
  },
  {
    id: 'rec-2',
    title: 'Investigate policy exception spikes',
    confidence: 0.74,
    summary:
      'Recent policy evaluation failures tied to new command templates. Suggest running diagnostics and reviewing latest policy diff.',
    risk: 'low',
    actions: [commandCatalogue[5]],
    updatedAt: iso(subMinutes(now, 18)),
  },
];

export const policySubjects = [
  { id: 'anna.ops', name: 'Anna (Ops Lead)', roles: ['ops-admin'] },
  { id: 'raul.sre', name: 'Raul (SRE)', roles: ['sre'] },
  { id: 'megan.audit', name: 'Megan (Audit)', roles: ['audit-reviewer'] },
];

export const policyCatalogue = range(5).map((index) => ({
  id: `policy-${index + 1}`,
  name:
    index === 0
      ? 'Command approvals'
      : index === 1
        ? 'Read-only operations'
        : index === 2
          ? 'Incident management'
          : index === 3
            ? 'Audit retention'
            : 'Sensitive exporters',
  status: index % 2 === 0 ? 'Published' : 'Draft',
  version: `v${1 + Math.floor(index / 2)}.${index % 2 === 0 ? 0 : 1}`,
  updatedAt: iso(subHours(now, index * 8)),
  owner: index % 2 === 0 ? 'anna.ops' : 'megan.audit',
  tags: index % 2 === 0 ? ['command', 'approval'] : ['governance'],
}));

export const policyDiff = {
  added: ['rules[3].conditions[2]'],
  removed: [],
  modified: [
    { path: 'rules[1].effect', oldValue: 'deny', newValue: 'allow' },
    { path: 'rules[2].conditions[0].threshold', oldValue: 3, newValue: 2 },
  ],
};

export const settingsSnapshot = {
  general: {
    theme: 'dark' as const,
    refreshIntervalSeconds: 30,
    timezone: 'UTC',
  },
  integrations: {
    apiBaseUrl: 'https://api.mock.dcmaar.io',
    telemetryCollector: 'wss://collectors.dcmaar.io/stream',
    notificationEmail: 'alerts@dcmaar.io',
  },
  notifications: {
    push: true,
    email: true,
    sms: false,
  },
  advanced: {
    enableVerboseLogging: false,
    collectAnonymisedUXMetrics: true,
    featureFlags: ['copilot_v2', 'command_audit_sharing'],
  },
};

export const diagnosticsSnapshot = {
  health: {
    api: 'healthy',
    agentBridge: 'degraded',
    extensionBridge: 'healthy',
    database: 'healthy',
  },
  identity: {
    subject: 'anna.ops',
    roles: ['ops-admin', 'command-approver'],
    tenant: 'west-coast',
  },
  correlationId: `corr-${1710}`,
  prometheusSample: [
    '# HELP dcmaar_ingest_queue_depth Current ingest queue depth',
    '# TYPE dcmaar_ingest_queue_depth gauge',
    'dcmaar_ingest_queue_depth{agent="alpha-1"} 312',
    'dcmaar_ingest_queue_depth{agent="alpha-2"} 186',
  ].join('\n'),
  recentFailures: [
    {
      timestamp: iso(subMinutes(now, 12)),
      subsystem: 'Exporter::Splunk',
      message: 'Timeout after 45s while sending batch',
      correlationId: `corr-${1648}`,
    },
    {
      timestamp: iso(subMinutes(now, 44)),
      subsystem: 'Command::Approval',
      message: 'Approval request auto-expired',
      correlationId: `corr-${1620}`,
    },
  ],
};

export const auditLog = range(18).map((index) => ({
  id: `audit-${index + 1}`,
  timestamp: iso(subMinutes(now, index * 12)),
  user: index % 3 === 0 ? 'anna.ops' : index % 2 === 0 ? 'raul.sre' : 'megan.audit',
  action: index % 4 === 0 ? 'command.execute' : index % 3 === 0 ? 'config.apply' : 'policy.update',
  summary:
    index % 4 === 0
      ? 'Executed exporter restart on agent alpha-1'
      : index % 3 === 0
        ? 'Applied configuration bundle v23.4'
        : 'Updated command approval policy',
  correlationId: `corr-${1500 + index}`,
  details: {
    status: index % 5 === 0 ? 'failed' : 'succeeded',
    diff:
      index % 3 === 0
        ? [
            { path: 'queue.maxSize', from: 10_000, to: 12_000 },
            { path: 'exporters.splunk.enabled', from: true, to: true },
          ]
        : undefined,
  },
}));

export const agentDeepLinks = {
  metrics: metricsSeries,
  events: recentEvents.slice(0, 15),
  status: agentStatus,
  config: agentStatus.queue,
};

export const reportsWorkspace = range(6).map((index) => ({
  id: `report-${index + 1}`,
  name:
    index === 0
      ? 'Daily ingest summary'
      : index === 1
        ? 'Latency and error drill-down'
        : index === 2
          ? 'Policy exception digest'
          : index === 3
            ? 'Command execution outcomes'
            : index === 4
              ? 'Agent plugin health'
              : 'Exporter performance overview',
  schedule: index % 2 === 0 ? 'Daily 09:00 UTC' : 'Weekly Monday 08:00 UTC',
  owner: index % 2 === 0 ? 'ops-team' : 'sre-team',
  recipients: ['ops@dcmaar.io', 'sre@dcmaar.io'],
  lastGenerated: iso(subHours(now, index * 6 + 2)),
  filters: {
    tenant: 'west-coast',
    severity: index % 2 === 0 ? 'warning+' : 'info+',
  },
}));

export const timelineRecommendations = [
  {
    timestamp: iso(subMinutes(now, 15)),
    title: 'Queue depth breach detected',
    description: 'Queue depth for exporter Splunk reached 82% capacity.',
    action: commandCatalogue[1],
  },
  {
    timestamp: iso(subMinutes(now, 32)),
    title: 'Policy diff pending approval',
    description: 'Command approvals policy awaiting review by audit.',
    action: null,
  },
];

export type PromiseOr<T> = Promise<T> | T;

const withLatency = <T>(value: T, delay = 300): Promise<T> =>
  new Promise((resolve) => setTimeout(() => resolve(value), delay));

export const mockApi = {
  fetchDashboard: () =>
    withLatency({
      metrics: dashboardMetrics,
      agent: agentStatus,
      extension: extensionStatus,
      events: recentEvents.slice(0, 6),
      recommendations: copilotRecommendations,
    }),
  fetchMetricsCatalogue: () => withLatency(metricsCatalogue),
  fetchMetricsSeries: (metricId: string) => withLatency(metricsSeries[metricId] ?? []),
  fetchEvents: () => withLatency(recentEvents),
  fetchCommands: () =>
    withLatency({
      catalogue: commandCatalogue,
      executions: commandExecutions,
    }),
  fetchCopilot: () =>
    withLatency({
      recommendations: copilotRecommendations,
      incidents: timelineRecommendations,
    }),
  fetchPolicies: () =>
    withLatency({
      subjects: policySubjects,
      catalogue: policyCatalogue,
      diff: policyDiff,
    }),
  fetchSettings: () => withLatency(settingsSnapshot),
  fetchDiagnostics: () => withLatency(diagnosticsSnapshot),
  fetchControlHubDefaults: () =>
    withLatency({
      editor: JSON.stringify(
        {
          exporters: {
            elastic: { enabled: true, batchSize: 5_000 },
            splunk: { enabled: true, batchSize: 2_000 },
          },
          queue: agentStatus.queue,
        },
        null,
        2,
      ),
      diff: policyDiff,
    }),
  fetchAuditLog: () => withLatency(auditLog),
  fetchAgentDetail: () => withLatency(agentDeepLinks),
  fetchReports: () => withLatency(reportsWorkspace),
};

export type MockApi = typeof mockApi;
