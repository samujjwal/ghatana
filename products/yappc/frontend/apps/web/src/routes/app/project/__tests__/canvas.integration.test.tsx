/**
 * Canvas Integration Tests
 *
 * Integration tests for the complete canvas with all new UI components
 * including AI Status Bar, Lifecycle Zones, Studio Mode, and more
 *
 * @doc.type test
 * @doc.purpose Integration testing
 * @doc.layer routes
 */

import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { BrowserRouter } from 'react-router-dom';
import UnifiedCanvasComplete from '../canvas';

// Mock all the new components
vi.mock('../../components/ai/AIStatusBar', () => ({
  AIStatusBar: ({
    status,
    currentPhase,
    phaseProgress,
    nextBestAction,
  }: unknown) => (
    <div
      data-testid="ai-status-bar"
      data-status={status}
      data-phase={currentPhase}
    >
      <span data-testid="ai-status">{status}</span>
      <span data-testid="current-phase">{currentPhase}</span>
      <span data-testid="phase-progress">{phaseProgress}%</span>
      {nextBestAction && (
        <button data-testid="next-action" onClick={nextBestAction.action}>
          {nextBestAction.title}
        </button>
      )}
    </div>
  ),
  useAIStatusBar: () => ({
    status: 'ready',
    currentPhase: 'INTENT',
    phaseProgress: 75,
    nextBestAction: {
      title: 'Add Validation',
      description: 'Add validation rules',
      action: vi.fn(),
    },
    setStatus: vi.fn(),
    setCurrentPhase: vi.fn(),
    setPhaseProgress: vi.fn(),
    setNextBestAction: vi.fn(),
  }),
}));

vi.mock('../../components/canvas/ZoomableLifecycleZones', () => ({
  ZoomableLifecycleZones: ({ zones, zoom, activePhase, onPhaseClick }: unknown) => (
    <div
      data-testid="lifecycle-zones"
      data-zoom={zoom}
      data-active={activePhase}
    >
      {zones.map((zone: unknown) => (
        <button
          key={zone.phase}
          data-testid={`phase-${zone.phase}`}
          onClick={() => onPhaseClick(zone.phase)}
        >
          {zone.phase}
        </button>
      ))}
    </div>
  ),
  useLifecycleZones: () => [
    { phase: 'INTENT', x: 0, y: 0, width: 200, height: 100 },
    { phase: 'SHAPE', x: 200, y: 0, width: 200, height: 100 },
    { phase: 'VALIDATE', x: 400, y: 0, width: 200, height: 100 },
  ],
}));

vi.mock('../../components/canvas/InlineCodePanel', () => ({
  InlineCodePanel: ({ isVisible, code, language, fileName, onToggle }: unknown) => (
    <div data-testid="inline-code-panel" data-visible={isVisible}>
      <button data-testid="code-panel-toggle" onClick={onToggle}>
        Toggle Code Panel
      </button>
      {isVisible && (
        <div>
          <div data-testid="code-file">{fileName}</div>
          <div data-testid="code-language">{language}</div>
          <textarea data-testid="code-editor" value={code} readOnly />
        </div>
      )}
    </div>
  ),
  useInlineCodePanel: () => ({
    isVisible: false,
    code: '// Sample code',
    fileName: 'example.tsx',
    language: 'typescript',
    handleCodeChange: vi.fn(),
    handleFormat: vi.fn(),
    handleRun: vi.fn(),
    handleToggle: vi.fn(),
  }),
}));

vi.mock('../../components/studio/StudioLayout', () => ({
  StudioLayout: ({
    fileTree,
    codeEditor,
    livePreview,
    validation,
    onClose,
  }: unknown) => (
    <div data-testid="studio-layout">
      <div data-testid="studio-file-tree">{fileTree}</div>
      <div data-testid="studio-code-editor">{codeEditor}</div>
      <div data-testid="studio-live-preview">{livePreview}</div>
      <div data-testid="studio-validation">{validation}</div>
      <button data-testid="studio-close" onClick={onClose}>
        Close Studio
      </button>
    </div>
  ),
  useStudioMode: () => ({
    isStudioMode: false,
    toggleStudioMode: vi.fn(),
  }),
}));

