/**
 * Tests for LifecyclePhaseBadge, LifecyclePhaseIcon, and LifecycleBreadcrumb
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import {
  LifecyclePhaseBadge,
  LifecyclePhaseIcon,
} from '../LifecyclePhaseBadge';
import { LifecycleBreadcrumb } from '../LifecycleBreadcrumb';
import { LifecyclePhase } from '../../../types/lifecycle';

// LifecyclePhaseBadge
describe('LifecyclePhaseBadge', () => {
  it('renders the INTENT phase label', () => {
    render(<LifecyclePhaseBadge phase={LifecyclePhase.INTENT} />);
    expect(screen.getByRole('status', { name: /lifecycle phase: intent/i })).toBeTruthy();
    expect(screen.getByText('Intent')).toBeTruthy();
  });

  it('renders the OBSERVE phase label', () => {
    render(<LifecyclePhaseBadge phase={LifecyclePhase.OBSERVE} />);
    expect(screen.getByText('Observe')).toBeTruthy();
  });

  it('renders with size sm', () => {
    render(<LifecyclePhaseBadge phase={LifecyclePhase.INTENT} size="sm" />);
    const badge = screen.getByRole('status');
    expect(badge.className).toContain('px-2');
    expect(badge.className).toContain('py-0.5');
    expect(badge.className).toContain('text-xs');
  });

  it('renders with size md (default)', () => {
    render(<LifecyclePhaseBadge phase={LifecyclePhase.INTENT} size="md" />);
    const badge = screen.getByRole('status');
    expect(badge.className).toContain('px-3');
    expect(badge.className).toContain('py-1');
    expect(badge.className).toContain('text-sm');
  });

  it('renders with size lg', () => {
    render(<LifecyclePhaseBadge phase={LifecyclePhase.INTENT} size="lg" />);
    const badge = screen.getByRole('status');
    expect(badge.className).toContain('px-4');
    expect(badge.className).toContain('py-1.5');
    expect(badge.className).toContain('text-base');
  });

  it('sets title tooltip when showTooltip=true (default)', () => {
    render(<LifecyclePhaseBadge phase={LifecyclePhase.INTENT} showTooltip />);
    const badge = screen.getByRole('status');
    expect(badge.getAttribute('title')).toBeTruthy();
  });

  it('does not set title when showTooltip=false', () => {
    render(<LifecyclePhaseBadge phase={LifecyclePhase.INTENT} showTooltip={false} />);
    const badge = screen.getByRole('status');
    expect(badge.getAttribute('title')).toBeNull();
  });

  it('applies custom className', () => {
    render(<LifecyclePhaseBadge phase={LifecyclePhase.INTENT} className="custom-class" />);
    const badge = screen.getByRole('status');
    expect(badge.className).toContain('custom-class');
  });

  it('renders the icon as aria-hidden', () => {
    render(<LifecyclePhaseBadge phase={LifecyclePhase.INTENT} />);
    const iconEl = screen.getByRole('status').querySelector('[aria-hidden]');
    expect(iconEl).toBeTruthy();
  });

  it('renders INSTITUTIONALIZE phase', () => {
    render(<LifecyclePhaseBadge phase={LifecyclePhase.INSTITUTIONALIZE} />);
    expect(screen.getByText('Institutionalize')).toBeTruthy();
  });

  it('renders CONTEXT phase (aliased as SHAPE)', () => {
    render(<LifecyclePhaseBadge phase={LifecyclePhase.SHAPE} />);
    expect(screen.getByText('Context')).toBeTruthy();
  });
});

// LifecyclePhaseIcon
describe('LifecyclePhaseIcon', () => {
  it('renders with role=img and aria-label', () => {
    render(<LifecyclePhaseIcon phase={LifecyclePhase.INTENT} />);
    expect(screen.getByRole('img', { name: /lifecycle phase: intent/i })).toBeTruthy();
  });

  it('shows title tooltip with description', () => {
    render(<LifecyclePhaseIcon phase={LifecyclePhase.OBSERVE} />);
    const iconEl = screen.getByRole('img');
    const title = iconEl.getAttribute('title');
    expect(title).toContain('Observe');
  });

  it('applies custom className', () => {
    render(<LifecyclePhaseIcon phase={LifecyclePhase.INTENT} className="my-icon" />);
    const iconEl = screen.getByRole('img');
    expect(iconEl.className).toContain('my-icon');
  });
});

// LifecycleBreadcrumb
describe('LifecycleBreadcrumb', () => {
  it('renders default project name and Lifecycle crumbs', () => {
    render(<LifecycleBreadcrumb />);
    expect(screen.getByText('Project')).toBeTruthy();
    expect(screen.getByText('Lifecycle')).toBeTruthy();
  });

  it('renders custom project name', () => {
    render(<LifecycleBreadcrumb projectName="My App" />);
    expect(screen.getByText('My App')).toBeTruthy();
  });

  it('renders current phase when provided', () => {
    render(<LifecycleBreadcrumb currentPhase="INTENT" />);
    expect(screen.getByText('Intent')).toBeTruthy();
  });

  it('renders artifact title when both kind and title provided', () => {
    render(
      <LifecycleBreadcrumb
        currentPhase="VALIDATE"
        selectedArtifactKind="prd"
        selectedArtifactTitle="My PRD"
      />
    );
    expect(screen.getByText('Validate')).toBeTruthy();
    expect(screen.getByText('My PRD')).toBeTruthy();
  });

  it('calls onNavigateToRoot when project button clicked', () => {
    const onNavigateToRoot = vi.fn();
    render(<LifecycleBreadcrumb projectName="Test" onNavigateToRoot={onNavigateToRoot} />);
    const projectButton = screen.getByRole('button', { name: /test/i });
    expect(projectButton).toHaveClass('gh-button');
    fireEvent.click(projectButton);
    expect(onNavigateToRoot).toHaveBeenCalledOnce();
  });

  it('calls onNavigateToPhase when phase button clicked', () => {
    const onNavigateToPhase = vi.fn();
    render(
      <LifecycleBreadcrumb
        currentPhase="OBSERVE"
        onNavigateToPhase={onNavigateToPhase}
      />
    );
    fireEvent.click(screen.getByText('Observe'));
    expect(onNavigateToPhase).toHaveBeenCalledWith('OBSERVE');
  });

  it('calls onClearArtifact when artifact crumb clicked', () => {
    const onClearArtifact = vi.fn();
    render(
      <LifecycleBreadcrumb
        currentPhase="GENERATE"
        selectedArtifactKind="prd"
        selectedArtifactTitle="PRD Doc"
        onClearArtifact={onClearArtifact}
      />
    );
    fireEvent.click(screen.getByText('PRD Doc'));
    expect(onClearArtifact).toHaveBeenCalledOnce();
  });

  it('renders as nav element', () => {
    render(<LifecycleBreadcrumb />);
    expect(screen.getByRole('navigation')).toBeTruthy();
  });

  it('does not render artifact crumb when only kind provided', () => {
    render(
      <LifecycleBreadcrumb
        currentPhase="INTENT"
        selectedArtifactKind="prd"
      />
    );
    // No artifact title rendered
    expect(screen.queryByText('PRD Doc')).toBeNull();
  });
});
