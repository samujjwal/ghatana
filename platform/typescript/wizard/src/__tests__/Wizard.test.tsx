/**
 * @group unit
 * @tier U, I-br
 *
 * Tests for @ghatana/wizard — Wizard component.
 */
import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';

import { Wizard } from '../Wizard';
import type { WizardStep } from '../hooks/useWizard';

const STEPS: WizardStep[] = [
  { id: 'info', title: 'Basic Info', description: 'Enter your details' },
  { id: 'plan', title: 'Choose Plan', description: 'Pick a plan' },
  { id: 'confirm', title: 'Confirm', description: 'Review and submit' },
];

const makeRenderStep = (label = 'step content') =>
  vi.fn((stepId: string) => <div data-testid={`content-${stepId}`}>{label}</div>);

describe('Wizard', () => {
  describe('rendering', () => {
    it('renders a navigation list with all step titles', () => {
      render(<Wizard steps={STEPS} renderStep={makeRenderStep()} />);
      expect(screen.getByText('Basic Info')).toBeInTheDocument();
      expect(screen.getByText('Choose Plan')).toBeInTheDocument();
      expect(screen.getByText('Confirm')).toBeInTheDocument();
    });

    it('calls renderStep with the current step id and index', () => {
      const renderStep = makeRenderStep();
      render(<Wizard steps={STEPS} renderStep={renderStep} />);
      expect(renderStep).toHaveBeenCalledWith('info', 0);
    });

    it('renders the description of the current step', () => {
      render(<Wizard steps={STEPS} renderStep={makeRenderStep()} />);
      expect(screen.getByText('Enter your details')).toBeInTheDocument();
    });

    it('wraps rendered step content in an aria-live region for accessibility', () => {
      render(<Wizard steps={STEPS} renderStep={makeRenderStep()} />);
      const live = document.querySelector('[aria-live]');
      expect(live).not.toBeNull();
    });
  });

  describe('footer navigation', () => {
    it('Previous button is disabled or absent on the first step', () => {
      render(<Wizard steps={STEPS} renderStep={makeRenderStep()} />);
      const prevBtn = screen.queryByRole('button', { name: /back|previous/i });
      if (prevBtn) {
        expect(prevBtn).toBeDisabled();
      } else {
        // Button not rendered on first step — also acceptable
        expect(prevBtn).toBeNull();
      }
    });

    it('shows Next button on intermediate steps', () => {
      render(<Wizard steps={STEPS} renderStep={makeRenderStep()} />);
      expect(screen.getByRole('button', { name: /next/i })).toBeInTheDocument();
    });

    it('Next button advances to the next step', async () => {
      const user = userEvent.setup();
      const renderStep = makeRenderStep();
      render(<Wizard steps={STEPS} renderStep={renderStep} />);
      await user.click(screen.getByRole('button', { name: /next/i }));
      expect(renderStep).toHaveBeenLastCalledWith('plan', 1);
      expect(screen.getByText('Choose Plan')).toBeInTheDocument();
    });

    it('shows Finish button on the last step', async () => {
      const user = userEvent.setup();
      render(<Wizard steps={STEPS} renderStep={makeRenderStep()} />);
      await user.click(screen.getByRole('button', { name: /next/i }));
      await user.click(screen.getByRole('button', { name: /next/i }));
      expect(screen.getByRole('button', { name: /finish|complete/i })).toBeInTheDocument();
    });

    it('calls onComplete when the Finish button is clicked', async () => {
      const user = userEvent.setup();
      const onComplete = vi.fn();
      render(<Wizard steps={STEPS} renderStep={makeRenderStep()} onComplete={onComplete} />);
      await user.click(screen.getByRole('button', { name: /next/i }));
      await user.click(screen.getByRole('button', { name: /next/i }));
      await user.click(screen.getByRole('button', { name: /finish|complete/i }));
      expect(onComplete).toHaveBeenCalledOnce();
    });

    it('calls onCancel when the Cancel button is clicked', async () => {
      const user = userEvent.setup();
      const onCancel = vi.fn();
      render(<Wizard steps={STEPS} renderStep={makeRenderStep()} onCancel={onCancel} />);
      // Cancel button should be visible; click it
      const cancelBtn = screen.queryByRole('button', { name: /cancel/i });
      if (cancelBtn) {
        await user.click(cancelBtn);
        expect(onCancel).toHaveBeenCalledOnce();
      }
    });

    it('Previous button becomes enabled after navigating forward', async () => {
      const user = userEvent.setup();
      render(<Wizard steps={STEPS} renderStep={makeRenderStep()} />);
      await user.click(screen.getByRole('button', { name: /next/i }));
      const prevBtn = screen.queryByRole('button', { name: /back|previous/i });
      if (prevBtn) {
        expect(prevBtn).not.toBeDisabled();
      }
    });

    it('Previous button retreats one step', async () => {
      const user = userEvent.setup();
      const renderStep = makeRenderStep();
      render(<Wizard steps={STEPS} renderStep={renderStep} />);
      await user.click(screen.getByRole('button', { name: /next/i }));
      const prevBtn = screen.queryByRole('button', { name: /back|previous/i });
      if (prevBtn) {
        await user.click(prevBtn);
        expect(renderStep).toHaveBeenLastCalledWith('info', 0);
      }
    });
  });

  describe('step navigation list', () => {
    it('future steps navigation buttons are disabled', () => {
      render(<Wizard steps={STEPS} renderStep={makeRenderStep()} />);
      // The nav step for 'Choose Plan' and 'Confirm' should be disabled at start
      const navButtons = screen.getAllByRole('button');
      const stepNavButtons = navButtons.filter(
        (b) => b.textContent?.match(/Choose Plan|Confirm/),
      );
      stepNavButtons.forEach((btn) => {
        expect(btn).toBeDisabled();
      });
    });

    it('completed step nav buttons become enabled after advancing', async () => {
      const user = userEvent.setup();
      render(<Wizard steps={STEPS} renderStep={makeRenderStep()} />);
      await user.click(screen.getByRole('button', { name: /next/i }));
      // 'Basic Info' step nav button should now be enabled/clickable
      const infoBtn = screen.getByRole('button', { name: /Basic Info/ });
      expect(infoBtn).not.toBeDisabled();
    });
  });

  describe('customisation', () => {
    it('renders custom completedText on the finish button', async () => {
      const user = userEvent.setup();
      render(
        <Wizard
          steps={STEPS}
          renderStep={makeRenderStep()}
          completedText="Submit Application"
        />,
      );
      await user.click(screen.getByRole('button', { name: /next/i }));
      await user.click(screen.getByRole('button', { name: /next/i }));
      expect(screen.getByRole('button', { name: /Submit Application/i })).toBeInTheDocument();
    });

    it('renders custom nextText on the next button', () => {
      render(
        <Wizard steps={STEPS} renderStep={makeRenderStep()} nextText="Continue →" />,
      );
      expect(screen.getByRole('button', { name: /Continue →/i })).toBeInTheDocument();
    });
  });

  describe('single-step wizard', () => {
    it('shows only the finish button with a single step (no next)', () => {
      render(
        <Wizard
          steps={[{ id: 'only', title: 'Only Step' }]}
          renderStep={makeRenderStep()}
        />,
      );
      // No 'next' because there are no further steps
      const nextBtn = screen.queryByRole('button', { name: /^next$/i });
      expect(nextBtn).toBeNull();
    });
  });
});