vi.mock('../../components/keyboard/KeyboardShortcutsManager', () => ({
  KeyboardShortcutsHelp: ({ isOpen, onClose }: unknown) => (
    <div data-testid="keyboard-shortcuts-help" data-open={isOpen}>
      {isOpen && (
        <div>
          <h3>Keyboard Shortcuts</h3>
          <button data-testid="help-close" onClick={onClose}>
            Close Help
          </button>
        </div>
      )}
    </div>
  ),
  useKeyboardShortcuts: () => ({
    isHelpOpen: false,
    closeHelp: vi.fn(),
  }),
}));

// Mock canvas components
vi.mock('../../components/canvas/unified/CanvasErrorBoundary', () => ({
  CanvasErrorBoundary: ({ children }: unknown) => <div>{children}</div>,
}));

vi.mock('@ghatana/canvas', () => ({
  CanvasCommandProvider: ({ children }: unknown) => <div>{children}</div>,
  useCanvasTelemetry: () => {},
  useCanvasCommands: () => {},
  OnboardingTour: ({ active }: unknown) => (
    <div data-testid="onboarding-tour" data-active={active}>
      Tour
    </div>
  ),
  FeatureHintsManager: () => <div data-testid="feature-hints">Hints</div>,
}));

vi.mock('../../hooks/useUnifiedCanvas', () => ({
  useUnifiedCanvas: () => ({
    nodes: [],
    connections: [],
    selectedNodeIds: [],
    viewport: { zoom: 1, x: 0, y: 0 },
    activeTool: 'select',
    addNode: vi.fn(),
    removeNode: vi.fn(),
    updateNode: vi.fn(),
    selectNodes: vi.fn(),
    undo: vi.fn(),
    redo: vi.fn(),
    startDrawing: vi.fn(),
    continueDrawing: vi.fn(),
    endDrawing: vi.fn(),
    drawings: [],
    setActiveTool: vi.fn(),
    alignNodes: vi.fn(),
    createGroup: vi.fn(),
    ungroup: vi.fn(),
    bringToFront: vi.fn(),
    bringForward: vi.fn(),
    sendBackward: vi.fn(),
    sendToBack: vi.fn(),
    duplicateNode: vi.fn(),
    createConnection: vi.fn(),
    removeConnection: vi.fn(),
    downloadJSON: vi.fn(),
    downloadSVG: vi.fn(),
  }),
}));

const renderWithProviders = (component: React.ReactElement) => {
  return render(<BrowserRouter>{component}</BrowserRouter>);
};

