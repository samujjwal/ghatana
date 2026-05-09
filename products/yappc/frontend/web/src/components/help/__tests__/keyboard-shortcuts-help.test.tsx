/**
 * Tests for help/KeyboardShortcutsHelp
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { KeyboardShortcutsHelp } from '../KeyboardShortcutsHelp';

describe('KeyboardShortcutsHelp', () => {
  it('does not render content when open=false', () => {
    render(<KeyboardShortcutsHelp open={false} onClose={vi.fn()} />);
    // Modal is closed — "Keyboard Shortcuts" heading should not be visible
    expect(screen.queryByText('Keyboard Shortcuts')).toBeNull();
  });

  it('renders Keyboard Shortcuts heading when open=true', () => {
    render(<KeyboardShortcutsHelp open onClose={vi.fn()} />);
    expect(screen.getByText('Keyboard Shortcuts')).toBeTruthy();
  });

  it('calls onClose when close button clicked', () => {
    const onClose = vi.fn();
    render(<KeyboardShortcutsHelp open onClose={onClose} />);
    // Close button has an accessible close label
    const closeBtn = screen.getByLabelText('Close');
    fireEvent.click(closeBtn);
    expect(onClose).toHaveBeenCalled();
  });

  it('renders search input', () => {
    render(<KeyboardShortcutsHelp open onClose={vi.fn()} />);
    expect(screen.getByPlaceholderText(/search/i)).toBeTruthy();
  });

  it('calls onClose when Escape key pressed', () => {
    const onClose = vi.fn();
    render(<KeyboardShortcutsHelp open onClose={onClose} />);
    fireEvent.keyDown(document, { key: 'Escape' });
    // onClose may be called once (effect) or twice (effect + Modal)
    expect(onClose).toHaveBeenCalled();
  });
});
