import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { AISourceChip } from '../AISourceChip';

describe('AISourceChip', () => {
  it('renders RULE source with correct label', () => {
    render(<AISourceChip source="RULE" />);
    expect(screen.getByRole('status')).toHaveTextContent('Rule');
  });

  it('renders MODEL source with correct label', () => {
    render(<AISourceChip source="MODEL" />);
    expect(screen.getByRole('status')).toHaveTextContent('Model');
  });

  it('shows confidence percentage for MODEL source', () => {
    render(<AISourceChip source="MODEL" confidence={0.87} />);
    const chip = screen.getByRole('status');
    expect(chip).toHaveTextContent('87%');
  });

  it('does not show confidence for RULE source even when provided', () => {
    render(<AISourceChip source="RULE" confidence={0.99} />);
    expect(screen.getByRole('status')).not.toHaveTextContent('%');
  });

  it('sets accessible aria-label including confidence when MODEL', () => {
    render(<AISourceChip source="MODEL" confidence={0.73} />);
    expect(screen.getByRole('status')).toHaveAttribute(
      'aria-label',
      'AI source: Model, confidence 73%',
    );
  });

  it('sets accessible aria-label without confidence when RULE', () => {
    render(<AISourceChip source="RULE" />);
    expect(screen.getByRole('status')).toHaveAttribute('aria-label', 'AI source: Rule');
  });

  it('uses rationale as title attribute when provided', () => {
    render(<AISourceChip source="MODEL" rationale="Based on semantic similarity" />);
    expect(screen.getByRole('status')).toHaveAttribute('title', 'Based on semantic similarity');
  });

  it('applies extra className', () => {
    const { container } = render(<AISourceChip source="RULE" className="my-class" />);
    expect(container.firstChild).toHaveClass('my-class');
  });
});
