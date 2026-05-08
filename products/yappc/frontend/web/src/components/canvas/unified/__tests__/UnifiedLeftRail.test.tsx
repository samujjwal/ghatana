import {
  render,
  screen,
  waitFor,
  fireEvent,
} from '@/test-utils/test-utils';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { UnifiedLeftRail } from '../UnifiedLeftRail';
import { railService } from '../../../../services/rail/RailServiceClient';
import type { RailContext } from '../UnifiedLeftRail.types';

// Mock the rail service
vi.mock('../../../../services/rail/RailServiceClient', () => ({
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
  mode: 'diagram',
  role: 'Architect',
  phase: 'SHAPE',
  projectType: 'web',
};

const mockInfraContext: RailContext = {
  mode: 'diagram',
  role: 'Architect',
  phase: 'SHAPE',
  projectType: 'web',
};

describe('UnifiedLeftRail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders correctly and opens the default Assets panel for diagram mode', async () => {

    render(
      <UnifiedLeftRail context={mockContext} nodes={[]} selectedNodeIds={[]} />
    );

    const assetsBtn = screen.getByTitle('Assets');
    expect(assetsBtn).toBeInTheDocument();

    fireEvent.click(assetsBtn);

    expect(await screen.findAllByText(/Assets/)).toBeTruthy();
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

    // Panel header/tab should show Infrastructure (text may appear in header + tab)
    expect(await screen.findAllByText(/Infrastructure/)).toBeTruthy();

    await waitFor(() => {
      expect(railService.getInfrastructure).toHaveBeenCalled();
    });

    expect(await screen.findByText('Test Infra')).toBeInTheDocument();
  });

  it('surfaces externally hovered preview nodes in the Layers panel', async () => {
    render(
      <UnifiedLeftRail
        context={mockContext}
        nodes={[
          {
            id: 'node-preview-hover',
            type: 'button',
            position: { x: 24, y: 48 },
            size: { width: 120, height: 44 },
            data: { label: 'Preview CTA' },
          },
        ]}
        selectedNodeIds={[]}
        hoveredNodeId="node-preview-hover"
      />,
    );

    fireEvent.click(screen.getByTitle('Layers'));
    fireEvent.click(await screen.findByText('button'));

    expect(await screen.findByTestId('layer-row-node-preview-hover')).toHaveAttribute(
      'data-preview-hovered',
      'true',
    );
    expect(screen.getByTestId('canvas-outline-minimap-marker-node-preview-hover')).toHaveAttribute(
      'data-preview-hovered',
      'true',
    );
    expect(screen.getByText('Preview CTA')).toBeInTheDocument();
  });
});
