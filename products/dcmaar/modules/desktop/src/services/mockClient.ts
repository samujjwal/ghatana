import { mockApi } from '../mocks/mockData';

/**
 * Lightweight wrapper mimicking network latency for mocked data.
 * Modules can import specific fetchers to keep React Query integration uniform.
 */

export const dashboardClient = {
  fetchOverview: mockApi.fetchDashboard,
};

export const metricsClient = {
  fetchCatalogue: mockApi.fetchMetricsCatalogue,
  fetchSeries: mockApi.fetchMetricsSeries,
};

export const eventsClient = {
  fetchEvents: mockApi.fetchEvents,
};

export const commandsClient = {
  fetchCommands: mockApi.fetchCommands,
};

export const copilotClient = {
  fetchRecommendations: mockApi.fetchCopilot,
};

export const policyClient = {
  fetchPolicies: mockApi.fetchPolicies,
};

export const settingsClient = {
  fetchSettings: mockApi.fetchSettings,
};

export const diagnosticsClient = {
  fetchDiagnostics: mockApi.fetchDiagnostics,
};

export const controlHubClient = {
  fetchDefaults: mockApi.fetchControlHubDefaults,
};

export const auditClient = {
  fetchAudit: mockApi.fetchAuditLog,
};

export const agentClient = {
  fetchDetails: mockApi.fetchAgentDetail,
};

export const reportsClient = {
  fetchReports: mockApi.fetchReports,
};
