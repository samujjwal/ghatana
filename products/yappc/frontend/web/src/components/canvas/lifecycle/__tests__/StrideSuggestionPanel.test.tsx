/**
 * StrideSuggestionPanel tests (AI-Y11)
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { StrideSuggestionPanel } from '../StrideSuggestionPanel';
import type { StrideSuggestionData } from '../StrideSuggestionPanel';

// ── Mock fetch ─────────────────────────────────────────────────────────────────

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

function jsonOk(data: unknown) {
  return Promise.resolve({
    ok: true,
    status: 200,
    json: () => Promise.resolve(data),
  } as Response);
}

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function Wrapper({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={makeClient()}>{children}</QueryClientProvider>;
}

const sampleData: StrideSuggestionData = {
  modelId: 'tm-1',
  suggestions: [
    {
      id: 's1',
      category: 'Spoofing',
      threat: 'Attacker impersonates admin via stolen session cookie.',
      mitigationHint: 'Rotate session tokens after privilege escalation.',
      confidence: 0.85,
      source: 'model',
    },
    {
      id: 's2',
      category: 'DenialOfService',
      threat: 'Flood of unauthenticated requests exhausts connection pool.',
      confidence: 0.71,
      source: 'rule',
    },
  ],
};

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('StrideSuggestionPanel (AI-Y11)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows loading state initially', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));

    render(
      <Wrapper>
        <StrideSuggestionPanel modelId="tm-1" />
      </Wrapper>
    );

    expect(screen.getByTestId('stride-loading')).toBeInTheDocument();
  });

  it('renders suggestions with category and threat text', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleData));

    render(
      <Wrapper>
        <StrideSuggestionPanel modelId="tm-1" />
      </Wrapper>
    );

    expect(await screen.findByTestId('stride-suggestion-panel')).toBeInTheDocument();
    expect(screen.getByTestId('stride-suggestion-s1')).toBeInTheDocument();
    expect(screen.getByText('Attacker impersonates admin via stolen session cookie.')).toBeInTheDocument();
    expect(screen.getByTestId('stride-suggestion-s2')).toBeInTheDocument();
  });

  it('shows "Add to model" buttons and calls onAddThreat', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleData));
    const onAdd = vi.fn();

    render(
      <Wrapper>
        <StrideSuggestionPanel modelId="tm-1" onAddThreat={onAdd} />
      </Wrapper>
    );

    await screen.findByTestId('stride-suggestion-s1');
    fireEvent.click(screen.getByTestId('stride-add-s1'));
    expect(onAdd).toHaveBeenCalledWith(expect.objectContaining({ id: 's1' }));
  });

  it('shows empty state when no suggestions', async () => {
    mockFetch.mockReturnValue(jsonOk({ modelId: 'tm-1', suggestions: [] }));

    render(
      <Wrapper>
        <StrideSuggestionPanel modelId="tm-1" />
      </Wrapper>
    );

    expect(await screen.findByTestId('stride-empty')).toBeInTheDocument();
  });

  it('shows error state on fetch failure', async () => {
    mockFetch.mockReturnValue(Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) }));

    render(
      <Wrapper>
        <StrideSuggestionPanel modelId="tm-1" />
      </Wrapper>
    );

    expect(await screen.findByTestId('stride-error')).toBeInTheDocument();
  });

  it('renders nothing when modelId is empty', () => {
    const { container } = render(
      <Wrapper>
        <StrideSuggestionPanel modelId="" />
      </Wrapper>
    );
    expect(container.firstChild).toBeNull();
    expect(mockFetch).not.toHaveBeenCalled();
  });
});
