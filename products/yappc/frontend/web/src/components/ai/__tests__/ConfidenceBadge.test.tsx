import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import '@testing-library/jest-dom';
import { ConfidenceBadge, ConfidenceDot } from '../ConfidenceBadge';

describe('ConfidenceBadge', () => {
  it('renders high confidence correctly', () => {
    render(<ConfidenceBadge score={92} />);
    expect(screen.getByTestId('confidence-badge')).toHaveTextContent('92% confidence');
  });

  it('renders medium confidence correctly', () => {
    render(<ConfidenceBadge score={72} />);
    expect(screen.getByTestId('confidence-badge')).toHaveTextContent('72% confidence');
  });

  it('renders low confidence correctly', () => {
    render(<ConfidenceBadge score={45} />);
    expect(screen.getByTestId('confidence-badge')).toHaveTextContent('45% confidence');
  });

  it('renders uncertain confidence correctly', () => {
    render(<ConfidenceBadge score={20} />);
    expect(screen.getByTestId('confidence-badge')).toHaveTextContent('20% confidence');
  });

  it('accepts custom label', () => {
    render(<ConfidenceBadge score={85} label="High Confidence" />);
    expect(screen.getByTestId('confidence-badge')).toHaveTextContent('High Confidence');
  });

  it('shows type when showType is true', () => {
    render(<ConfidenceBadge score={80} showType type="rule-based heuristic" />);
    expect(screen.getByText('rule-based heuristic')).toBeInTheDocument();
  });

  it('shows evidence link when evidenceRef is provided', () => {
    render(<ConfidenceBadge score={90} evidenceRef="https://example.com/evidence" />);
    expect(screen.getByRole('link', { name: 'Evidence' })).toHaveAttribute('href', 'https://example.com/evidence');
  });
});

describe('ConfidenceDot', () => {
  it('renders for high confidence', () => {
    render(<ConfidenceDot score={95} />);
    expect(screen.getByTestId('confidence-dot')).toBeInTheDocument();
  });

  it('renders for low confidence', () => {
    render(<ConfidenceDot score={30} />);
    expect(screen.getByTestId('confidence-dot')).toBeInTheDocument();
  });
});
