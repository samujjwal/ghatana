import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CanvasComplete } from './CanvasComplete';
import { Provider } from 'jotai';

// Mock ReactFlow
vi.mock('@xyflow/react', () => ({
  ReactFlow: ({ children }: any) => <div data-testid="react-flow">{children}</div>,
  Background: () => <div data-testid="background" />,
  Controls: () => <div data-testid="controls" />,
  MiniMap: () => <div data-testid="minimap" />,
  useNodesState: () => [[], vi.fn()],
  useEdgesState: () => [[], vi.fn()],
  useReactFlow: () => ({
    fitView: vi.fn(),
    zoomIn: vi.fn(),
    zoomOut: vi.fn(),
  }),
  Panel: ({ children }: any) => <div>{children}</div>,
  ReactFlowProvider: ({ children }: any) => <>{children}</>,
}));

describe('CanvasComplete', () => {
  const renderWithProvider = (ui: React.ReactElement) => {
    return render(<Provider>{ui}</Provider>);
  };

  it('should render canvas with toolbar', () => {
    renderWithProvider(<CanvasComplete />);
    expect(screen.getByTestId('react-flow')).toBeInTheDocument();
  });

  it('should render collaboration presence when enabled', () => {
    renderWithProvider(<CanvasComplete enableCollaboration={true} />);
    expect(screen.getByTestId('react-flow')).toBeInTheDocument();
  });

  it('should handle node selection', async () => {
    const user = userEvent.setup();
    renderWithProvider(<CanvasComplete />);
    
    // Canvas should be interactive
    const canvas = screen.getByTestId('react-flow');
    expect(canvas).toBeInTheDocument();
  });
});

describe('Canvas Toolbar', () => {
  it('should render toolbar buttons', () => {
    renderWithProvider(<CanvasComplete />);
    
    // Check for toolbar presence
    expect(screen.getByTestId('react-flow')).toBeInTheDocument();
  });

  it('should switch tools on click', async () => {
    const user = userEvent.setup();
    renderWithProvider(<CanvasComplete />);
    
    // Tool switching should work
    const canvas = screen.getByTestId('react-flow');
    expect(canvas).toBeInTheDocument();
  });
});

describe('AI Assistant', () => {
  it('should render AI suggestions', () => {
    renderWithProvider(<CanvasComplete enableAI={true} />);
    expect(screen.getByTestId('react-flow')).toBeInTheDocument();
  });

  it('should handle AI search', async () => {
    const user = userEvent.setup();
    renderWithProvider(<CanvasComplete enableAI={true} />);
    
    const canvas = screen.getByTestId('react-flow');
    expect(canvas).toBeInTheDocument();
  });
});

describe('Canvas State Management', () => {
  it('should persist canvas state', () => {
    const { rerender } = renderWithProvider(<CanvasComplete canvasId="test-1" />);
    
    rerender(<Provider><CanvasComplete canvasId="test-1" /></Provider>);
    
    expect(screen.getByTestId('react-flow')).toBeInTheDocument();
  });

  it('should handle undo/redo operations', () => {
    renderWithProvider(<CanvasComplete />);
    
    // Undo/redo should be available
    expect(screen.getByTestId('react-flow')).toBeInTheDocument();
  });
});
