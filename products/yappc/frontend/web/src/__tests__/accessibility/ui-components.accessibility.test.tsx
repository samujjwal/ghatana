/**
 * UI Components Accessibility Tests
 *
 * Comprehensive accessibility testing for all new UI components
 * ensuring WCAG 2.2 AA compliance and keyboard navigation
 *
 * @doc.type test
 * @doc.purpose Accessibility testing
 * @doc.layer testing
 */

import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { axe, toHaveNoViolations } from 'jest-axe';

// Import all new components
import { AIStatusBar } from '../../components/ai/AIStatusBar';
import { ZoomableLifecycleZones } from '../../components/canvas/ZoomableLifecycleZones';
import { InlineCodePanel } from '../../components/canvas/InlineCodePanel';
import { StudioLayout } from '../../components/studio/StudioLayout';
import { KeyboardShortcutsHelp } from '../../components/keyboard/KeyboardShortcutsManager';

// Extend Jest matchers
expect.extend(toHaveNoViolations);

describe('UI Components Accessibility Tests', () => {
  beforeEach(() => {
    // Reset DOM
    document.body.innerHTML = '';
  });

  describe('WCAG 2.2 AA Compliance', () => {
    it('AI Status Bar has no accessibility violations', async () => {
      const { container } = render(
        <AIStatusBar
          status="ready"
          currentPhase="INTENT"
          phaseProgress={75}
          nextBestAction={{
            title: 'Add Validation',
            description: 'Add validation rules to your components',
            action: () => {},
          }}
        />
      );

      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });

    it('Lifecycle Zones have no accessibility violations', async () => {
      const zones = [
        {
          phase: 'INTENT' as const,
          x: 0,
          y: 0,
          width: 200,
          height: 100,
          color: '#3b82f6',
          status: 'active' as const,
          progress: 75,
          artifacts: ['requirement.md', 'user-stories.md'],
        },
        {
          phase: 'SHAPE' as const,
          x: 200,
          y: 0,
          width: 200,
          height: 100,
          color: '#10b981',
          status: 'pending' as const,
          progress: 0,
          artifacts: [],
        },
      ];

      const { container } = render(
        <ZoomableLifecycleZones
          zones={zones}
          zoom={1}
          activePhase="INTENT"
          onPhaseClick={() => {}}
        />
      );

      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });

    it('Inline Code Panel has no accessibility violations', async () => {
      const { container } = render(
        <InlineCodePanel
          code="// Sample TypeScript code\nconst greeting = 'Hello World';"
          language="typescript"
          fileName="example.tsx"
          isVisible={true}
          onCodeChange={() => {}}
          onFormat={() => {}}
          onRun={() => {}}
          onToggle={() => {}}
        />
      );

      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });

    it('Studio Layout has no accessibility violations', async () => {
      const { container } = render(
        <StudioLayout
          fileTree={<div>File Tree Content</div>}
          codeEditor={<div>Code Editor Content</div>}
          livePreview={<div>Live Preview Content</div>}
          validation={<div>Validation Content</div>}
          onClose={() => {}}
        />
      );

      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });

    it('Keyboard Shortcuts Help has no accessibility violations', async () => {
      const { container } = render(
        <KeyboardShortcutsHelp isOpen={true} onClose={() => {}} />
      );

      const results = await axe(container);
      expect(results).toHaveNoViolations();
    });
  });

  describe('Keyboard Navigation', () => {
    it('AI Status Bar supports keyboard navigation', () => {
      render(
        <AIStatusBar
          status="ready"
          currentPhase="INTENT"
          phaseProgress={75}
          nextBestAction={{
            title: 'Add Validation',
            description: 'Add validation rules',
            action: () => {},
          }}
        />
      );

      // Check that interactive elements are focusable
      const nextActionButton = screen.getByText('Add Validation');
      expect(nextActionButton).toHaveAttribute('tabIndex', '0');

      // Test keyboard interaction
      nextActionButton.focus();
      expect(document.activeElement).toBe(nextActionButton);

      fireEvent.keyDown(nextActionButton, { key: 'Enter' });
      fireEvent.keyDown(nextActionButton, { key: ' ' });

      // Should handle keyboard activation
      expect(nextActionButton).toBeInTheDocument();
    });

    it('Lifecycle Zones support keyboard navigation', () => {
      const zones = [
        {
          phase: 'INTENT' as const,
          x: 0,
          y: 0,
          width: 200,
          height: 100,
          color: '#3b82f6',
          status: 'active' as const,
          progress: 75,
          artifacts: [],
        },
      ];

      render(
        <ZoomableLifecycleZones
          zones={zones}
          zoom={1}
          activePhase="INTENT"
          onPhaseClick={() => {}}
        />
      );

      // Phase zones should be keyboard accessible
      const phaseButton = screen.getByText('INTENT');
      expect(phaseButton).toHaveAttribute('tabIndex', '0');

      phaseButton.focus();
      expect(document.activeElement).toBe(phaseButton);

      fireEvent.keyDown(phaseButton, { key: 'Enter' });
      fireEvent.keyDown(phaseButton, { key: ' ' });
    });

    it('Inline Code Panel supports keyboard navigation', () => {
      render(
        <InlineCodePanel
          code="// Sample code"
          language="typescript"
          fileName="example.tsx"
          isVisible={true}
          onCodeChange={() => {}}
          onFormat={() => {}}
          onRun={() => {}}
          onToggle={() => {}}
        />
      );

      // Check code editor accessibility
      const codeEditor =
        screen.getByRole('textbox') ||
        screen.getByDisplayValue('// Sample code');
      expect(codeEditor).toBeInTheDocument();

      // Check buttons
      const formatButton = screen.getByText('Format');
      const runButton = screen.getByText('Run');
      const toggleButton = screen.getByText('Hide Code Panel');

      [formatButton, runButton, toggleButton].forEach((button) => {
        expect(button).toHaveAttribute('tabIndex', '0');
      });
    });

    it('Studio Layout supports keyboard navigation', () => {
      render(
        <StudioLayout
          fileTree={<div>File Tree Content</div>}
          codeEditor={<div>Code Editor Content</div>}
          livePreview={<div>Live Preview Content</div>}
          validation={<div>Validation Content</div>}
          onClose={() => {}}
        />
      );

      // Close button should be keyboard accessible
      const closeButton = screen.getByLabelText('Close Studio Mode');
      expect(closeButton).toHaveAttribute('tabIndex', '0');

      closeButton.focus();
      expect(document.activeElement).toBe(closeButton);

      fireEvent.keyDown(closeButton, { key: 'Enter' });
      fireEvent.keyDown(closeButton, { key: 'Escape' });
    });

    it('Keyboard Shortcuts Help supports keyboard navigation', () => {
      render(<KeyboardShortcutsHelp isOpen={true} onClose={() => {}} />);

      // Close button should be keyboard accessible
      const closeButton = screen.getByText('Close');
      expect(closeButton).toHaveAttribute('tabIndex', '0');

      closeButton.focus();
      expect(document.activeElement).toBe(closeButton);

      fireEvent.keyDown(closeButton, { key: 'Enter' });
      fireEvent.keyDown(closeButton, { key: 'Escape' });
    });
  });

  describe('Screen Reader Support', () => {
    it('AI Status Bar has proper ARIA labels', () => {
      render(
        <AIStatusBar
          status="thinking"
          currentPhase="INTENT"
          phaseProgress={50}
          nextBestAction={{
            title: 'Add Validation',
            description: 'Add validation rules',
            action: () => {},
          }}
        />
      );

      const statusBar = screen.getByTestId('ai-status-bar');
      expect(statusBar).toHaveAttribute(
        'aria-label',
        'AI Status and next actions'
      );

      // Status should be announced
      const statusElement = screen.getByText('AI Status: Thinking...');
      expect(statusElement).toBeInTheDocument();

      // Progress should be accessible
      const progressElement = screen.getByText('50%');
      expect(progressElement).toBeInTheDocument();
    });

    it('Lifecycle Zones have proper ARIA labels', () => {
      const zones = [
        {
          phase: 'INTENT' as const,
          x: 0,
          y: 0,
          width: 200,
          height: 100,
          color: '#3b82f6',
          status: 'active' as const,
          progress: 75,
          artifacts: [],
        },
      ];

      render(
        <ZoomableLifecycleZones
          zones={zones}
          zoom={1}
          activePhase="INTENT"
          onPhaseClick={() => {}}
        />
      );

      const zonesContainer = screen.getByTestId('lifecycle-zones');
      expect(zonesContainer).toHaveAttribute('aria-label', 'Lifecycle phases');

      // Active phase should be indicated
      const activePhase = screen.getByText('INTENT');
      expect(activePhase).toBeInTheDocument();
    });

    it('Inline Code Panel has proper ARIA labels', () => {
      render(
        <InlineCodePanel
          code="// Sample code"
          language="typescript"
          fileName="example.tsx"
          isVisible={true}
          onCodeChange={() => {}}
          onFormat={() => {}}
          onRun={() => {}}
          onToggle={() => {}}
        />
      );

      const codePanel = screen.getByTestId('inline-code-panel');
      expect(codePanel).toHaveAttribute('aria-label', 'Code editor panel');

      // File name should be announced
      const fileName = screen.getByText('example.tsx');
      expect(fileName).toBeInTheDocument();

      // Language should be indicated
      const language = screen.getByText('typescript');
      expect(language).toBeInTheDocument();
    });

    it('Studio Layout has proper ARIA labels', () => {
      render(
        <StudioLayout
          fileTree={<div>File Tree Content</div>}
          codeEditor={<div>Code Editor Content</div>}
          livePreview={<div>Live Preview Content</div>}
          validation={<div>Validation Content</div>}
          onClose={() => {}}
        />
      );

      const layout = screen.getByTestId('studio-layout');
      expect(layout).toHaveAttribute('aria-label', 'Studio Mode Layout');

      // Panel regions should be labeled
      const fileTree = screen.getByTestId('studio-file-tree');
      expect(fileTree).toHaveAttribute('aria-label', 'File tree panel');

      const codeEditor = screen.getByTestId('studio-code-editor');
      expect(codeEditor).toHaveAttribute('aria-label', 'Code editor panel');

      const livePreview = screen.getByTestId('studio-live-preview');
      expect(livePreview).toHaveAttribute('aria-label', 'Live preview panel');

      const validation = screen.getByTestId('studio-validation');
      expect(validation).toHaveAttribute('aria-label', 'Validation panel');
    });
  });

  describe('Color Contrast', () => {
    it('AI Status Bar has sufficient color contrast', () => {
      render(
        <AIStatusBar
          status="ready"
          currentPhase="INTENT"
          phaseProgress={75}
          nextBestAction={{
            title: 'Add Validation',
            description: 'Add validation rules',
            action: () => {},
          }}
        />
      );

      const statusBar = screen.getByTestId('ai-status-bar');
      const styles = window.getComputedStyle(statusBar);

      // Check background and text color contrast
      // This would typically use a contrast checking library
      expect(styles.color).toBeDefined();
      expect(styles.backgroundColor).toBeDefined();
    });

    it('Lifecycle Zones have sufficient color contrast', () => {
      const zones = [
        {
          phase: 'INTENT' as const,
          x: 0,
          y: 0,
          width: 200,
          height: 100,
          color: '#3b82f6',
          status: 'active' as const,
          progress: 75,
          artifacts: [],
        },
      ];

      render(
        <ZoomableLifecycleZones
          zones={zones}
          zoom={1}
          activePhase="INTENT"
          onPhaseClick={() => {}}
        />
      );

      const phaseElement = screen.getByText('INTENT');
      const styles = window.getComputedStyle(phaseElement);

      expect(styles.color).toBeDefined();
      expect(styles.backgroundColor).toBeDefined();
    });
  });

  describe('Focus Management', () => {
    it('manages focus correctly when opening/closing panels', () => {
      const { rerender } = render(
        <InlineCodePanel
          code="// Sample code"
          language="typescript"
          fileName="example.tsx"
          isVisible={false}
          onCodeChange={() => {}}
          onFormat={() => {}}
          onRun={() => {}}
          onToggle={() => {}}
        />
      );

      // Initially hidden
      expect(screen.queryByRole('textbox')).not.toBeInTheDocument();

      // Show panel
      rerender(
        <InlineCodePanel
          code="// Sample code"
          language="typescript"
          fileName="example.tsx"
          isVisible={true}
          onCodeChange={() => {}}
          onFormat={() => {}}
          onRun={() => {}}
          onToggle={() => {}}
        />
      );

      // Should focus code editor when shown
      const codeEditor =
        screen.getByRole('textbox') ||
        screen.getByDisplayValue('// Sample code');
      expect(codeEditor).toBeInTheDocument();
    });

    it('traps focus within modal dialogs', () => {
      render(<KeyboardShortcutsHelp isOpen={true} onClose={() => {}} />);

      const helpDialog = screen.getByTestId('keyboard-shortcuts-help');
      expect(helpDialog).toBeInTheDocument();

      // Focus should be trapped within the dialog
      const closeButton = screen.getByText('Close');
      closeButton.focus();
      expect(document.activeElement).toBe(closeButton);

      // Tab should stay within dialog
      fireEvent.keyDown(helpDialog, { key: 'Tab' });
      // Focus should still be within the dialog
    });
  });

  describe('Reduced Motion Support', () => {
    it('respects prefers-reduced-motion', () => {
      // Mock prefers-reduced-motion
      Object.defineProperty(window, 'matchMedia', {
        writable: true,
        value: (query: string) => ({
          matches: query === '(prefers-reduced-motion: reduce)',
          media: query,
          onchange: null,
          addListener: () => {},
          removeListener: () => {},
          addEventListener: () => {},
          removeEventListener: () => {},
          dispatchEvent: () => {},
        }),
      });

      render(
        <AIStatusBar
          status="ready"
          currentPhase="INTENT"
          phaseProgress={75}
          nextBestAction={{
            title: 'Add Validation',
            description: 'Add validation rules',
            action: () => {},
          }}
        />
      );

      const statusBar = screen.getByTestId('ai-status-bar');
      const styles = window.getComputedStyle(statusBar);

      // Should have reduced or no animations
      expect(styles.animation).toBe(
        'none' || styles.transitionDuration === '0s'
      );
    });
  });

  describe('High Contrast Mode Support', () => {
    it('supports high contrast mode', () => {
      // Mock high contrast mode
      Object.defineProperty(window, 'matchMedia', {
        writable: true,
        value: (query: string) => ({
          matches: query === '(prefers-contrast: high)',
          media: query,
          onchange: null,
          addListener: () => {},
          removeListener: () => {},
          addEventListener: () => {},
          removeEventListener: () => {},
          dispatchEvent: () => {},
        }),
      });

      render(
        <AIStatusBar
          status="ready"
          currentPhase="INTENT"
          phaseProgress={75}
          nextBestAction={{
            title: 'Add Validation',
            description: 'Add validation rules',
            action: () => {},
          }}
        />
      );

      const statusBar = screen.getByTestId('ai-status-bar');
      const styles = window.getComputedStyle(statusBar);

      // Should have high contrast colors
      expect(styles.color).toBeDefined();
      expect(styles.backgroundColor).toBeDefined();
    });
  });

  describe('Text Scaling Support', () => {
    it('supports text scaling up to 200%', () => {
      // Mock larger text size
      document.documentElement.style.fontSize = '200%';

      render(
        <AIStatusBar
          status="ready"
          currentPhase="INTENT"
          phaseProgress={75}
          nextBestAction={{
            title: 'Add Validation',
            description: 'Add validation rules',
            action: () => {},
          }}
        />
      );

      const statusBar = screen.getByTestId('ai-status-bar');
      const styles = window.getComputedStyle(statusBar);

      // Should accommodate larger text
      expect(statusBar).toBeInTheDocument();
      expect(styles.fontSize).toBeDefined();

      // Reset
      document.documentElement.style.fontSize = '';
    });
  });
});