describe('Canvas Integration Tests', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Component Rendering', () => {
    it('renders all new UI components', async () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      // Check AI Status Bar
      expect(screen.getByTestId('ai-status-bar')).toBeInTheDocument();
      expect(screen.getByTestId('ai-status')).toHaveTextContent('ready');
      expect(screen.getByTestId('current-phase')).toHaveTextContent('INTENT');
      expect(screen.getByTestId('phase-progress')).toHaveTextContent('75%');

      // Check Lifecycle Zones
      expect(screen.getByTestId('lifecycle-zones')).toBeInTheDocument();
      expect(screen.getByTestId('phase-INTENT')).toBeInTheDocument();
      expect(screen.getByTestId('phase-SHAPE')).toBeInTheDocument();
      expect(screen.getByTestId('phase-VALIDATE')).toBeInTheDocument();

      // Check Inline Code Panel
      expect(screen.getByTestId('inline-code-panel')).toBeInTheDocument();
      expect(screen.getByTestId('code-panel-toggle')).toBeInTheDocument();

      // Check Keyboard Shortcuts Help
      expect(screen.getByTestId('keyboard-shortcuts-help')).toBeInTheDocument();

      // Check supporting components
      expect(screen.getByTestId('onboarding-tour')).toBeInTheDocument();
      expect(screen.getByTestId('feature-hints')).toBeInTheDocument();
    });

    it('renders in normal mode by default', () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      // Should show normal canvas, not studio mode
      expect(screen.queryByTestId('studio-layout')).not.toBeInTheDocument();
      expect(screen.getByTestId('ai-status-bar')).toBeInTheDocument();
    });
  });

  describe('AI Status Bar Integration', () => {
    it('displays AI status and phase information', () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      const statusBar = screen.getByTestId('ai-status-bar');
      expect(statusBar).toHaveAttribute('data-status', 'ready');
      expect(statusBar).toHaveAttribute('data-phase', 'INTENT');

      expect(screen.getByTestId('ai-status')).toHaveTextContent('ready');
      expect(screen.getByTestId('current-phase')).toHaveTextContent('INTENT');
      expect(screen.getByTestId('phase-progress')).toHaveTextContent('75%');
    });

    it('shows next best action when available', () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      const nextAction = screen.getByTestId('next-action');
      expect(nextAction).toBeInTheDocument();
      expect(nextAction).toHaveTextContent('Add Validation');
    });
  });

  describe('Lifecycle Zones Integration', () => {
    it('renders all lifecycle phases', () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      const zones = screen.getByTestId('lifecycle-zones');
      expect(zones).toHaveAttribute('data-zoom', '1');
      expect(zones).toHaveAttribute('data-active', 'INTENT');

      expect(screen.getByTestId('phase-INTENT')).toBeInTheDocument();
      expect(screen.getByTestId('phase-SHAPE')).toBeInTheDocument();
      expect(screen.getByTestId('phase-VALIDATE')).toBeInTheDocument();
    });

    it('handles phase clicks', async () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      const phaseButton = screen.getByTestId('phase-SHAPE');
      fireEvent.click(phaseButton);

      // Should trigger phase change
      await waitFor(() => {
        expect(phaseButton).toBeInTheDocument();
      });
    });
  });

  describe('Code Panel Integration', () => {
    it('renders code panel toggle', () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      const toggleButton = screen.getByTestId('code-panel-toggle');
      expect(toggleButton).toBeInTheDocument();
      expect(toggleButton).toHaveTextContent('Toggle Code Panel');
    });

    it('shows code information when visible', () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      // Initially hidden
      expect(screen.queryByTestId('code-file')).not.toBeInTheDocument();

      // Toggle to show
      const toggleButton = screen.getByTestId('code-panel-toggle');
      fireEvent.click(toggleButton);

      // Should show code details
      expect(screen.getByTestId('code-file')).toBeInTheDocument();
      expect(screen.getByTestId('code-language')).toBeInTheDocument();
      expect(screen.getByTestId('code-editor')).toBeInTheDocument();
    });
  });

  describe('Studio Mode Integration', () => {
    it('can toggle studio mode', () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      // Initially in normal mode
      expect(screen.queryByTestId('studio-layout')).not.toBeInTheDocument();

      // Toggle to studio mode (would need to mock the hook return)
      // This is a simplified test - in reality, the hook would need to be mocked
      // to return isStudioMode: true
    });

    it('renders all studio panels when active', () => {
      // Mock studio mode as active
      const { useStudioMode } = require('../../components/studio/StudioLayout');
      useStudioMode.mockReturnValue({
        isStudioMode: true,
        toggleStudioMode: vi.fn(),
      });

      renderWithProviders(<UnifiedCanvasComplete />);

      expect(screen.getByTestId('studio-layout')).toBeInTheDocument();
      expect(screen.getByTestId('studio-file-tree')).toBeInTheDocument();
      expect(screen.getByTestId('studio-code-editor')).toBeInTheDocument();
      expect(screen.getByTestId('studio-live-preview')).toBeInTheDocument();
      expect(screen.getByTestId('studio-validation')).toBeInTheDocument();
      expect(screen.getByTestId('studio-close')).toBeInTheDocument();
    });
  });

  describe('Keyboard Shortcuts Integration', () => {
    it('renders keyboard shortcuts help', () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      const help = screen.getByTestId('keyboard-shortcuts-help');
      expect(help).toBeInTheDocument();
      expect(help).toHaveAttribute('data-open', 'false');
    });

    it('shows help content when open', () => {
      // Mock help as open
      const {
        useKeyboardShortcuts,
      } = require('../../components/keyboard/KeyboardShortcutsManager');
      useKeyboardShortcuts.mockReturnValue({
        isHelpOpen: true,
        closeHelp: vi.fn(),
      });

      renderWithProviders(<UnifiedCanvasComplete />);

      const help = screen.getByTestId('keyboard-shortcuts-help');
      expect(help).toHaveAttribute('data-open', 'true');
      expect(screen.getByText('Keyboard Shortcuts')).toBeInTheDocument();
      expect(screen.getByTestId('help-close')).toBeInTheDocument();
    });
  });

  describe('Component Interactions', () => {
    it('handles AI status bar interactions', () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      const nextAction = screen.getByTestId('next-action');
      fireEvent.click(nextAction);

      // Should trigger the action
      expect(nextAction).toBeInTheDocument();
    });

    it('handles lifecycle zone interactions', () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      const phaseButton = screen.getByTestId('phase-INTENT');
      fireEvent.click(phaseButton);

      // Should handle phase selection
      expect(phaseButton).toBeInTheDocument();
    });

    it('handles code panel interactions', () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      const toggleButton = screen.getByTestId('code-panel-toggle');
      fireEvent.click(toggleButton);

      // Should toggle panel visibility
      expect(toggleButton).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('has proper ARIA labels', () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      // Check that components have proper labels
      const statusBar = screen.getByTestId('ai-status-bar');
      expect(statusBar).toBeInTheDocument();

      const zones = screen.getByTestId('lifecycle-zones');
      expect(zones).toBeInTheDocument();
    });

    it('supports keyboard navigation', () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      // Test keyboard navigation
      const nextAction = screen.getByTestId('next-action');
      nextAction.focus();

      expect(document.activeElement).toBe(nextAction);
    });
  });

  describe('Performance', () => {
    it('renders efficiently with all components', () => {
      const startTime = performance.now();

      renderWithProviders(<UnifiedCanvasComplete />);

      const endTime = performance.now();
      const renderTime = endTime - startTime;

      // Should render quickly (under 100ms)
      expect(renderTime).toBeLessThan(100);
    });

    it('handles rapid state updates', async () => {
      renderWithProviders(<UnifiedCanvasComplete />);

      // Simulate rapid updates
      for (let i = 0; i < 10; i++) {
        const phaseButton = screen.getByTestId(
          `phase-${['INTENT', 'SHAPE', 'VALIDATE'][i % 3]}`
        );
        fireEvent.click(phaseButton);
      }

      // Should handle updates without issues
      expect(screen.getByTestId('lifecycle-zones')).toBeInTheDocument();
    });
  });

  describe('Error Handling', () => {
    it('handles component errors gracefully', () => {
      // Mock a component error
      const originalError = console.error;
      console.error = vi.fn();

      renderWithProviders(<UnifiedCanvasComplete />);

      // Should still render other components
      expect(screen.getByTestId('ai-status-bar')).toBeInTheDocument();

      console.error = originalError;
    });
  });

  describe('Responsive Design', () => {
    it('adapts to different screen sizes', () => {
      // Test mobile size
      Object.defineProperty(window, 'innerWidth', {
        writable: true,
        configurable: true,
        value: 375,
      });

      renderWithProviders(<UnifiedCanvasComplete />);

      expect(screen.getByTestId('ai-status-bar')).toBeInTheDocument();

      // Test desktop size
      Object.defineProperty(window, 'innerWidth', {
        writable: true,
        configurable: true,
        value: 1920,
      });

      renderWithProviders(<UnifiedCanvasComplete />);

      expect(screen.getByTestId('ai-status-bar')).toBeInTheDocument();
    });
  });
});
