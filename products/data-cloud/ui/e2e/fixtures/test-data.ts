/**
 * Test Data Fixtures
 * 
 * Provides mock data for E2E tests.
 * 
 * @doc.type fixture
 * @doc.purpose Test data for E2E testing
 * @doc.layer testing
 */

export const mockCollections = [
  {
    id: 'col-1',
    name: 'Products',
    description: 'Product catalog collection',
    schemaType: 'entity',
    status: 'active',
    entityCount: 1250,
    schema: {
      fields: [
        { name: 'name', type: 'string', required: true },
        { name: 'price', type: 'number', required: true },
        { name: 'description', type: 'string', required: false },
      ],
    },
    tags: ['catalog', 'products'],
    createdAt: new Date('2024-01-01').toISOString(),
    updatedAt: new Date('2024-01-15').toISOString(),
    createdBy: 'user-1',
  },
  {
    id: 'col-2',
    name: 'Customers',
    description: 'Customer data collection',
    schemaType: 'entity',
    status: 'active',
    entityCount: 5420,
    schema: {
      fields: [
        { name: 'email', type: 'email', required: true },
        { name: 'name', type: 'string', required: true },
        { name: 'phone', type: 'string', required: false },
      ],
    },
    tags: ['crm', 'customers'],
    createdAt: new Date('2024-01-05').toISOString(),
    updatedAt: new Date('2024-01-20').toISOString(),
    createdBy: 'user-1',
  },
];

export const mockWorkflows = [
  {
    id: 'wf-1',
    name: 'Data Export',
    description: 'Export data to external system',
    status: 'active',
    executionCount: 125,
    lastExecutedAt: new Date('2024-01-20T10:30:00').toISOString(),
    createdAt: new Date('2024-01-01').toISOString(),
    updatedAt: new Date('2024-01-15').toISOString(),
  },
  {
    id: 'wf-2',
    name: 'Data Sync',
    description: 'Sync data between systems',
    status: 'active',
    executionCount: 342,
    lastExecutedAt: new Date('2024-01-21T14:15:00').toISOString(),
    createdAt: new Date('2024-01-03').toISOString(),
    updatedAt: new Date('2024-01-18').toISOString(),
  },
];

export const mockExecutions = [
  {
    id: 'exec-1',
    workflowId: 'wf-1',
    status: 'completed',
    startedAt: new Date('2024-01-20T10:30:00').toISOString(),
    completedAt: new Date('2024-01-20T10:30:15').toISOString(),
    duration: 15000,
  },
  {
    id: 'exec-2',
    workflowId: 'wf-1',
    status: 'failed',
    startedAt: new Date('2024-01-19T09:15:00').toISOString(),
    completedAt: new Date('2024-01-19T09:15:30').toISOString(),
    duration: 30000,
    error: 'Connection timeout',
  },
];

export const mockAlerts = [
  {
    id: 'alert-1',
    title: 'High Error Rate',
    description: 'Error rate exceeded 5% threshold in wf-1 (Data Export)',
    severity: 'critical',
    status: 'active',
    source: 'workflow-monitor',
    createdAt: new Date('2024-01-21T08:00:00').toISOString(),
  },
  {
    id: 'alert-2',
    title: 'Storage Capacity Warning',
    description: 'Storage utilisation at 82% — consider scaling or archiving',
    severity: 'warning',
    status: 'active',
    source: 'infrastructure',
    createdAt: new Date('2024-01-20T16:45:00').toISOString(),
  },
  {
    id: 'alert-3',
    title: 'Schema Drift Detected',
    description: 'Collection col-2 schema diverged from registered contract; auto-resolved',
    severity: 'info',
    status: 'resolved',
    source: 'governance',
    createdAt: new Date('2024-01-19T12:00:00').toISOString(),
    resolvedAt: new Date('2024-01-19T12:05:00').toISOString(),
  },
];

export const mockAlertGroups = [
  {
    id: 'group-1',
    title: 'Workflow Reliability Cluster',
    rootCause: 'Data Export pipeline retries spiked after source timeout errors.',
    alertIds: ['alert-1', 'alert-2'],
    aiConfidence: 0.94,
    suggestedAction: 'Pause downstream retries and investigate the source connector timeout.',
    suggestedActionType: 'manual',
  },
];

export const mockAlertSuggestions = [
  {
    id: 'suggestion-1',
    alertId: 'alert-1',
    suggestion: 'Temporarily widen the retry backoff and page the workflow owner.',
    confidence: 0.91,
    canAutoResolve: false,
    steps: ['Increase retry backoff to 5m', 'Notify workflow owner in Slack'],
  },
  {
    id: 'suggestion-2',
    alertId: 'alert-2',
    suggestion: 'Archive stale parquet partitions to recover storage headroom.',
    confidence: 0.82,
    canAutoResolve: true,
    steps: ['Archive partitions older than 30 days', 'Re-run storage capacity check'],
  },
];

export const mockAlertRules = [
  {
    id: 'rule-1',
    name: 'High Workflow Error Rate',
    description: 'Trigger when workflow failure rate exceeds the operational SLO.',
    enabled: true,
    severity: 'critical',
    conditionType: 'threshold',
    metric: 'workflow.error_rate',
    operator: 'gt',
    threshold: 5,
    duration: 15,
    channels: ['slack', 'pagerduty'],
    recipients: ['oncall@datacloud.example'],
  },
  {
    id: 'rule-2',
    name: 'Storage Capacity Warning',
    description: 'Warn when storage utilisation approaches archival thresholds.',
    enabled: true,
    severity: 'warning',
    conditionType: 'threshold',
    metric: 'storage.capacity_used_percent',
    operator: 'gte',
    threshold: 80,
    duration: 30,
    channels: ['email'],
    recipients: ['platform@datacloud.example'],
  },
];

export const mockEntities = [
  {
    id: 'ent-1',
    collectionId: 'col-1',
    data: {
      name: 'Product A',
      price: 99.99,
      description: 'High quality product',
    },
    createdAt: new Date('2024-01-10').toISOString(),
    updatedAt: new Date('2024-01-10').toISOString(),
  },
  {
    id: 'ent-2',
    collectionId: 'col-1',
    data: {
      name: 'Product B',
      price: 149.99,
      description: 'Premium product',
    },
    createdAt: new Date('2024-01-11').toISOString(),
    updatedAt: new Date('2024-01-11').toISOString(),
  },
];
