import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TestWrapper } from '../test-utils/wrapper';
import { alertsSurfaceBoundary } from '@/components/common/unsupportedSurfaceRegistry';
import { ALERTS_UNSUPPORTED_MESSAGE } from '@/lib/runtime-boundaries';

const { mockAlertsService } = vi.hoisted(() => ({
  mockAlertsService: {
    getAlerts: vi.fn(),
    getAlertGroups: vi.fn(),
    getResolutionSuggestions: vi.fn(),
    listAlertRules: vi.fn(),
    acknowledgeAlert: vi.fn(),
    resolveAlert: vi.fn(),
    resolveGroup: vi.fn(),
    applySuggestion: vi.fn(),
    openStream: vi.fn(),
    createAlertRule: vi.fn(),
    updateAlertRule: vi.fn(),
  },
}));

vi.mock('../../api/alerts.service', () => ({
  ALERTS_UNSUPPORTED_MESSAGE,
  alertsService: mockAlertsService,
}));

import { AlertsPage } from '../../pages/AlertsPage';

const sampleAlerts = [
  {
    id: 'alert-1',
    title: 'Kafka backlog spike',
    description: 'Consumer lag exceeded the threshold for the orders stream.',
    severity: 'critical',
    status: 'active',
    source: 'kafka-monitor',
    createdAt: '2026-04-14T10:00:00Z',
  },
  {
    id: 'alert-2',
    title: 'Schema registry latency',
    description: 'Registry responses are slower than expected.',
    severity: 'warning',
    status: 'active',
    source: 'schema-registry',
    createdAt: '2026-04-14T10:05:00Z',
  },
];

const sampleGroups = [
  {
    id: 'group-1',
    title: 'Orders pipeline degradation',
    rootCause: 'Kafka consumer saturation',
    alertIds: ['alert-1', 'alert-2'],
    aiConfidence: 0.91,
    suggestedAction: 'Scale the orders consumer deployment and reprocess lagging partitions.',
    suggestedActionType: 'auto',
  },
];

const sampleSuggestions = [
  {
    id: 'suggestion-1',
    alertId: 'alert-1',
    suggestion: 'Restart unhealthy consumers and replay the affected partition offsets.',
    confidence: 0.88,
    canAutoResolve: true,
    steps: ['Restart consumer', 'Verify lag is dropping'],
  },
];

const sampleRules = [
  {
    id: 'rule-1',
    name: 'Kafka lag',
    description: 'Escalate when backlog spikes',
    enabled: true,
    severity: 'critical',
    conditionType: 'threshold',
    metric: 'queue_depth',
    operator: 'gt',
    threshold: 1000,
    duration: 5,
    channels: ['email'],
  },
];

