/**
 * Tests for NLQInput component
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, renderHook } from '@testing-library/react';
import { NLQInput, useNLQParse } from '../NLQInput';
import { createAepTestWrapper } from '@/__tests__/test-utils/wrapper';
import * as aepApi from '@/api/aep.api';

vi.mock('@/api/aep.api', async () => {
  const actual = await vi.importActual<typeof import('@/api/aep.api')>('@/api/aep.api');
  return {
    ...actual,
    parseNlQuery: vi.fn(),
  };
});

const wrapper = createAepTestWrapper();

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
      />,
      { wrapper }
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
      />,
      { wrapper }
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

    vi.mocked(aepApi.parseNlQuery).mockResolvedValueOnce({
      intent: 'search',
      entities: [{ type: 'keyword', value: 'test', confidence: 0.9 }],
      confidence: 0.9,
      query: 'test query',
      tenantId: 'default',
      timestamp: new Date().toISOString(),
    });

    render(
      <NLQInput
        onQuery={onQuery}
        placeholder="Ask anything..."
      />,
      { wrapper }
    );

    const input = screen.getByPlaceholderText('Ask anything...');
    fireEvent.change(input, { target: { value: 'test query' } });

    const submitButton = screen.getByLabelText('Submit query');
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(aepApi.parseNlQuery).toHaveBeenCalledWith('test query', 'default', '/api/v1/nlp/parse');
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

    vi.mocked(aepApi.parseNlQuery).mockResolvedValueOnce({
      intent: 'search',
      entities: [],
      confidence: 0.3,
      query: 'test query',
      tenantId: 'default',
      timestamp: new Date().toISOString(),
    });

    render(
      <NLQInput
        onQuery={onQuery}
        placeholder="Ask anything..."
        confidenceThreshold={0.5}
      />,
      { wrapper }
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

    vi.mocked(aepApi.parseNlQuery).mockRejectedValueOnce(new Error('Network error'));

    render(
      <NLQInput
        onQuery={onQuery}
        placeholder="Ask anything..."
        onParseError={onParseError}
      />,
      { wrapper }
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

    let resolvePromise: ((value: aepApi.NlqParseResult) => void) | undefined;
    vi.mocked(aepApi.parseNlQuery).mockImplementationOnce(
      () => new Promise((resolve) => {
        resolvePromise = resolve;
      }),
    );

    render(
      <NLQInput
        onQuery={onQuery}
        placeholder="Ask anything..."
      />,
      { wrapper }
    );

    const input = screen.getByPlaceholderText('Ask anything...');
    fireEvent.change(input, { target: { value: 'test query' } });

    const submitButton = screen.getByLabelText('Submit query');
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(screen.getByText('Processing')).toBeInTheDocument();
    });

    resolvePromise?.({
        intent: 'search',
        entities: [],
        confidence: 0.9,
        query: 'test query',
        tenantId: 'default',
        timestamp: new Date().toISOString(),
    });

    await waitFor(() => {
      expect(screen.queryByText('Processing')).not.toBeInTheDocument();
    });
  });

  it('is disabled when disabled prop is true', () => {
    const onQuery = vi.fn();
    render(
      <NLQInput
        onQuery={onQuery}
        placeholder="Ask anything..."
        disabled
      />,
      { wrapper }
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

    vi.mocked(aepApi.parseNlQuery).mockResolvedValueOnce({
      intent: 'search',
      entities: [],
      confidence: 0.9,
      query: 'test query',
      tenantId: 'default',
      timestamp: new Date().toISOString(),
    });

    render(
      <NLQInput
        onQuery={onQuery}
        placeholder="Ask anything..."
        endpoint="/custom/nlp/endpoint"
      />,
      { wrapper }
    );

    const input = screen.getByPlaceholderText('Ask anything...');
    fireEvent.change(input, { target: { value: 'test query' } });

    const submitButton = screen.getByLabelText('Submit query');
    fireEvent.click(submitButton);

    await waitFor(() => {
      expect(aepApi.parseNlQuery).toHaveBeenCalledWith('test query', 'default', '/custom/nlp/endpoint');
    });
  });
});

describe('useNLQParse hook', () => {
  it('provides parse function', () => {
    const { result } = renderHook(() => useNLQParse(), { wrapper });
    expect(typeof result.current.parse).toBe('function');
  });

  it('provides isPending state', () => {
    const { result } = renderHook(() => useNLQParse(), { wrapper });
    expect(typeof result.current.isPending).toBe('boolean');
  });

  it('parses text successfully', async () => {
    vi.mocked(aepApi.parseNlQuery).mockResolvedValueOnce({
      intent: 'search',
      entities: [{ type: 'keyword', value: 'test', confidence: 0.9 }],
      confidence: 0.9,
      query: 'test query',
      tenantId: 'default',
      timestamp: new Date().toISOString(),
    });

    const { result } = renderHook(() => useNLQParse(), { wrapper });
    const parsed = await result.current.parse('test query');

    expect(parsed.intent).toBe('search');
    expect(parsed.confidence).toBe(0.9);
  });

  it('throws error on parse failure', async () => {
    vi.mocked(aepApi.parseNlQuery).mockRejectedValueOnce(new Error('Network error'));

    const { result } = renderHook(() => useNLQParse(), { wrapper });

    await expect(result.current.parse('test query')).rejects.toThrow('Network error');
  });
});
