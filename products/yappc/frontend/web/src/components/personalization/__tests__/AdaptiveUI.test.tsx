/**
 * AdaptiveUI Component Tests
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AdaptiveUI } from '../AdaptiveUI';
import { resetState, recordAction } from '../../../services/ai/LearningService';

describe('AdaptiveUI', () => {
  beforeEach(() => {
    resetState();
  });

  it('should render personalisation header', () => {
    render(<AdaptiveUI />);
    expect(screen.getByText('Personalisation')).toBeDefined();
  });

  it('should show "not enough data" message when few actions recorded', () => {
    render(<AdaptiveUI />);
    expect(screen.getByText(/Keep using YAPPC/)).toBeDefined();
  });

  it('should show suggestions when enough data exists', () => {
    for (let i = 0; i < 15; i++) {
      recordAction({ category: 'navigation', action: 'nav', context: 'ctx' });
    }
    render(<AdaptiveUI />);
    expect(screen.getByText(/Switch to/)).toBeDefined();
    expect(screen.getByText(/navigation/i)).toBeDefined();
    expect(screen.getByText(/AI assistance/)).toBeDefined();
  });

  it('should call onApplyLayout when Apply clicked', () => {
    for (let i = 0; i < 15; i++) {
      recordAction({ category: 'navigation', action: 'nav', context: 'ctx' });
    }
    const onApply = vi.fn();
    render(<AdaptiveUI onApplyLayout={onApply} />);
    const applyButtons = screen.getAllByText('Apply');
    fireEvent.click(applyButtons[0]);
    expect(onApply).toHaveBeenCalled();
  });

  it('should call onApplyNavigation when Apply clicked', () => {
    for (let i = 0; i < 15; i++) {
      recordAction({ category: 'navigation', action: 'nav', context: 'ctx' });
    }
    const onApplyNav = vi.fn();
    render(<AdaptiveUI onApplyNavigation={onApplyNav} />);
    const applyButtons = screen.getAllByText('Apply');
    fireEvent.click(applyButtons[1]);
    expect(onApplyNav).toHaveBeenCalled();
  });

  it('should render frequent actions', () => {
    for (let i = 0; i < 15; i++) {
      recordAction({ category: 'editing', action: 'save-file', context: 'project:a' });
    }
    render(<AdaptiveUI />);
    expect(screen.getByText('Your top actions')).toBeDefined();
    expect(screen.getByText('save-file')).toBeDefined();
  });

  it('should call onPinFeature when action button clicked', () => {
    for (let i = 0; i < 15; i++) {
      recordAction({ category: 'editing', action: 'save-file', context: 'project:a' });
    }
    const onPin = vi.fn();
    render(<AdaptiveUI onPinFeature={onPin} />);
    fireEvent.click(screen.getByText('save-file'));
    expect(onPin).toHaveBeenCalledWith('save-file');
  });

  it('should accept className prop', () => {
    const { container } = render(<AdaptiveUI className="custom-cls" />);
    expect(container.firstElementChild?.classList.contains('custom-cls')).toBe(true);
  });

  it('should reset state when reset button clicked', () => {
    for (let i = 0; i < 15; i++) {
      recordAction({ category: 'editing', action: 'edit', context: 'ctx' });
    }
    render(<AdaptiveUI />);
    // After reset, should show the not-enough-data message
    const resetBtn = screen.getAllByRole('button')[0]; // first button is reset
    fireEvent.click(resetBtn);
    expect(screen.getByText(/Keep using YAPPC/)).toBeDefined();
  });
});
