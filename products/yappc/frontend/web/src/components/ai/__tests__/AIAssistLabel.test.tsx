/**
 * AIAssistLabel tests (C-Y3)
 */

import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import React from 'react';

import { AIAssistLabel } from '../AIAssistLabel';

describe('AIAssistLabel (C-Y3)', () => {
  it('renders rule source with correct data-source attribute', () => {
    render(<AIAssistLabel source="rule" />);
    const label = screen.getByTestId('ai-assist-label');
    expect(label).toBeInTheDocument();
    expect(label).toHaveAttribute('data-source', 'rule');
    expect(label).toHaveTextContent('Rule-based');
  });

  it('renders model source with correct label', () => {
    render(<AIAssistLabel source="model" />);
    expect(screen.getByTestId('ai-assist-label')).toHaveAttribute('data-source', 'model');
    expect(screen.getByText('AI model')).toBeInTheDocument();
  });

  it('renders hybrid source', () => {
    render(<AIAssistLabel source="hybrid" />);
    expect(screen.getByTestId('ai-assist-label')).toHaveAttribute('data-source', 'hybrid');
    expect(screen.getByText('Rule + AI model')).toBeInTheDocument();
  });

  it('uses custom label when provided', () => {
    render(<AIAssistLabel source="model" label="GPT-4o" />);
    expect(screen.getByText('GPT-4o')).toBeInTheDocument();
  });

  it('has accessible aria-label', () => {
    render(<AIAssistLabel source="rule" label="STRIDE policy" />);
    expect(screen.getByRole('img', { name: /AI assist source: STRIDE policy/i })).toBeInTheDocument();
  });

  it('includes tooltip via title attribute', () => {
    render(<AIAssistLabel source="model" />);
    const label = screen.getByTestId('ai-assist-label');
    expect(label.getAttribute('title')).toContain('AI language model');
  });
});
