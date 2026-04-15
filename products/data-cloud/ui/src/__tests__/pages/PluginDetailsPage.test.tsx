import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';

import { TestWrapper } from '../test-utils/wrapper';
import { PluginDetailsPage } from '../../pages/PluginDetailsPage';
import { pluginService, type Plugin } from '../../api/plugin.service';

const mockNavigate = vi.fn();
const mockUseParams = vi.fn(() => ({ id: 'plugin-1' }));

vi.mock('react-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router')>();
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useParams: () => mockUseParams(),
  };
});

vi.mock('../../api/plugin.service', () => ({
  pluginService: {
    getPlugin: vi.fn(),
  },
}));

vi.mock('../../components/plugins/PluginHealthMonitor', () => ({
  PluginHealthMonitor: () => <div>Plugin health monitor</div>,
}));

vi.mock('../../components/plugins/PluginVersionCompare', () => ({
  PluginVersionCompare: () => <div>Plugin version compare</div>,
}));

vi.mock('../../components/plugins/PluginDependencyGraph', () => ({
  PluginDependencyGraph: () => <div>Plugin dependency graph</div>,
}));

vi.mock('../../components/plugins/PluginPerformanceMetrics', () => ({
  PluginPerformanceMetrics: () => <div>Plugin performance metrics</div>,
}));

vi.mock('../../components/plugins/PluginLogsViewer', () => ({
  PluginLogsViewer: () => <div>Plugin logs viewer</div>,
}));

const SAMPLE_PLUGIN: Plugin = {
  id: 'plugin-1',
  metadata: {
    id: 'plugin-1',
    name: 'Alpha Connector',
    version: '1.4.0',
    author: 'Ghatana',
    description: 'Bundled connector for canonical plugin detail coverage',
    category: 'connector',
    license: 'Bundled',
    tags: ['crm', 'sync'],
  },
  status: 'active',
  installedAt: '2026-04-15T08:00:00Z',
  updatedAt: '2026-04-15T08:30:00Z',
  capabilities: [
    {
      id: 'cap-1',
      name: 'entity sync',
      description: 'Processes entity synchronization payloads.',
      type: 'processor',
    },
  ],
};

describe('PluginDetailsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockNavigate.mockReset();
    mockUseParams.mockReturnValue({ id: 'plugin-1' });
    vi.mocked(pluginService.getPlugin).mockResolvedValue(SAMPLE_PLUGIN);
  });

  it('renders canonical plugin metadata, capabilities, and bundled delivery guidance', async () => {
    render(<PluginDetailsPage />, { wrapper: TestWrapper });

    expect(await screen.findByText('Alpha Connector')).toBeInTheDocument();
    expect(screen.getAllByText('Bundled connector for canonical plugin detail coverage')).toHaveLength(2);
    expect(screen.getByText('Capabilities')).toBeInTheDocument();
    expect(screen.getAllByText('entity sync').length).toBeGreaterThan(0);
    expect(screen.getByText(/bundled plugins can only be toggled at runtime/i)).toBeInTheDocument();
    expect(screen.getByText(/deploy a new launcher build/i)).toBeInTheDocument();
  });

  it('navigates back to the canonical plugins inventory', async () => {
    render(<PluginDetailsPage />, { wrapper: TestWrapper });

    fireEvent.click(await screen.findByRole('button', { name: /back to plugins/i }));

    expect(mockNavigate).toHaveBeenCalledWith('/plugins');
  });

  it('shows the not-found state when the plugin lookup fails', async () => {
    vi.mocked(pluginService.getPlugin).mockRejectedValueOnce(new Error('missing plugin'));

    render(<PluginDetailsPage />, { wrapper: TestWrapper });

    expect(await screen.findByText('Plugin Not Found')).toBeInTheDocument();
    expect(screen.getByText(/doesn't exist or has been removed/i)).toBeInTheDocument();
  });
});