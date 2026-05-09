import React from 'react';
import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PageStateNotice } from '@/components/PageStateNotice';

describe('PageStateNotice', () => {
  it('renders loading state without alert role', () => {
    render(<PageStateNotice testId="state-loading" tone="loading" message="Loading data..." />);

    const node = screen.getByTestId('state-loading');
    expect(node).toHaveTextContent('Loading data...');
    expect(node).not.toHaveAttribute('role');
  });

  it('renders error state with alert role', () => {
    render(<PageStateNotice testId="state-error" tone="error" message="Failed to load." />);

    const node = screen.getByTestId('state-error');
    expect(node).toHaveTextContent('Failed to load.');
    expect(node).toHaveAttribute('role', 'alert');
  });
});
