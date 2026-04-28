/**
 * TemplateRecommendationCard tests (AI-Y9)
 */

import { render, screen, fireEvent } from '@testing-library/react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { TemplateRecommendationCard } from '../TemplateRecommendationCard';
import type { TemplateRecommendation } from '../../hooks/useTemplateRecommendation';

// ── Mock fetch ─────────────────────────────────────────────────────────────────

const mockFetch = vi.fn();
vi.stubGlobal('fetch', mockFetch);

function jsonOk(recs: TemplateRecommendation[]) {
  return Promise.resolve({
    ok: true,
    status: 200,
    json: () => Promise.resolve({ recommendations: recs }),
  } as Response);
}

function makeClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

function Wrapper({ children }: { children: React.ReactNode }) {
  return <QueryClientProvider client={makeClient()}>{children}</QueryClientProvider>;
}

const sampleRecs: TemplateRecommendation[] = [
  {
    templateId: 'web-saas',
    name: 'SaaS Web App',
    description: 'Full-stack template for SaaS products.',
    tags: ['react', 'api', 'auth'],
    confidence: 0.91,
  },
  {
    templateId: 'mobile-app',
    name: 'Mobile App',
    description: 'React Native starter.',
    tags: ['react-native'],
    confidence: 0.72,
  },
];

// ── Tests ──────────────────────────────────────────────────────────────────────

describe('TemplateRecommendationCard (AI-Y9)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders nothing when context has no role or goal', () => {
    const { container } = render(
      <Wrapper>
        <TemplateRecommendationCard context={{}} />
      </Wrapper>
    );
    expect(container.firstChild).toBeNull();
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('shows loading state while fetching', () => {
    mockFetch.mockReturnValue(new Promise(() => {}));

    render(
      <Wrapper>
        <TemplateRecommendationCard context={{ role: 'engineer' }} />
      </Wrapper>
    );

    expect(screen.getByTestId('template-rec-loading')).toBeInTheDocument();
  });

  it('renders recommendation cards', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleRecs));

    render(
      <Wrapper>
        <TemplateRecommendationCard context={{ role: 'engineer', goal: 'ship saas' }} />
      </Wrapper>
    );

    expect(await screen.findByTestId('template-rec-panel')).toBeInTheDocument();
    expect(screen.getByTestId('template-rec-web-saas')).toBeInTheDocument();
    expect(screen.getByText('SaaS Web App')).toBeInTheDocument();
    expect(screen.getByTestId('template-rec-mobile-app')).toBeInTheDocument();
  });

  it('calls onSelect with templateId when clicked', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleRecs));
    const onSelect = vi.fn();

    render(
      <Wrapper>
        <TemplateRecommendationCard context={{ role: 'engineer' }} onSelect={onSelect} />
      </Wrapper>
    );

    await screen.findByTestId('template-rec-web-saas');
    fireEvent.click(screen.getByTestId('template-rec-web-saas'));
    expect(onSelect).toHaveBeenCalledWith('web-saas');
  });

  it('shows selected state via aria-pressed', async () => {
    mockFetch.mockReturnValue(jsonOk(sampleRecs));

    render(
      <Wrapper>
        <TemplateRecommendationCard
          context={{ role: 'engineer' }}
          selectedTemplateId="web-saas"
        />
      </Wrapper>
    );

    const item = await screen.findByTestId('template-rec-web-saas');
    expect(item).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByTestId('template-rec-mobile-app')).toHaveAttribute('aria-pressed', 'false');
  });

  it('shows error state on fetch failure', async () => {
    mockFetch.mockReturnValue(Promise.resolve({ ok: false, status: 500, json: () => Promise.resolve({}) }));

    render(
      <Wrapper>
        <TemplateRecommendationCard context={{ role: 'engineer' }} />
      </Wrapper>
    );

    expect(await screen.findByTestId('template-rec-error')).toBeInTheDocument();
  });
});
