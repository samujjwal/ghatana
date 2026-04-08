/**
 * IntegrationHub Component Tests
 */

import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { IntegrationHub } from '../IntegrationHub';
import type { Integration } from '../../../services/integrations/IntegrationService';

function createIntegration(overrides: Partial<Integration> = {}): Integration {
  return {
    id: 'int-1',
    name: 'GitHub',
    category: 'vcs',
    status: 'connected',
    description: 'GitHub version control',
    config: {},
    connectedAt: new Date().toISOString(),
    lastSyncAt: new Date().toISOString(),
    ...overrides,
  };
}

describe('IntegrationHub', () => {
  it('should render header', () => {
    render(<IntegrationHub integrations={[]} />);
    expect(screen.getByText('Integrations')).toBeDefined();
  });

  it('should show connected count', () => {
    const integrations = [
      createIntegration({ id: '1', status: 'connected' }),
      createIntegration({ id: '2', status: 'disconnected', name: 'Slack' }),
    ];
    render(<IntegrationHub integrations={integrations} />);
    expect(screen.getByText('1/2 connected')).toBeDefined();
  });

  it('should show empty state when no integrations', () => {
    render(<IntegrationHub integrations={[]} />);
    expect(screen.getByText(/No integrations configured/)).toBeDefined();
  });

  it('should render integration cards', () => {
    const integrations = [createIntegration()];
    render(<IntegrationHub integrations={integrations} />);
    expect(screen.getByText('GitHub')).toBeDefined();
    expect(screen.getByText('GitHub version control')).toBeDefined();
    expect(screen.getByText('Version Control')).toBeDefined();
  });

  it('should show Connected badge for connected integration', () => {
    render(<IntegrationHub integrations={[createIntegration()]} />);
    expect(screen.getByText('Connected')).toBeDefined();
  });

  it('should show Disconnected badge for disconnected integration', () => {
    render(<IntegrationHub integrations={[createIntegration({ status: 'disconnected' })]} />);
    expect(screen.getByText('Disconnected')).toBeDefined();
  });

  it('should show Pending badge for pending integration', () => {
    render(<IntegrationHub integrations={[createIntegration({ status: 'pending' })]} />);
    expect(screen.getByText('Pending')).toBeDefined();
  });

  it('should show Error badge for errored integration', () => {
    render(<IntegrationHub integrations={[createIntegration({ status: 'error' })]} />);
    expect(screen.getByText('Error')).toBeDefined();
  });

  it('should show Connect button for disconnected integration', () => {
    const onConnect = vi.fn();
    render(
      <IntegrationHub
        integrations={[createIntegration({ status: 'disconnected' })]}
        onConnect={onConnect}
      />,
    );
    fireEvent.click(screen.getByText('Connect'));
    expect(onConnect).toHaveBeenCalledWith('int-1');
  });

  it('should show Disconnect button for connected integration', () => {
    const onDisconnect = vi.fn();
    render(
      <IntegrationHub
        integrations={[createIntegration({ status: 'connected' })]}
        onDisconnect={onDisconnect}
      />,
    );
    fireEvent.click(screen.getByText('Disconnect'));
    expect(onDisconnect).toHaveBeenCalledWith('int-1');
  });

  it('should call onRemove when remove clicked', () => {
    const onRemove = vi.fn();
    render(
      <IntegrationHub
        integrations={[createIntegration()]}
        onRemove={onRemove}
      />,
    );
    // Remove button uses trash icon (last button)
    const buttons = screen.getAllByRole('button');
    const removeBtn = buttons[buttons.length - 1];
    fireEvent.click(removeBtn);
    expect(onRemove).toHaveBeenCalledWith('int-1');
  });

  it('should call onRefresh when refresh clicked', () => {
    const onRefresh = vi.fn();
    render(<IntegrationHub integrations={[]} onRefresh={onRefresh} />);
    const refreshBtn = screen.getAllByRole('button')[0];
    fireEvent.click(refreshBtn);
    expect(onRefresh).toHaveBeenCalled();
  });

  it('should show last sync time', () => {
    const integration = createIntegration({ lastSyncAt: '2026-04-01T12:00:00Z' });
    render(<IntegrationHub integrations={[integration]} />);
    expect(screen.getByText(/Last sync/)).toBeDefined();
  });

  it('should accept className prop', () => {
    const { container } = render(<IntegrationHub integrations={[]} className="custom" />);
    expect(container.firstElementChild?.classList.contains('custom')).toBe(true);
  });
});
