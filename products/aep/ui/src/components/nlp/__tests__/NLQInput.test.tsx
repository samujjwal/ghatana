/**
 * Tests for NLQInput component
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { NLQInput, useNLQParse } from '../NLQInput';

// Mock fetch
global.fetch = vi.fn();

describe('NLQInput', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('renders input with placeholder', () => {
    const onQuery = vi.fn();
    render(
      <NLQInput
        onQuery={onQuery}
        placeholder="Ask anything..."
      />
    );

    const input = screen.getByPlaceholderText('Ask anything...');
    expect(input).toBeInTheDocument();
  });

  it('submits query on button click', async () => {
    const onQuery = vi.fn();
    localStorage.setItem('consent_ai_suggestions', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'ai_suggestions',
      granted: false,
      timestamp: new Date().toISOString(),
    }));

    render(
      <NLQInput
        onQuery={onQuery}
        placeholder="Ask anything..."
      />
    );

    const input = screen.getByPlaceholderText('Ask anything...');
    fireEvent.change(input, { target: { value: 'test query' } });

    const submitButton = screen.getByLabelText('Submit query');
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(onQuery).toHaveBeenCalledWith('test query');
    });
  });

  it('parses intent when consent granted', async () => {
    const onQuery = vi.fn();
    localStorage.setItem('consent_ai_suggestions', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'ai_suggestions',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        intent: 'search',
        entities: [{ type: 'keyword', value: 'test', confidence: 0.9 }],
        confidence: 0.9,
        query: 'test query',
      }),
    });

    render(
      <NLQInput
        onQuery={onQuery}
        placeholder="Ask anything..."
      />
    );

    const input = screen.getByPlaceholderText('Ask anything...');
    fireEvent.change(input, { target: { value: 'test query' } });

    const submitButton = screen.getByLabelText('Submit query');
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        '/api/v1/nlp/parse',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ text: 'test query' }),
        })
      );
    });

    await waitFor(() => {
      expect(onQuery).toHaveBeenCalledWith('test query', 'search', expect.any(Array));
    });
  });

  it('falls back to raw query when confidence below threshold', async () => {
    const onQuery = vi.fn();
    localStorage.setItem('consent_ai_suggestions', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'ai_suggestions',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        intent: 'search',
        entities: [],
        confidence: 0.3, // Below default threshold of 0.5
        query: 'test query',
      }),
    });

    render(
      <NLQInput
        onQuery={onQuery}
        placeholder="Ask anything..."
        confidenceThreshold={0.5}
      />
    );

    const input = screen.getByPlaceholderText('Ask anything...');
    fireEvent.change(input, { target: { value: 'test query' } });

    const submitButton = screen.getByLabelText('Submit query');
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(onQuery).toHaveBeenCalledWith('test query'); // No intent passed
    });
  });

  it('falls back to raw query when NLP parse fails', async () => {
    const onQuery = vi.fn();
    const onParseError = vi.fn();
    localStorage.setItem('consent_ai_suggestions', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'ai_suggestions',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    (global.fetch as any).mockRejectedValueOnce(new Error('Network error'));

    render(
      <NLQInput
        onQuery={onQuery}
        placeholder="Ask anything..."
        onParseError={onParseError}
      />
    );

    const input = screen.getByPlaceholderText('Ask anything...');
    fireEvent.change(input, { target: { value: 'test query' } });

    const submitButton = screen.getByLabelText('Submit query');
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(onQuery).toHaveBeenCalledWith('test query');
    });

    await waitFor(() => {
      expect(onParseError).toHaveBeenCalledWith(expect.any(Error));
    });
  });

  it('shows loading state while parsing', async () => {
    const onQuery = vi.fn();
    localStorage.setItem('consent_ai_suggestions', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'ai_suggestions',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    let resolvePromise: (value: any) => void = () => {};
    (global.fetch as any).mockImplementationOnce(() => {
      return new Promise((resolve) => {
        resolvePromise = resolve;
      });
    });

    render(
      <NLQInput
        onQuery={onQuery}
        placeholder="Ask anything..."
      />
    );

    const input = screen.getByPlaceholderText('Ask anything...');
    fireEvent.change(input, { target: { value: 'test query' } });

    const submitButton = screen.getByLabelText('Submit query');
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByLabelText('Processing')).toBeInTheDocument();
    });

    resolvePromise({
      ok: true,
      json: async () => ({
        intent: 'search',
        entities: [],
        confidence: 0.9,
        query: 'test query',
      }),
    });

    await waitFor(() => {
      expect(screen.queryByLabelText('Processing')).not.toBeInTheDocument();
    });
  });

  it('is disabled when disabled prop is true', () => {
    const onQuery = vi.fn();
    render(
      <NLQInput
        onQuery={onQuery}
        placeholder="Ask anything..."
        disabled
      />
    );

    const input = screen.getByPlaceholderText('Ask anything...');
    expect(input).toBeDisabled();

    const submitButton = screen.getByLabelText('Submit query');
    expect(submitButton).toBeDisabled();
  });

  it('uses custom endpoint when provided', async () => {
    const onQuery = vi.fn();
    localStorage.setItem('consent_ai_suggestions', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'ai_suggestions',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        intent: 'search',
        entities: [],
        confidence: 0.9,
        query: 'test query',
      }),
    });

    render(
      <NLQInput
        onQuery={onQuery}
        placeholder="Ask anything..."
        endpoint="/custom/nlp/endpoint"
      />
    );

    const input = screen.getByPlaceholderText('Ask anything...');
    fireEvent.change(input, { target: { value: 'test query' } });

    const submitButton = screen.getByLabelText('Submit query');
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        '/custom/nlp/endpoint',
        expect.any(Object)
      );
    });
  });
});

describe('useNLQParse hook', () => {
  it('provides parse function', () => {
    const { parse } = useNLQParse();
    expect(typeof parse).toBe('function');
  });

  it('provides isPending state', () => {
    const { isPending } = useNLQParse();
    expect(typeof isPending).toBe('boolean');
  });

  it('parses text successfully', async () => {
    (global.fetch as any).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        intent: 'search',
        entities: [{ type: 'keyword', value: 'test', confidence: 0.9 }],
        confidence: 0.9,
        query: 'test query',
      }),
    });

    const { parse } = useNLQParse();
    const result = await parse('test query');

    expect(result.intent).toBe('search');
    expect(result.confidence).toBe(0.9);
  });

  it('throws error on parse failure', async () => {
    (global.fetch as any).mockRejectedValueOnce(new Error('Network error'));

    const { parse } = useNLQParse();

    await expect(parse('test query')).rejects.toThrow('NLP parse failed');
  });
});