describe('AlertsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAlertsService.getAlerts.mockResolvedValue(sampleAlerts);
    mockAlertsService.getAlertGroups.mockResolvedValue(sampleGroups);
    mockAlertsService.getResolutionSuggestions.mockResolvedValue(sampleSuggestions);
    mockAlertsService.listAlertRules.mockResolvedValue(sampleRules);
    mockAlertsService.acknowledgeAlert.mockResolvedValue(sampleAlerts[0]);
    mockAlertsService.resolveAlert.mockResolvedValue({ ...sampleAlerts[0], status: 'resolved' });
    mockAlertsService.resolveGroup.mockResolvedValue(undefined);
    mockAlertsService.applySuggestion.mockResolvedValue(undefined);
    mockAlertsService.openStream.mockReturnValue({
      addEventListener: vi.fn(),
      close: vi.fn(),
    } as unknown as EventSource);
    mockAlertsService.createAlertRule.mockResolvedValue({ id: 'rule-1' });
    mockAlertsService.updateAlertRule.mockResolvedValue({ id: 'rule-1' });
  });

  it('renders the alerts shell with grouped-view triage controls', async () => {
    render(<AlertsPage />, { wrapper: TestWrapper });
    await screen.findByText('Kafka backlog spike');

    expect(screen.getByRole('heading', { name: 'Alerts' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Review Rules/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Grouped by root cause/i })).toBeInTheDocument();
    expect(screen.getByText(/1 enabled rules/i)).toBeInTheDocument();
  });

  it('shows an alert truth panel with route, stream, and coverage metrics', async () => {
    render(<AlertsPage />, { wrapper: TestWrapper });

    expect(await screen.findByText(/Route Coverage/i)).toBeInTheDocument();
    expect(screen.getByText(/7\/7 live/i)).toBeInTheDocument();
    expect(screen.getByText(/Grouped Coverage/i)).toBeInTheDocument();
    expect(screen.getByText(/Suggestion Coverage/i)).toBeInTheDocument();
  });

  it('shows canonical alert count summary cards', async () => {
    render(<AlertsPage />, { wrapper: TestWrapper });

    await waitFor(() => {
      expect(screen.getAllByText('Critical').length).toBeGreaterThan(0);
      expect(screen.getAllByText('Warning').length).toBeGreaterThan(0);
      expect(screen.getByText('Total Active')).toBeInTheDocument();
      expect(document.body.textContent).toContain('2');
    });
  });

  it('renders AI grouped triage from canonical alert groups', async () => {
    render(<AlertsPage />, { wrapper: TestWrapper });

    await screen.findByTestId('alerts-grouped-section');
    expect(screen.getAllByText(/Grouped by root cause/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/Orders pipeline degradation/i)).toBeInTheDocument();
    expect(screen.getByText(/91% confidence/i)).toBeInTheDocument();
  });

  it('toggles alert group expand/collapse to show correlated alerts', async () => {
    const user = userEvent.setup();
    render(<AlertsPage />, { wrapper: TestWrapper });

    await screen.findByText('Orders pipeline degradation');
    expect(screen.queryByText('Suggested Action')).not.toBeInTheDocument();

    await user.click(screen.getByText('Orders pipeline degradation'));
    expect(screen.getByText('Suggested Action')).toBeInTheDocument();
    expect(screen.getAllByText('Kafka backlog spike').length).toBeGreaterThan(0);
  });

  it('opens the alert rule form from the canonical create action', async () => {
    const user = userEvent.setup();
    render(<AlertsPage />, { wrapper: TestWrapper });

    await screen.findByText('Kafka backlog spike');
    await user.click(screen.getByRole('button', { name: /review rules/i }));
    await user.click(screen.getByRole('button', { name: /create alert rule/i }));

    expect(screen.getAllByText(/create alert rule/i).length).toBeGreaterThan(0);
  });

  it('opens an existing alert rule from the live rules snapshot', async () => {
    const user = userEvent.setup();
    render(<AlertsPage />, { wrapper: TestWrapper });

    await screen.findByText('Kafka backlog spike');
    await user.click(screen.getByRole('button', { name: /review rules/i }));
    await user.click(screen.getByRole('button', { name: /Kafka lag/i }));

    expect(screen.getByDisplayValue('Kafka lag')).toBeInTheDocument();
  });

  it('keeps rule management collapsed until explicitly requested', async () => {
    const user = userEvent.setup();
    render(<AlertsPage />, { wrapper: TestWrapper });

    await screen.findByText('Kafka backlog spike');
    expect(screen.queryByRole('button', { name: /Create Alert Rule/i })).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /Review Rules/i }));

    expect(screen.getByRole('button', { name: /Create Alert Rule/i })).toBeInTheDocument();
  });

  it('switches to list view and applies AI suggestions through the canonical service', async () => {
    const user = userEvent.setup();
    render(<AlertsPage />, { wrapper: TestWrapper });

    await screen.findByText('Orders pipeline degradation');
    await user.click(screen.getByRole('button', { name: /list view/i }));

    expect(await screen.findByText(/AI Resolution Suggestions/i)).toBeInTheDocument();
    expect(screen.getByText(/Restart unhealthy consumers/i)).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: /apply/i }));

    await waitFor(() => {
      expect(mockAlertsService.applySuggestion).toHaveBeenCalledWith('suggestion-1');
    });
  });

  it('requires a reason before acknowledging an alert through the GuardedAction gate', async () => {
    const user = userEvent.setup();
    render(<AlertsPage />, { wrapper: TestWrapper });

    // Switch to list view where per-alert action buttons are visible
    await screen.findByText('Kafka backlog spike');
    await user.click(screen.getByRole('button', { name: /list view/i }));
    await screen.findByText('Kafka backlog spike');

    // Click the Acknowledge trigger — should open the GuardedAction dialog
    const ackButtons = screen.getAllByRole('button', { name: /^acknowledge$/i });
    await user.click(ackButtons[0]!);

    // Dialog should now be open
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toBeInTheDocument();
    expect(screen.getByText(/Acknowledge alert/i)).toBeInTheDocument();

    // Confirm button should be disabled until a reason is typed
    const confirmButtons = screen.getAllByRole('button', { name: /^acknowledge$/i });
    const dialogConfirm = confirmButtons.find((btn) => btn.closest('[role="dialog"]'));
    expect(dialogConfirm).toBeDisabled();

    // Type a reason and confirm
    const reasonTextarea = screen.getByPlaceholderText(/provide a brief reason/i);
    await user.type(reasonTextarea, 'Investigating with the on-call team.');
    expect(dialogConfirm).not.toBeDisabled();

    await user.click(dialogConfirm!);

    await waitFor(() => {
      expect(mockAlertsService.acknowledgeAlert).toHaveBeenCalledWith('alert-1');
    });
  });

  it('requires a reason before resolving an alert through the GuardedAction gate', async () => {
    const user = userEvent.setup();
    render(<AlertsPage />, { wrapper: TestWrapper });

    await screen.findByText('Kafka backlog spike');
    await user.click(screen.getByRole('button', { name: /list view/i }));
    await screen.findByText('Kafka backlog spike');

    // Click the Resolve trigger for the first active alert
    const resolveButtons = screen.getAllByRole('button', { name: /^resolve$/i });
    await user.click(resolveButtons[0]!);

    // Dialog should be open with the guard impact text
    await screen.findByRole('dialog');
    expect(screen.getByText(/Resolve alert/i)).toBeInTheDocument();

    // Confirm button disabled without a reason
    const dialogConfirmButtons = screen.getAllByRole('button', { name: /^resolve$/i });
    const dialogConfirm = dialogConfirmButtons.find((btn) => btn.closest('[role="dialog"]'));
    expect(dialogConfirm).toBeDisabled();

    // Type a reason
    const reasonTextarea = screen.getByPlaceholderText(/provide a brief reason/i);
    await user.type(reasonTextarea, 'Root cause identified and remediation deployed.');

    await user.click(dialogConfirm!);

    await waitFor(() => {
      expect(mockAlertsService.resolveAlert).toHaveBeenCalledWith('alert-1');
    });
  });

  it('cancelling the GuardedAction dialog does not invoke the alert service', async () => {
    const user = userEvent.setup();
    render(<AlertsPage />, { wrapper: TestWrapper });

    await screen.findByText('Kafka backlog spike');
    await user.click(screen.getByRole('button', { name: /list view/i }));
    await screen.findByText('Kafka backlog spike');

    const ackButtons = screen.getAllByRole('button', { name: /^acknowledge$/i });
    await user.click(ackButtons[0]!);

    const dialog = await screen.findByRole('dialog');

    const cancelButtons = within(dialog).getAllByRole('button', { name: /cancel/i });
    const textCancelButton = cancelButtons.find((button) => button.textContent?.trim().toLowerCase() === 'cancel');
    await user.click(textCancelButton ?? cancelButtons[0]!);

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(mockAlertsService.acknowledgeAlert).not.toHaveBeenCalled();
  });

  it('surfaces an honest unsupported-state banner when the launcher does not expose alerts routes', async () => {
    mockAlertsService.getAlerts.mockRejectedValueOnce(new Error(ALERTS_UNSUPPORTED_MESSAGE));
    mockAlertsService.getAlertGroups.mockRejectedValueOnce(new Error(ALERTS_UNSUPPORTED_MESSAGE));
    mockAlertsService.getResolutionSuggestions.mockRejectedValueOnce(new Error(ALERTS_UNSUPPORTED_MESSAGE));

    render(<AlertsPage />, { wrapper: TestWrapper });

    expect(
      await screen.findByText(/Operator-facing alert triage remains unavailable until the launcher exposes canonical alert routes/i),
    ).toBeInTheDocument();
    expect(screen.getByText(alertsSurfaceBoundary.summary)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Create Alert Rule/i })).not.toBeInTheDocument();
    expect(screen.queryByText(/AI-Detected Correlations/i)).not.toBeInTheDocument();
  });
});
