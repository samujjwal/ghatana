import {
  render,
  screen,
  waitFor,
  fireEvent,
} from '@/test-utils/test-utils';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { UnifiedLeftRail } from '../UnifiedLeftRail';
import { railService } from '../../../services/rail/RailServiceClient';
import type { RailContext } from '../UnifiedLeftRail.types';

// Mock the rail service
vi.mock('../../../services/rail/RailServiceClient', () => ({
  railService: {
    getComponents: vi.fn(),
    getInfrastructure: vi.fn(),
    getHistory: vi.fn(),
    getFiles: vi.fn(),
    getDataSources: vi.fn(),
    getSuggestions: vi.fn(),
    getFavorites: vi.fn(),
  },
}));

const mockContext: RailContext = {
  mode: 'design',
  role: 'architect',
  phase: 'prototype',
  projectType: 'web-app',
};

describe('UnifiedLeftRail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders correctly and defaults to Components panel based on context or default', async () => {
    // Mock response for components
    (railService.getComponents as unknown).mockResolvedValue([
      {
        id: 'c1',
        name: 'Test Component',
        category: 'Test',
        tags: [],
        usage: 10,
      },
    ]);

    render(
      <UnifiedLeftRail context={mockContext} nodes={[]} selectedNodeIds={[]} />
    );

    // Initial render might show icons.
    // We need to click the icon to expand if it's collapsed, unless state atom says open.
    // Assuming default state might be closed or open depending on atom default.
    // But testing library renders what is there.
    // If collapsed, we see icons.

    // Check for Components icon (or label if we can find it by title)
    const componentsBtn = screen.getByTitle('Components');
    expect(componentsBtn).toBeInTheDocument();

    // Click to open
    fireEvent.click(componentsBtn);

    // Now panel should be visible
    expect(await screen.findByText('📦 Components')).toBeInTheDocument();

    // Check if service was called
    await waitFor(() => {
      expect(railService.getComponents).toHaveBeenCalled();
    });

    // Check if data is rendered
    expect(await screen.findByText('Test Component')).toBeInTheDocument();
  });

  it('switches to Infrastructure panel and fetches data', async () => {
    (railService.getInfrastructure as unknown).mockResolvedValue([
      {
        id: 'i1',
        name: 'Test Infra',
        type: 'compute',
        status: 'running',
        cost: 100,
      },
    ]);

    render(
      <UnifiedLeftRail context={mockContext} nodes={[]} selectedNodeIds={[]} />
    );

    const infraBtn = screen.getByTitle('Infrastructure');
    fireEvent.click(infraBtn);

    expect(await screen.findByText('☁️ Infrastructure')).toBeInTheDocument();

    await waitFor(() => {
      expect(railService.getInfrastructure).toHaveBeenCalled();
    });

    expect(await screen.findByText('Test Infra')).toBeInTheDocument();
  });
});
