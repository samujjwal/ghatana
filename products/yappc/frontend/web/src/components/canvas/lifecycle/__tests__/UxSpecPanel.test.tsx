import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { UxSpecPanel, type UxSpecPanelProps } from '../UxSpecPanel';
import type { UxSpecPayload } from '@/shared/types/lifecycle-artifacts';

function makeData(overrides: Partial<UxSpecPayload> = {}): UxSpecPayload {
  return {
    primaryFlows: [{ name: '', steps: [''], notes: '' }],
    iaNotes: '',
    a11yNotes: '',
    contentNotes: '',
    edgeCases: [''],
    ...overrides,
  };
}

function makeProps(overrides: Partial<UxSpecPanelProps> = {}): UxSpecPanelProps {
  return {
    onSave: vi.fn().mockResolvedValue(undefined),
    onClose: vi.fn(),
    ...overrides,
  };
}

describe('UxSpecPanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders header with title and subtitle', () => {
    render(<UxSpecPanel {...makeProps()} />);
    expect(screen.getByText('UX Specification')).toBeDefined();
    expect(screen.getByText('User flows, accessibility, and content guidelines')).toBeDefined();
  });

  it('renders Flows, Accessibility, Content tabs', () => {
    render(<UxSpecPanel {...makeProps()} />);
    expect(screen.getByRole('button', { name: /Flows/ })).toBeDefined();
    expect(screen.getByRole('button', { name: /Accessibility/ })).toBeDefined();
    expect(screen.getByRole('button', { name: /Content/ })).toBeDefined();
  });

  it('shows flow name input on default Flows tab', () => {
    render(<UxSpecPanel {...makeProps()} />);
    expect(screen.getByPlaceholderText('Flow name (e.g., User Registration)')).toBeDefined();
  });

  it('shows pre-filled flow name', () => {
    const data = makeData({
      primaryFlows: [{ name: 'User Login Flow', steps: ['Visit /login', 'Enter credentials'], notes: '' }],
    });
    render(<UxSpecPanel {...makeProps({ data })} />);
    expect(screen.getByDisplayValue('User Login Flow')).toBeDefined();
  });

  it('adds a new flow when Add Flow button clicked', () => {
    render(<UxSpecPanel {...makeProps()} />);
    const initial = screen.getAllByPlaceholderText('Flow name (e.g., User Registration)');
    fireEvent.click(screen.getByText('Add Flow'));
    const after = screen.getAllByPlaceholderText('Flow name (e.g., User Registration)');
    expect(after.length).toBe(initial.length + 1);
  });

  it('switches to Accessibility tab and shows a11y textarea', () => {
    render(<UxSpecPanel {...makeProps()} />);
    fireEvent.click(screen.getByRole('button', { name: /Accessibility/ }));
    expect(
      screen.getByPlaceholderText(
        'Document accessibility considerations, keyboard navigation, screen reader support, color contrast requirements...'
      )
    ).toBeDefined();
  });

  it('shows provided a11y notes on Accessibility tab', () => {
    const data = makeData({ a11yNotes: 'WCAG 2.1 AA compliance required' });
    render(<UxSpecPanel {...makeProps({ data })} />);
    fireEvent.click(screen.getByRole('button', { name: /Accessibility/ }));
    expect(screen.getByDisplayValue('WCAG 2.1 AA compliance required')).toBeDefined();
  });

  it('switches to Content tab', () => {
    render(<UxSpecPanel {...makeProps()} />);
    fireEvent.click(screen.getByRole('button', { name: /Content/ }));
    expect(
      screen.getByPlaceholderText('Navigation structure, page hierarchy, content organization...')
    ).toBeDefined();
  });

  it('shows provided content notes on Content tab', () => {
    const data = makeData({ iaNotes: 'Flat nav structure', contentNotes: '' });
    render(<UxSpecPanel {...makeProps({ data })} />);
    fireEvent.click(screen.getByRole('button', { name: /Content/ }));
    expect(screen.getByDisplayValue('Flat nav structure')).toBeDefined();
  });

  it('renders Save button', () => {
    render(<UxSpecPanel {...makeProps()} />);
    expect(screen.getByRole('button', { name: /Save/ })).toBeDefined();
  });

  it('calls onSave with uxSpec data when Save is clicked', async () => {
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<UxSpecPanel {...makeProps({ onSave })} />);
    fireEvent.click(screen.getByRole('button', { name: /Save/ }));
    await waitFor(() => {
      expect(onSave).toHaveBeenCalledOnce();
    });
    const [arg] = onSave.mock.calls[0] as [UxSpecPayload][];
    expect(arg).toHaveProperty('primaryFlows');
    expect(arg).toHaveProperty('a11yNotes');
  });

  it('shows Saving... while onSave is pending', async () => {
    let resolve: () => void;
    const onSave = vi.fn().mockReturnValue(new Promise<void>((r) => { resolve = r; }));
    render(<UxSpecPanel {...makeProps({ onSave })} />);
    fireEvent.click(screen.getByRole('button', { name: /Save/ }));
    expect(screen.getByText('Saving...')).toBeDefined();
    resolve!();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /Save/ })).toBeDefined();
    });
  });

  it('renders AI Critique button when onAIAssist provided', () => {
    const onAIAssist = vi.fn().mockResolvedValue(null);
    render(<UxSpecPanel {...makeProps({ onAIAssist })} />);
    expect(screen.getByText('AI Critique')).toBeDefined();
  });

  it('does not render AI Critique button when onAIAssist absent', () => {
    render(<UxSpecPanel {...makeProps({ onAIAssist: undefined })} />);
    expect(screen.queryByText('AI Critique')).toBeNull();
  });

  it('calls onAIAssist when AI Critique clicked', async () => {
    const onAIAssist = vi.fn().mockResolvedValue(null);
    render(<UxSpecPanel {...makeProps({ onAIAssist })} />);
    fireEvent.click(screen.getByText('AI Critique'));
    await waitFor(() => {
      expect(onAIAssist).toHaveBeenCalledOnce();
    });
  });
});
