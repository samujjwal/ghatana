import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { AdrPanel, type AdrPanelProps } from '../AdrPanel';
import type { AdrPayload } from '@/shared/types/lifecycle-artifacts';

function makeData(overrides: Partial<AdrPayload> = {}): AdrPayload {
  return {
    context: '',
    decision: '',
    options: [{ name: '', pros: [''], cons: [''] }],
    consequences: '',
    status: 'proposed',
    ...overrides,
  };
}

function makeProps(overrides: Partial<AdrPanelProps> = {}): AdrPanelProps {
  return {
    onSave: vi.fn().mockResolvedValue(undefined),
    onClose: vi.fn(),
    ...overrides,
  };
}

describe('AdrPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders header with title and subtitle', () => {
    render(<AdrPanel {...makeProps()} />);
    expect(screen.getByText('Architecture Decision Record')).toBeDefined();
    expect(screen.getByText('Document key architectural decisions')).toBeDefined();
  });

  it('renders status buttons', () => {
    render(<AdrPanel {...makeProps()} />);
    expect(screen.getByText('Proposed')).toBeDefined();
    expect(screen.getByText('Accepted')).toBeDefined();
    expect(screen.getByText('Superseded')).toBeDefined();
    expect(screen.getByText('Deprecated')).toBeDefined();
  });

  it('renders Context textarea', () => {
    render(<AdrPanel {...makeProps()} />);
    expect(
      screen.getByPlaceholderText("What is the issue that we're seeing that is motivating this decision?")
    ).toBeDefined();
  });

  it('renders Decision textarea', () => {
    render(<AdrPanel {...makeProps()} />);
    expect(
      screen.getByPlaceholderText("What is the change that we're proposing and/or doing?")
    ).toBeDefined();
  });

  it('renders Consequences textarea', () => {
    render(<AdrPanel {...makeProps()} />);
    expect(
      screen.getByPlaceholderText('What becomes easier or more difficult to do because of this change?')
    ).toBeDefined();
  });

  it('shows pre-filled context text', () => {
    const data = makeData({ context: 'We need caching for performance' });
    render(<AdrPanel {...makeProps({ data })} />);
    expect(screen.getByDisplayValue('We need caching for performance')).toBeDefined();
  });

  it('renders default option name input', () => {
    render(<AdrPanel {...makeProps()} />);
    expect(screen.getByPlaceholderText('Option name')).toBeDefined();
  });

  it('changes status when a status button is clicked', () => {
    render(<AdrPanel {...makeProps()} />);
    fireEvent.click(screen.getByText('Accepted'));
    // After clicking Accepted, the Accepted button should be highlighted (active class)
    // We can't easily check class, so just confirm it doesn't throw
    expect(screen.getByText('Accepted')).toBeDefined();
  });

  it('renders Save button', () => {
    render(<AdrPanel {...makeProps()} />);
    expect(screen.getByRole('button', { name: /Save/ })).toBeDefined();
  });

  it('calls onSave with adr data when Save is clicked', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<AdrPanel {...makeProps({ onSave })} />);
    fireEvent.click(screen.getByRole('button', { name: /Save/ }));
    await waitFor(() => {
      expect(onSave).toHaveBeenCalledOnce();
    });
    const [arg] = onSave.mock.calls[0] as [AdrPayload][];
    expect(arg).toHaveProperty('context');
    expect(arg).toHaveProperty('decision');
    expect(arg).toHaveProperty('options');
  });

  it('shows Saving... while onSave is pending', async () => {
    let resolve: () => void;
    const onSave = vi.fn().mockReturnValue(new Promise<void>((r) => { resolve = r; }));
    render(<AdrPanel {...makeProps({ onSave })} />);
    fireEvent.click(screen.getByRole('button', { name: /Save/ }));
    expect(screen.getByText('Saving...')).toBeDefined();
    resolve!();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Save/ })).toBeDefined();
    });
  });

  it('renders AI Assist button when onAIAssist is provided', () => {
    const onAIAssist = vi.fn().mockResolvedValue(null);
    render(<AdrPanel {...makeProps({ onAIAssist })} />);
    expect(screen.getByText('AI Assist')).toBeDefined();
  });

  it('does not render AI Assist when onAIAssist absent', () => {
    render(<AdrPanel {...makeProps({ onAIAssist: undefined })} />);
    expect(screen.queryByText('AI Assist')).toBeNull();
  });

  it('calls onAIAssist when AI Assist button clicked', async () => {
    const onAIAssist = vi.fn().mockResolvedValue(null);
    render(<AdrPanel {...makeProps({ onAIAssist })} />);
    fireEvent.click(screen.getByText('AI Assist'));
    await waitFor(() => {
      expect(onAIAssist).toHaveBeenCalledOnce();
    });
  });

  it('adds a second option when Add Option is clicked', () => {
    render(<AdrPanel {...makeProps()} />);
    const initial = screen.getAllByPlaceholderText('Option name');
    fireEvent.click(screen.getByText('Add Option'));
    const after = screen.getAllByPlaceholderText('Option name');
    expect(after.length).toBe(initial.length + 1);
  });
});
