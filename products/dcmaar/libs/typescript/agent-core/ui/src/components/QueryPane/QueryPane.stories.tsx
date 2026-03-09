import type { Meta, StoryObj } from '@storybook/react';
import { QueryPane } from './QueryPane';
import type { QueryResult } from './QueryPane';

const meta: Meta<typeof QueryPane> = {
  title: 'Components/QueryPane',
  component: QueryPane,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
  argTypes: {
    serverUrl: {
      control: 'text',
      description: 'URL of the NLQ server',
    },
  },
};

export default meta;
type Story = StoryObj<typeof meta>;

// Mock query result for demonstration
const mockQueryResult: QueryResult = {
  queryId: 'demo-001',
  intent: {
    metric: 'error_rate',
    aggregation: 'avg',
    timeRange: {
      start: '2024-01-01T00:00:00Z',
      end: '2024-01-02T00:00:00Z',
      label: 'last 24h',
    },
    filters: { service: 'api' },
    groupBy: ['service'],
    confidence: 0.85,
    originalText: 'show error rate for service-api in last 24 hours',
    parsedBy: 'grammar',
  },
  generatedSQL: {
    sql: "SELECT toStartOfInterval(timestamp, INTERVAL 5 MINUTE) as time_bucket, avg(error_rate) as error_rate FROM events WHERE service = 'api' AND timestamp >= '2024-01-01 00:00:00' AND timestamp <= '2024-01-02 00:00:00' GROUP BY time_bucket ORDER BY time_bucket ASC LIMIT 1000",
    parameters: {},
    tables: ['events'],
    columns: ['timestamp', 'error_rate', 'service'],
    functions: ['avg', 'toStartOfInterval'],
    rationale: 'Query analyzes error_rate metric using avg aggregation over time range last 24h filtered by service=api grouped by time_bucket.',
    safety: {
      approved: true,
      violations: [],
      riskLevel: 'LOW',
    },
  },
  data: [
    ['2024-01-01T00:00:00Z', 0.02],
    ['2024-01-01T00:05:00Z', 0.03],
    ['2024-01-01T00:10:00Z', 0.01],
    ['2024-01-01T00:15:00Z', 0.04],
    ['2024-01-01T00:20:00Z', 0.02],
  ],
  columns: ['time_bucket', 'error_rate'],
  rowCount: 5,
  executionTime: 145,
  chartConfig: {
    type: 'line',
    xAxis: 'time_bucket',
    yAxis: ['error_rate'],
    series: ['error_rate'],
    title: 'Error Rate Over Time',
    description: 'Average error rate for service-api',
    annotations: [],
  },
  success: true,
  timestamp: '2024-01-01T10:30:00Z',
};

const mockExamples = [
  'show error rate for service-api in last 24 hours',
  'average response time grouped by service in last week',
  'cpu usage above 80% for production hosts',
  'memory utilization trending up in last 3 days',
  'count alerts by severity since yesterday',
  'database connections for mysql service',
];

// Mock query submission function
const mockQuerySubmit = async (query: string): Promise<QueryResult> => {
  // Simulate API delay
  await new Promise(resolve => setTimeout(resolve, 1000));
  
  // Return mock result with query-specific modifications
  return {
    ...mockQueryResult,
    intent: {
      ...mockQueryResult.intent,
      originalText: query,
    },
  };
};

export const Default: Story = {
  args: {
    examples: mockExamples,
    onQuerySubmit: mockQuerySubmit,
    serverUrl: 'http://localhost:8081',
  },
};

export const WithCustomServer: Story = {
  args: {
    examples: mockExamples,
    onQuerySubmit: mockQuerySubmit,
    serverUrl: 'https://dcmaar-api.example.com',
  },
};

export const Minimal: Story = {
  args: {
    onQuerySubmit: mockQuerySubmit,
  },
};

export const WithManyExamples: Story = {
  args: {
    examples: [
      ...mockExamples,
      'disk usage over 90% grouped by host',
      'network latency between us-east and us-west',
      'deployment events for team-backend today',
      'incident count compared to last month',
      'show spike in error rate after deploy-v2.1',
      'failed requests grouped by status code',
      'average build time for ci/cd pipeline',
      'queue length for message broker',
      'cache hit rate below 70% in last hour',
    ],
    onQuerySubmit: mockQuerySubmit,
  },
};