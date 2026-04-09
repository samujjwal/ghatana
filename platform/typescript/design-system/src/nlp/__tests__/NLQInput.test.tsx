/**
 * Tests for NLQInput component
 */

import React from 'react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, renderHook } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NLQInput, useNLQParse, type NLPResponse } from '../NLQInput';

// Mock fetch
global.fetch = vi.fn();

// Mock dependencies — path relative to THIS test file (src/nlp/__tests__/)
vi.mock('../../voice', () => ({
  VoiceInput: ({ onTranscript }: { onTranscript: (text: string) => void }) => (
    <button onClick={() => onTranscript('voice transcript')}>Voice Input</button>
  ),
}));

vi.mock('../../privacy', () => ({
  useConsent: () => ({ consentGranted: true }),
}));

const createTestQueryClient = (): QueryClient =>
  new QueryClient({ defaultOptions: { mutations: { retry: false } } });

const TestWrapper = ({ children }: { children: React.ReactNode }): React.ReactElement => (
  <QueryClientProvider client={createTestQueryClient()}>{children}</QueryClientProvider>
);

describe('useNLQParse', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('parses query successfully', async () => {
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () =>
        ({
          intent: 'search',
          entities: { query: 'test' },
          confidence: 0.9,
          query: 'test query',
        }) satisfies NLPResponse,
    });

    const { result } = renderHook(() => useNLQParse(), { wrapper: TestWrapper });

    const response = await result.current.mutateAsync('test query');

    expect(response.intent).toBe('search');
    expect(response.confidence).toBe(0.9);
    expect(global.fetch).toHaveBeenCalledWith('/api/v1/nlp/parse', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: 'test query' }),
    });
  });

  it('handles API errors', async () => {
    (global.fetch as ReturnType<typeof vi.fn>).mockRejectedValueOnce(new Error('API Error'));

    const { result } = renderHook(() => useNLQParse(), { wrapper: TestWrapper });

    await expect(result.current.mutateAsync('test query')).rejects.toThrow('API Error');
  });
});

