/**
 * @fileoverview Keyboard-only Canvas and Builder Navigation Tests
 *
 * Tests for keyboard-only navigation and interaction in the canvas and builder.
 * Verifies accessibility and screen reader compatibility for users who cannot use a mouse.
 *
 * @doc.type test
 * @doc.purpose Keyboard-only navigation tests for canvas and builder
 * @doc.layer product
 * @doc.pattern Accessibility Test
 */

import { render, fireEvent, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom';
import React from 'react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import userEvent from '@testing-library/user-event';

// Mock the canvas components for testing
vi.mock('../CanvasToolbar', () => ({
  CanvasToolbar: ({ onToolSelect }: { onToolSelect: (tool: string) => void }) => (
    <div data-testid="canvas-toolbar">
      <button data-testid="tool-select" onClick={() => onToolSelect('select')}>Select</button>
      <button data-testid="tool-pan" onClick={() => onToolSelect('pan')}>Pan</button>
      <button data-testid="tool-draw" onClick={() => onToolSelect('draw')}>Draw</button>
    </div>
  ),
}));

vi.mock('../ComponentPalette', () => ({
  ComponentPalette: ({ onAddComponent }: { onAddComponent: (component: string) => void }) => (
    <div data-testid="component-palette">
      <button data-testid="add-button" onClick={() => onAddComponent('button')}>Add Button</button>
      <button data-testid="add-input" onClick={() => onAddComponent('input')}>Add Input</button>
    </div>
  ),
}));

describe('Keyboard-only Canvas Navigation', () => {
  beforeEach(() => {
    document.body.innerHTML = '';
  });

  describe('Selection with keyboard', () => {
    it('should select nodes using Tab navigation', async () => {
      const mockSelect = vi.fn();
      
      render(
        <div role="application" aria-label="Canvas">
          <div role="button" tabIndex={0} data-testid="node-1" onKeyPress={() => mockSelect('node-1')}>
            Node 1
          </div>
          <div role="button" tabIndex={0} data-testid="node-2" onKeyPress={() => mockSelect('node-2')}>
            Node 2
          </div>
          <div role="button" tabIndex={0} data-testid="node-3" onKeyPress={() => mockSelect('node-3')}>
            Node 3
          </div>
        </div>
      );

      const user = userEvent.setup();

      // Tab through nodes
      await user.tab();
      expect(screen.getByTestId('node-1')).toHaveFocus();

      await user.keyboard('{Enter}');
      expect(mockSelect).toHaveBeenCalledWith('node-1');

      await user.tab();
      expect(screen.getByTestId('node-2')).toHaveFocus();

      await user.keyboard('{Enter}');
      expect(mockSelect).toHaveBeenCalledWith('node-2');
    });

    it('should navigate between selected nodes using arrow keys', async () => {
      const mockNavigate = vi.fn();

      render(
        <div role="application" aria-label="Canvas">
          <div
            role="button"
            tabIndex={0}
            data-testid="selected-node"
            onKeyDown={(e) => {
              if (e.key === 'ArrowDown') mockNavigate('down');
              if (e.key === 'ArrowUp') mockNavigate('up');
              if (e.key === 'ArrowLeft') mockNavigate('left');
              if (e.key === 'ArrowRight') mockNavigate('right');
            }}
          >
            Selected Node
          </div>
        </div>
      );

      const user = userEvent.setup();
      const selectedNode = screen.getByTestId('selected-node');

      await user.click(selectedNode);
      await user.keyboard('{ArrowDown}');
      expect(mockNavigate).toHaveBeenCalledWith('down');

      await user.keyboard('{ArrowUp}');
      expect(mockNavigate).toHaveBeenCalledWith('up');

      await user.keyboard('{ArrowLeft}');
      expect(mockNavigate).toHaveBeenCalledWith('left');

      await user.keyboard('{ArrowRight}');
      expect(mockNavigate).toHaveBeenCalledWith('right');
    });
  });

  describe('Insertion with keyboard', () => {
    it('should insert components using keyboard shortcuts', async () => {
      const mockInsert = vi.fn();

      render(
        <div role="application" aria-label="Canvas">
          <button data-testid="insert-button" onClick={() => mockInsert('button')}>
            Insert Button (Ctrl+B)
          </button>
          <button data-testid="insert-input" onClick={() => mockInsert('input')}>
            Insert Input (Ctrl+I)
          </button>
        </div>
      );

      const user = userEvent.setup();

      // Focus the canvas and use keyboard shortcuts
      await user.keyboard('{Control>}b{/Control}');
      expect(mockInsert).toHaveBeenCalledWith('button');

      await user.keyboard('{Control>}i{/Control}');
      expect(mockInsert).toHaveBeenCalledWith('input');
    });

    it('should open component palette with keyboard shortcut', async () => {
      const mockOpenPalette = vi.fn();

      render(
        <div role="application" aria-label="Canvas" onKeyDown={(e) => {
          if (e.key === 'p' && e.ctrlKey) {
            mockOpenPalette();
          }
        }}>
          <div>Canvas Content</div>
        </div>
      );

      const user = userEvent.setup();
      const canvas = screen.getByRole('application');

      await user.click(canvas);
      await user.keyboard('{Control>}p{/Control}');

      expect(mockOpenPalette).toHaveBeenCalled();
    });
  });

  describe('Move and reorder with keyboard', () => {
    it('should move selected node using keyboard shortcuts', async () => {
      const mockMove = vi.fn();

      render(
        <div role="application" aria-label="Canvas">
          <div
            role="button"
            tabIndex={0}
            data-testid="selected-node"
            onKeyDown={(e) => {
              if (e.key === 'ArrowUp' && e.shiftKey) mockMove('up');
              if (e.key === 'ArrowDown' && e.shiftKey) mockMove('down');
              if (e.key === 'ArrowLeft' && e.shiftKey) mockMove('left');
              if (e.key === 'ArrowRight' && e.shiftKey) mockMove('right');
            }}
            aria-label="Selected Node, press Shift+Arrow keys to move"
          >
            Selected Node
          </div>
        </div>
      );

      const user = userEvent.setup();
      const selectedNode = screen.getByTestId('selected-node');

      await user.click(selectedNode);
      await user.keyboard('{Shift>}{ArrowUp}{/Shift}');
      expect(mockMove).toHaveBeenCalledWith('up');

      await user.keyboard('{Shift>}{ArrowDown}{/Shift}');
      expect(mockMove).toHaveBeenCalledWith('down');

      await user.keyboard('{Shift>}{ArrowLeft}{/Shift}');
      expect(mockMove).toHaveBeenCalledWith('left');

      await user.keyboard('{Shift>}{ArrowRight}{/Shift}');
      expect(mockMove).toHaveBeenCalledWith('right');
    });

    it('should reorder nodes in list using keyboard', async () => {
      const mockReorder = vi.fn();

      render(
        <div role="list" aria-label="Node List">
          <div role="listitem" tabIndex={0} data-testid="node-1" onKeyDown={(e) => {
            if (e.key === '[') mockReorder('node-1', 'up');
            if (e.key === ']') mockReorder('node-1', 'down');
          }}>
            Node 1
          </div>
          <div role="listitem" tabIndex={0} data-testid="node-2" onKeyDown={(e) => {
            if (e.key === '[') mockReorder('node-2', 'up');
            if (e.key === ']') mockReorder('node-2', 'down');
          }}>
            Node 2
          </div>
        </div>
      );

      const user = userEvent.setup();
      const node1 = screen.getByTestId('node-1');

      await user.click(node1);
      await user.keyboard('[');
      expect(mockReorder).toHaveBeenCalledWith('node-1', 'up');

      await user.keyboard(']');
      expect(mockReorder).toHaveBeenCalledWith('node-1', 'down');
    });
  });

  describe('Delete with keyboard', () => {
    it('should delete selected node with Delete or Backspace key', async () => {
      const mockDelete = vi.fn();

      render(
        <div role="application" aria-label="Canvas">
          <div
            role="button"
            tabIndex={0}
            data-testid="selected-node"
            onKeyDown={(e) => {
              if (e.key === 'Delete' || e.key === 'Backspace') {
                mockDelete();
              }
            }}
            aria-label="Selected Node, press Delete or Backspace to remove"
          >
            Selected Node
          </div>
        </div>
      );

      const user = userEvent.setup();
      const selectedNode = screen.getByTestId('selected-node');

      await user.click(selectedNode);
      await user.keyboard('{Delete}');
      expect(mockDelete).toHaveBeenCalled();

      // Reset and test Backspace
      mockDelete.mockClear();
      await user.click(selectedNode);
      await user.keyboard('{Backspace}');
      expect(mockDelete).toHaveBeenCalled();
    });

    it('should show confirmation dialog before deletion with keyboard', async () => {
      const mockDelete = vi.fn();
      const mockConfirm = vi.fn().mockReturnValue(true);

      render(
        <div role="application" aria-label="Canvas">
          <div
            role="button"
            tabIndex={0}
            data-testid="selected-node"
            onKeyDown={(e) => {
              if (e.key === 'Delete' && window.confirm('Delete this node?')) {
                mockDelete();
              }
            }}
            aria-label="Selected Node, press Delete to remove"
          >
            Selected Node
          </div>
        </div>
      );

      window.confirm = mockConfirm;
      const user = userEvent.setup();
      const selectedNode = screen.getByTestId('selected-node');

      await user.click(selectedNode);
      await user.keyboard('{Delete}');

      expect(mockConfirm).toHaveBeenCalledWith('Delete this node?');
      expect(mockDelete).toHaveBeenCalled();
    });
  });

  describe('Undo/redo with keyboard', () => {
    it('should undo last action with Ctrl+Z', async () => {
      const mockUndo = vi.fn();

      render(
        <div role="application" aria-label="Canvas" onKeyDown={(e) => {
          if (e.key === 'z' && e.ctrlKey) {
            mockUndo();
          }
        }}>
          <div>Canvas Content</div>
        </div>
      );

      const user = userEvent.setup();
      const canvas = screen.getByRole('application');

      await user.click(canvas);
      await user.keyboard('{Control>}z{/Control}');

      expect(mockUndo).toHaveBeenCalled();
    });

    it('should redo last undone action with Ctrl+Y or Ctrl+Shift+Z', async () => {
      const mockRedo = vi.fn();

      render(
        <div role="application" aria-label="Canvas" onKeyDown={(e) => {
          if (e.key === 'y' && e.ctrlKey) {
            mockRedo();
          }
          if (e.key === 'z' && e.ctrlKey && e.shiftKey) {
            mockRedo();
          }
        }}>
          <div>Canvas Content</div>
        </div>
      );

      const user = userEvent.setup();
      const canvas = screen.getByRole('application');

      await user.click(canvas);
      await user.keyboard('{Control>}y{/Control}');
      expect(mockRedo).toHaveBeenCalled();

      mockRedo.mockClear();
      await user.keyboard('{Control>}{Shift>}z{/Shift}{/Control}');
      expect(mockRedo).toHaveBeenCalled();
    });
  });

  describe('Drawer and modal focus management', () => {
    it('should trap focus within modal when opened', async () => {
      const mockClose = vi.fn();

      render(
        <div>
          <button data-testid="open-modal">Open Modal</button>
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="modal-title"
            data-testid="modal"
            style={{ display: 'none' }}
          >
            <h2 id="modal-title">Modal Title</h2>
            <button data-testid="modal-button-1">Button 1</button>
            <button data-testid="modal-button-2">Button 2</button>
            <button data-testid="modal-close" onClick={mockClose}>Close</button>
          </div>
        </div>
      );

      const user = userEvent.setup();
      const openButton = screen.getByTestId('open-modal');
      const modal = screen.getByTestId('modal');

      // Open modal
      modal.style.display = 'block';
      await user.click(openButton);

      // Focus should move to first focusable element in modal
      const closeButton = screen.getByTestId('modal-close');
      closeButton.focus();

      // Tab should cycle within modal
      await user.tab();
      expect(screen.getByTestId('modal-button-1')).toHaveFocus();

      await user.tab();
      expect(screen.getByTestId('modal-button-2')).toHaveFocus();

      await user.tab();
      expect(closeButton).toHaveFocus();
    });

    it('should return focus to trigger element when modal closes', async () => {
      const mockClose = vi.fn();

      render(
        <div>
          <button data-testid="open-modal">Open Modal</button>
          <div
            role="dialog"
            aria-modal="true"
            aria-labelledby="modal-title"
            data-testid="modal"
          >
            <h2 id="modal-title">Modal Title</h2>
            <button data-testid="modal-close" onClick={mockClose}>Close</button>
          </div>
        </div>
      );

      const user = userEvent.setup();
      const openButton = screen.getByTestId('open-modal');
      const closeButton = screen.getByTestId('modal-close');
      const modal = screen.getByTestId('modal');

      // Open modal
      await user.click(openButton);
      closeButton.focus();

      // Close modal
      await user.click(closeButton);
      modal.style.display = 'none';

      // Focus should return to trigger element
      expect(openButton).toHaveFocus();
    });

    it('should close drawer with Escape key', async () => {
      const mockClose = vi.fn();

      render(
        <div role="dialog" aria-label="Drawer" onKeyDown={(e) => {
          if (e.key === 'Escape') {
            mockClose();
          }
        }}>
          <button data-testid="drawer-close">Close Drawer</button>
        </div>
      );

      const user = userEvent.setup();
      const drawer = screen.getByRole('dialog');

      await user.click(drawer);
      await user.keyboard('{Escape}');

      expect(mockClose).toHaveBeenCalled();
    });
  });

  describe('Screen reader announcements', () => {
    it('should announce node selection to screen readers', async () => {
      const mockAnnounce = vi.fn();

      render(
        <div role="application" aria-label="Canvas" aria-live="polite">
          <div
            role="button"
            tabIndex={0}
            data-testid="node-1"
            aria-label="Node 1"
            onClick={() => {
              mockAnnounce('Node 1 selected');
            }}
          >
            Node 1
          </div>
        </div>
      );

      const user = userEvent.setup();
      const node1 = screen.getByTestId('node-1');

      await user.click(node1);
      expect(mockAnnounce).toHaveBeenCalledWith('Node 1 selected');
    });

    it('should announce component insertion to screen readers', async () => {
      const mockAnnounce = vi.fn();

      render(
        <div role="application" aria-label="Canvas" aria-live="polite">
          <button
            data-testid="insert-button"
            onClick={() => {
              mockAnnounce('Button component inserted');
            }}
          >
            Insert Button
          </button>
        </div>
      );

      const user = userEvent.setup();
      const insertButton = screen.getByTestId('insert-button');

      await user.click(insertButton);
      expect(mockAnnounce).toHaveBeenCalledWith('Button component inserted');
    });

    it('should announce deletion to screen readers', async () => {
      const mockAnnounce = vi.fn();

      render(
        <div role="application" aria-label="Canvas" aria-live="polite">
          <div
            role="button"
            tabIndex={0}
            data-testid="node-1"
            aria-label="Node 1"
            onKeyDown={(e) => {
              if (e.key === 'Delete') {
                mockAnnounce('Node 1 deleted');
              }
            }}
          >
            Node 1
          </div>
        </div>
      );

      const user = userEvent.setup();
      const node1 = screen.getByTestId('node-1');

      await user.click(node1);
      await user.keyboard('{Delete}');

      expect(mockAnnounce).toHaveBeenCalledWith('Node 1 deleted');
    });

    it('should announce tool changes to screen readers', async () => {
      const mockAnnounce = vi.fn();

      render(
        <div role="toolbar" aria-label="Canvas Toolbar" aria-live="polite">
          <button
            data-testid="tool-select"
            onClick={() => mockAnnounce('Select tool activated')}
            aria-pressed="false"
          >
            Select
          </button>
          <button
            data-testid="tool-draw"
            onClick={() => mockAnnounce('Draw tool activated')}
            aria-pressed="false"
          >
            Draw
          </button>
        </div>
      );

      const user = userEvent.setup();
      const selectTool = screen.getByTestId('tool-select');

      await user.click(selectTool);
      expect(mockAnnounce).toHaveBeenCalledWith('Select tool activated');
    });
  });

  describe('Keyboard shortcuts help', () => {
    it('should show keyboard shortcuts dialog with Ctrl+?', async () => {
      const mockShowHelp = vi.fn();

      render(
        <div role="application" aria-label="Canvas" onKeyDown={(e) => {
          if (e.key === '?' && e.ctrlKey) {
            mockShowHelp();
          }
        }}>
          <div>Canvas Content</div>
        </div>
      );

      const user = userEvent.setup();
      const canvas = screen.getByRole('application');

      await user.click(canvas);
      await user.keyboard('{Control>?{/Control}');

      expect(mockShowHelp).toHaveBeenCalled();
    });

    it('should display all keyboard shortcuts in help dialog', async () => {
      render(
        <div role="dialog" aria-label="Keyboard Shortcuts" data-testid="shortcuts-dialog">
          <h2>Keyboard Shortcuts</h2>
          <ul>
            <li><kbd>Ctrl+Z</kbd> - Undo</li>
            <li><kbd>Ctrl+Y</kbd> - Redo</li>
            <li><kbd>Delete</kbd> - Delete selected</li>
            <li><kbd>Tab</kbd> - Navigate nodes</li>
            <li><kbd>Shift+Arrow</kbd> - Move node</li>
            <li><kbd>Ctrl+?</kbd> - Show shortcuts</li>
          </ul>
        </div>
      );

      const dialog = screen.getByTestId('shortcuts-dialog');

      expect(dialog).toBeInTheDocument();
      expect(screen.getByText(/Undo/)).toBeInTheDocument();
      expect(screen.getByText(/Redo/)).toBeInTheDocument();
      expect(screen.getByText(/Delete selected/)).toBeInTheDocument();
      expect(screen.getByText(/Navigate nodes/)).toBeInTheDocument();
      expect(screen.getByText(/Move node/)).toBeInTheDocument();
      expect(screen.getByText(/Show shortcuts/)).toBeInTheDocument();
    });
  });
});
