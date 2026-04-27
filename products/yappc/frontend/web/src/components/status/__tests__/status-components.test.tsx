/**
 * Tests for SaveSyncStatusBadge and BackendStatusIndicator
 */
import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest';
import { SaveSyncStatusBadge } from '../SaveSyncStatusBadge';
import { BackendStatusIndicator } from '../BackendStatusIndicator';

// ─── SaveSyncStatusBadge ────────────────────────────────────────────────────

describe('SaveSyncStatusBadge', () => {
  it('shows "Local draft only" for local-only status', () => {
    render(<SaveSyncStatusBadge status="local-only" />);
    expect(screen.getByText('Local draft only')).toBeTruthy();
  });

  it('shows "Syncing remote draft" for syncing status', () => {
    render(<SaveSyncStatusBadge status="syncing" />);
    expect(screen.getByText('Syncing remote draft')).toBeTruthy();
  });

  it('shows "Remote draft saved" for remote-saved status', () => {
    render(<SaveSyncStatusBadge status="remote-saved" />);
    expect(screen.getByText('Remote draft saved')).toBeTruthy();
  });

  it('shows "Remote sync failed" for remote-failed status', () => {
    render(<SaveSyncStatusBadge status="remote-failed" />);
    expect(screen.getByText('Remote sync failed')).toBeTruthy();
  });

  it('shows "Remote draft stale" for stale status', () => {
    render(<SaveSyncStatusBadge status="stale" />);
    expect(screen.getByText('Remote draft stale')).toBeTruthy();
  });

  it('shows "Sync conflict detected" for conflict status', () => {
    render(<SaveSyncStatusBadge status="conflict" />);
    expect(screen.getByText('Sync conflict detected')).toBeTruthy();
  });

  it('renders as a span element', () => {
    const { container } = render(<SaveSyncStatusBadge status="syncing" />);
    expect(container.querySelector('span')).toBeTruthy();
  });

  it('applies custom className', () => {
    render(<SaveSyncStatusBadge status="syncing" className="my-custom" />);
    const span = screen.getByText('Syncing remote draft');
    expect(span.className).toContain('my-custom');
  });

  it('accepts custom labels override', () => {
    render(
      <SaveSyncStatusBadge
        status="syncing"
        labels={{ syncing: 'Uploading...' }}
      />
    );
    expect(screen.getByText('Uploading...')).toBeTruthy();
  });

  it('uses default label for non-overridden statuses', () => {
    render(
      <SaveSyncStatusBadge
        status="conflict"
        labels={{ syncing: 'Uploading...' }}
      />
    );
    expect(screen.getByText('Sync conflict detected')).toBeTruthy();
  });

  it('applies blue color classes for syncing status', () => {
    render(<SaveSyncStatusBadge status="syncing" />);
    const badge = screen.getByText('Syncing remote draft');
    expect(badge.className).toContain('blue');
  });

  it('applies green color classes for remote-saved status', () => {
    render(<SaveSyncStatusBadge status="remote-saved" />);
    const badge = screen.getByText('Remote draft saved');
    expect(badge.className).toContain('emerald');
  });

  it('applies red color classes for remote-failed status', () => {
    render(<SaveSyncStatusBadge status="remote-failed" />);
    const badge = screen.getByText('Remote sync failed');
    expect(badge.className).toContain('red');
  });

  it('applies purple color classes for conflict status', () => {
    render(<SaveSyncStatusBadge status="conflict" />);
    const badge = screen.getByText('Sync conflict detected');
    expect(badge.className).toContain('purple');
  });

  it('applies amber color classes for stale status', () => {
    render(<SaveSyncStatusBadge status="stale" />);
    const badge = screen.getByText('Remote draft stale');
    expect(badge.className).toContain('amber');
  });

  it('passes extra HTML attributes to span', () => {
    render(<SaveSyncStatusBadge status="syncing" data-testid="badge" />);
    expect(screen.getByTestId('badge')).toBeTruthy();
  });
});

// ─── BackendStatusIndicator ─────────────────────────────────────────────────

describe('BackendStatusIndicator', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders a status element on initial mount', () => {
    render(<BackendStatusIndicator />);
    expect(screen.getByRole('status')).toBeTruthy();
  });

  it('renders with an aria-label on initial mount', () => {
    render(<BackendStatusIndicator />);
    const indicator = screen.getByRole('status');
    expect(indicator.getAttribute('aria-label')).toBeTruthy();
  });

  it('applies custom className', () => {
    render(<BackendStatusIndicator className="test-cls" />);
    const indicator = screen.getByRole('status');
    expect(indicator.className).toContain('test-cls');
  });

  it('shows "unknown" status text in aria-label initially', () => {
    render(<BackendStatusIndicator />);
    const indicator = screen.getByRole('status');
    const label = indicator.getAttribute('aria-label') ?? '';
    // initial state is 'unknown' before first check
    expect(label).toContain('unknown');
  });

  it('renders a colored dot indicator span inside', () => {
    const { container } = render(<BackendStatusIndicator />);
    // There should be at least one inner span (the color dot)
    const spans = container.querySelectorAll('span span');
    expect(spans.length).toBeGreaterThan(0);
  });
});
