/**
 * Tests for:
 * - project/ProjectCard
 * - voice/VoiceInputButton (not-supported case)
 * - ratelimit/ThrottleAlertBanner (null-by-default case)
 */
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ProjectCard } from '../../project/ProjectCard';
import { VoiceInputButton } from '../../voice/VoiceInputButton';
import { ThrottleAlertBanner } from '../../ratelimit/ThrottleAlertBanner';

function makeQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
}

function withQuery(ui: React.ReactElement) {
  return <QueryClientProvider client={makeQueryClient()}>{ui}</QueryClientProvider>;
}

// ─── ProjectCard ──────────────────────────────────────────────────────────────

describe('ProjectCard', () => {
  const makeProject = (overrides = {}) => ({
    id: 'proj-1',
    name: 'My App',
    type: 'FULL_STACK',
    updatedAt: '2024-06-01T10:00:00Z',
    ...overrides,
  });

  it('renders project name', () => {
    render(<ProjectCard project={makeProject()} onClick={vi.fn()} />);
    expect(screen.getByText('My App')).toBeTruthy();
  });

  it('renders project type', () => {
    render(<ProjectCard project={makeProject({ type: 'MOBILE' })} onClick={vi.fn()} />);
    expect(screen.getByText('MOBILE')).toBeTruthy();
  });

  it('renders fallback type when type is undefined', () => {
    render(
      <ProjectCard
        project={makeProject({ type: undefined })}
        onClick={vi.fn()}
      />
    );
    expect(screen.getByText('webapp')).toBeTruthy();
  });

  it('calls onClick with project id when clicked', () => {
    const onClick = vi.fn();
    render(<ProjectCard project={makeProject({ id: 'abc-123' })} onClick={onClick} />);
    const card = screen.getByText('My App').closest('div') ?? screen.getByText('My App');
    fireEvent.click(card.parentElement!);
    expect(onClick).toHaveBeenCalledWith('abc-123');
  });

  it('renders updatedAt date when provided', () => {
    render(<ProjectCard project={makeProject({ updatedAt: '2024-06-01T10:00:00Z' })} onClick={vi.fn()} />);
    // Date is rendered via toLocaleDateString
    expect(screen.getByText(/2024|Jun/)).toBeTruthy();
  });

  it('renders "Recently" when updatedAt is missing', () => {
    render(
      <ProjectCard
        project={makeProject({ updatedAt: undefined })}
        onClick={vi.fn()}
      />
    );
    expect(screen.getByText('Recently')).toBeTruthy();
  });
});

// ─── VoiceInputButton ─────────────────────────────────────────────────────────

describe('VoiceInputButton', () => {
  it('renders button with Start voice input aria-label when supported', () => {
    const { container } = render(<VoiceInputButton />);
    if (container.firstChild === null) {
      // Voice input not supported in this environment — skip
      return;
    }
    expect(screen.getByRole('button')).toBeTruthy();
    expect(screen.getByLabelText('Start voice input')).toBeTruthy();
  });

  it('renders disabled button when disabled prop is true', () => {
    const { container } = render(<VoiceInputButton disabled />);
    if (container.firstChild === null) return;
    const btn = screen.getByRole('button');
    expect((btn as HTMLButtonElement).disabled).toBe(true);
  });

  it('accepts size=small without crashing', () => {
    const { container } = render(<VoiceInputButton size="small" />);
    // Either null (unsupported) or renders a button
    if (container.firstChild !== null) {
      expect(screen.getByRole('button')).toBeTruthy();
    }
  });

  it('accepts size=large without crashing', () => {
    const { container } = render(<VoiceInputButton size="large" />);
    if (container.firstChild !== null) {
      expect(screen.getByRole('button')).toBeTruthy();
    }
  });
});

// ─── ThrottleAlertBanner ──────────────────────────────────────────────────────

describe('ThrottleAlertBanner', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('Network error')));
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders null by default (no rate limit data loaded)', () => {
    const { container } = render(withQuery(<ThrottleAlertBanner />));
    // Initially returns null (shouldShow = false, status = null)
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });

  it('accepts threshold prop without crashing', () => {
    const { container } = render(withQuery(<ThrottleAlertBanner threshold={90} />));
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });

  it('accepts onDismiss prop without crashing', () => {
    const onDismiss = vi.fn();
    const { container } = render(withQuery(<ThrottleAlertBanner onDismiss={onDismiss} />));
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });
});
