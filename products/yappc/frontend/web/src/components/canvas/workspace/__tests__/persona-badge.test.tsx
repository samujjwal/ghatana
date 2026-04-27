/**
 * Tests for PersonaBadge and StatusBadge (canvas workspace components)
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import {
  PersonaBadge,
  StatusBadge,
  PERSONA_ICONS,
} from '../PersonaBadge';

// ─── PersonaBadge ────────────────────────────────────────────────────────────

describe('PersonaBadge', () => {
  it('renders a chip with abbreviated persona name', () => {
    render(<PersonaBadge persona="Product Manager" />);
    // PM is the abbreviation for Product Manager
    expect(screen.getByText('PM')).toBeTruthy();
  });

  it('renders abbreviation for Frontend Engineer', () => {
    render(<PersonaBadge persona="Frontend Engineer" />);
    expect(screen.getByText('FE')).toBeTruthy();
  });

  it('renders abbreviation for Backend Engineer', () => {
    render(<PersonaBadge persona="Backend Engineer" />);
    expect(screen.getByText('BE')).toBeTruthy();
  });

  it('renders abbreviation for QA Engineer', () => {
    render(<PersonaBadge persona="QA Engineer" />);
    expect(screen.getByText('QE')).toBeTruthy();
  });

  it('handles single-word persona name', () => {
    render(<PersonaBadge persona="Developer" />);
    // Single word → only first char 'D'
    expect(screen.getByText('D')).toBeTruthy();
  });

  it('limits label to 2 characters max', () => {
    render(<PersonaBadge persona="Very Long Multi Word Name" />);
    // Only first 2 initials: 'VL'
    const chip = screen.getByText('VL');
    expect(chip).toBeTruthy();
  });

  it('calls onClick when clicked', () => {
    const onClick = vi.fn();
    render(<PersonaBadge persona="Product Manager" onClick={onClick} />);
    fireEvent.click(screen.getByText('PM'));
    expect(onClick).toHaveBeenCalledOnce();
  });

  it('renders without onClick prop without error', () => {
    render(<PersonaBadge persona="UX Designer" />);
    // 'UX Designer' → initials 'U' + 'D' = 'UD' (component splits on spaces)
    expect(screen.getByText('UD')).toBeTruthy();
  });

  it('renders with size=small (default)', () => {
    render(<PersonaBadge persona="Product Manager" size="small" />);
    expect(screen.getByText('PM')).toBeTruthy();
  });

  it('renders with size=medium', () => {
    render(<PersonaBadge persona="Product Manager" size="medium" />);
    expect(screen.getByText('PM')).toBeTruthy();
  });

  it('renders with variant=outlined', () => {
    render(<PersonaBadge persona="Product Manager" variant="outlined" />);
    expect(screen.getByText('PM')).toBeTruthy();
  });
});

// ─── PERSONA_ICONS constant ──────────────────────────────────────────────────

describe('PERSONA_ICONS', () => {
  it('has entries for expected persona roles', () => {
    expect(PERSONA_ICONS['Product Manager']).toBe('PM');
    expect(PERSONA_ICONS['UX Designer']).toBe('UX');
    expect(PERSONA_ICONS['Frontend Engineer']).toBe('FE');
    expect(PERSONA_ICONS['Backend Engineer']).toBe('BE');
    expect(PERSONA_ICONS['QA Engineer']).toBe('QA');
    expect(PERSONA_ICONS['DevOps Engineer']).toBe('DO');
    expect(PERSONA_ICONS['Data Engineer']).toBe('DE');
    expect(PERSONA_ICONS['Security Engineer']).toBe('SE');
  });
});

// ─── StatusBadge ─────────────────────────────────────────────────────────────

describe('StatusBadge', () => {
  it('renders "Done" for complete status', () => {
    render(<StatusBadge status="complete" />);
    expect(screen.getByText('Done')).toBeTruthy();
  });

  it('renders "In Progress" for in-progress status', () => {
    render(<StatusBadge status="in-progress" />);
    expect(screen.getByText('In Progress')).toBeTruthy();
  });

  it('renders "In Review" for review status', () => {
    render(<StatusBadge status="review" />);
    expect(screen.getByText('In Review')).toBeTruthy();
  });

  it('renders "Pending" for pending status', () => {
    render(<StatusBadge status="pending" />);
    expect(screen.getByText('Pending')).toBeTruthy();
  });

  it('renders "Blocked" for blocked status', () => {
    render(<StatusBadge status="blocked" />);
    expect(screen.getByText('Blocked')).toBeTruthy();
  });

  it('renders with size=small by default', () => {
    render(<StatusBadge status="complete" size="small" />);
    expect(screen.getByText('Done')).toBeTruthy();
  });

  it('renders with size=medium', () => {
    render(<StatusBadge status="complete" size="medium" />);
    expect(screen.getByText('Done')).toBeTruthy();
  });
});