describe('NLQInput component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders input field and submit button', () => {
    const onQuery = vi.fn();

    render(
      <TestWrapper>
        <NLQInput onQuery={onQuery} />
      </TestWrapper>,
    );

    expect(screen.getByPlaceholderText('Ask a question...')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /submit/i })).toBeInTheDocument();
  });

  it('submits query when form is submitted', async () => {
    const onQuery = vi.fn();
    (global.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () =>
        ({
          intent: 'search',
          entities: {},
          confidence: 0.8,
          query: 'test query',
        }) satisfies NLPResponse,
    });

    render(
      <TestWrapper>
        <NLQInput onQuery={onQuery} />
      </TestWrapper>,
    );

    const input = screen.getByPlaceholderText('Ask a question...');
    const submitButton = screen.getByRole('button', { name: /submit/i });

    fireEvent.change(input, { target: { value: 'test query' } });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(onQuery).toHaveBeenCalledWith('test query', {
        intent: 'search',
        entities: {},
        confidence: 0.8,
        query: 'test query',
      });
    });
  });

  it('disables submit button when query is empty', () => {
    const onQuery = vi.fn();

    render(
      <TestWrapper>
        <NLQInput onQuery={onQuery} />
      </TestWrapper>,
    );

    const submitButton = screen.getByRole('button', { name: /submit/i });
    expect(submitButton).toBeDisabled();
  });

  it('enables submit button when query is not empty', () => {
    const onQuery = vi.fn();

    render(
      <TestWrapper>
        <NLQInput onQuery={onQuery} />
      </TestWrapper>,
    );

    const input = screen.getByPlaceholderText('Ask a question...');
    const submitButton = screen.getByRole('button', { name: /submit/i });

    fireEvent.change(input, { target: { value: 'test' } });
    expect(submitButton).not.toBeDisabled();
  });

  it('shows loading indicator during submission', async () => {
    const onQuery = vi.fn();
    (global.fetch as ReturnType<typeof vi.fn>).mockImplementation(() => new Promise(() => {}));

    render(
      <TestWrapper>
        <NLQInput onQuery={onQuery} />
      </TestWrapper>,
    );

    const input = screen.getByPlaceholderText('Ask a question...');
    const submitButton = screen.getByRole('button', { name: /submit/i });

    fireEvent.change(input, { target: { value: 'test query' } });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByLabelText('Loading')).toBeInTheDocument();
    });
  });

  it('renders voice input when enabled', () => {
    const onQuery = vi.fn();

    render(
      <TestWrapper>
        <NLQInput onQuery={onQuery} enableVoice={true} />
      </TestWrapper>,
    );

    expect(screen.getByText('Voice Input')).toBeInTheDocument();
  });

  it('does not render voice input when disabled', () => {
    const onQuery = vi.fn();

    render(
      <TestWrapper>
        <NLQInput onQuery={onQuery} enableVoice={false} />
      </TestWrapper>,
    );

    expect(screen.queryByText('Voice Input')).not.toBeInTheDocument();
  });

  it('handles voice transcript', () => {
    const onQuery = vi.fn();

    render(
      <TestWrapper>
        <NLQInput onQuery={onQuery} enableVoice={true} />
      </TestWrapper>,
    );

    const voiceButton = screen.getByText('Voice Input');
    fireEvent.click(voiceButton);

    const input = screen.getByPlaceholderText('Ask a question...');
    expect(input).toHaveValue('voice transcript');
  });

  it('shows AI suggestion hint when query is entered', () => {
    const onQuery = vi.fn();

    render(
      <TestWrapper>
        <NLQInput onQuery={onQuery} />
      </TestWrapper>,
    );

    const input = screen.getByPlaceholderText('Ask a question...');
    fireEvent.change(input, { target: { value: 'test query' } });

    expect(screen.getByText(/AI will help interpret your query/)).toBeInTheDocument();
  });

  it('uses custom placeholder', () => {
    const onQuery = vi.fn();

    render(
      <TestWrapper>
        <NLQInput onQuery={onQuery} placeholder="Custom placeholder" />
      </TestWrapper>,
    );

    expect(screen.getByPlaceholderText('Custom placeholder')).toBeInTheDocument();
  });

  it('is disabled when disabled prop is true', () => {
    const onQuery = vi.fn();

    render(
      <TestWrapper>
        <NLQInput onQuery={onQuery} disabled={true} />
      </TestWrapper>,
    );

    const input = screen.getByPlaceholderText('Ask a question...');
    expect(input).toBeDisabled();
  });

  it('applies custom className', () => {
    const onQuery = vi.fn();

    render(
      <TestWrapper>
        <NLQInput onQuery={onQuery} className="custom-class" />
      </TestWrapper>,
    );

    const container = screen.getByPlaceholderText('Ask a question...').closest('.custom-class');
    expect(container).not.toBeNull();
  });

  it('clears input when clear button is clicked', () => {
    const onQuery = vi.fn();

    render(
      <TestWrapper>
        <NLQInput onQuery={onQuery} />
      </TestWrapper>,
    );

    const input = screen.getByPlaceholderText('Ask a question...');
    fireEvent.change(input, { target: { value: 'test query' } });

    const clearButton = screen.getByText('×');
    fireEvent.click(clearButton);

    expect(input).toHaveValue('');
  });

  it('falls back to raw query when NLP parsing fails', async () => {
    const onQuery = vi.fn();
    (global.fetch as ReturnType<typeof vi.fn>).mockRejectedValueOnce(new Error('API Error'));

    render(
      <TestWrapper>
        <NLQInput onQuery={onQuery} />
      </TestWrapper>,
    );

    const input = screen.getByPlaceholderText('Ask a question...');
    const submitButton = screen.getByRole('button', { name: /submit/i });

    fireEvent.change(input, { target: { value: 'test query' } });
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(onQuery).toHaveBeenCalledWith('test query');
    });
  });
});
