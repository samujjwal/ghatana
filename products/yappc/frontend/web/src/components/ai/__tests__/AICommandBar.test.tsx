import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AICommandBar } from '../AICommandBar';

const defaultProps = {
  onSubmit: vi.fn().mockResolvedValue(undefined),
};

describe('AICommandBar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders the AI prompt input', () => {
    render(<AICommandBar {...defaultProps} />);
    expect(screen.getByRole('textbox', { name: /AI prompt input/i })).toBeInTheDocument();
  });

  it('renders submit button', () => {
    render(<AICommandBar {...defaultProps} />);
    expect(screen.getByRole('button', { name: /Submit/i })).toBeInTheDocument();
  });

  it('submit button is disabled when input is empty', () => {
    render(<AICommandBar {...defaultProps} />);
    expect(screen.getByRole('button', { name: /Submit/i })).toBeDisabled();
  });

  it('enables submit when text is entered', () => {
    render(<AICommandBar {...defaultProps} />);
    const input = screen.getByRole('textbox', { name: /AI prompt input/i });
    fireEvent.change(input, { target: { value: 'Generate a login form' } });
    expect(screen.getByRole('button', { name: /Submit/i })).not.toBeDisabled();
  });

  it('calls onSubmit with the prompt when submit clicked', async () => {
    render(<AICommandBar {...defaultProps} />);
    const input = screen.getByRole('textbox', { name: /AI prompt input/i });
    fireEvent.change(input, { target: { value: 'Generate a form' } });
    fireEvent.click(screen.getByRole('button', { name: /Submit/i }));
    await waitFor(() => {
      expect(defaultProps.onSubmit).toHaveBeenCalledWith('Generate a form', expect.anything());
    });
  });

  it('calls onSubmit when Enter key pressed in input', async () => {
    render(<AICommandBar {...defaultProps} />);
    const input = screen.getByRole('textbox', { name: /AI prompt input/i });
    fireEvent.change(input, { target: { value: 'Explain this' } });
    fireEvent.keyDown(input, { key: 'Enter' });
    await waitFor(() => {
      expect(defaultProps.onSubmit).toHaveBeenCalledWith('Explain this', expect.anything());
    });
  });

  it('disables input when isProcessing=true', () => {
    render(<AICommandBar {...defaultProps} isProcessing={true} />);
    expect(screen.getByRole('textbox', { name: /AI prompt input/i })).toBeDisabled();
  });

  it('disables submit when isProcessing=true', () => {
    render(<AICommandBar {...defaultProps} isProcessing={true} />);
    expect(screen.getByRole('button', { name: /Submit/i })).toBeDisabled();
  });

  it('shows recent prompts history button when recentPrompts provided', () => {
    render(<AICommandBar {...defaultProps} recentPrompts={['past prompt']} />);
    expect(screen.getByRole('button', { name: /Show recent prompts/i })).toBeInTheDocument();
  });

  it('does not show history button when no recentPrompts', () => {
    render(<AICommandBar {...defaultProps} recentPrompts={[]} />);
    expect(screen.queryByRole('button', { name: /Show recent prompts/i })).not.toBeInTheDocument();
  });

  it('renders expand/collapse button', () => {
    render(<AICommandBar {...defaultProps} />);
    const expandBtns = screen.queryAllByRole('button', { name: /Expand/i });
    const collapseBtns = screen.queryAllByRole('button', { name: /Collapse/i });
    expect(expandBtns.length + collapseBtns.length).toBeGreaterThan(0);
  });

  it('shows "Open full panel" button when onOpenFullPanel is provided', () => {
    const onOpenFullPanel = vi.fn();
    render(<AICommandBar {...defaultProps} onOpenFullPanel={onOpenFullPanel} />);
    expect(screen.getByText('Open full panel →')).toBeInTheDocument();
  });

  it('calls onOpenFullPanel when open full panel button clicked', () => {
    const onOpenFullPanel = vi.fn();
    render(<AICommandBar {...defaultProps} onOpenFullPanel={onOpenFullPanel} />);
    fireEvent.click(screen.getByText('Open full panel →'));
    expect(onOpenFullPanel).toHaveBeenCalledTimes(1);
  });

  it('shows quick action chips when input is empty', () => {
    render(<AICommandBar {...defaultProps} currentMode="brainstorm" />);
    expect(screen.getByText('Expand idea')).toBeInTheDocument();
  });

  it('does not show quick actions when input has text', () => {
    render(<AICommandBar {...defaultProps} currentMode="brainstorm" />);
    const input = screen.getByRole('textbox', { name: /AI prompt input/i });
    fireEvent.change(input, { target: { value: 'some text' } });
    expect(screen.queryByText('Expand idea')).not.toBeInTheDocument();
  });

  it('renders clear button when input has value', () => {
    render(<AICommandBar {...defaultProps} />);
    const input = screen.getByRole('textbox', { name: /AI prompt input/i });
    fireEvent.change(input, { target: { value: 'abc' } });
    expect(screen.getByRole('button', { name: /Clear/i })).toBeInTheDocument();
  });

  it('clears input when clear button clicked', () => {
    render(<AICommandBar {...defaultProps} />);
    const input = screen.getByRole('textbox', { name: /AI prompt input/i });
    fireEvent.change(input, { target: { value: 'abc' } });
    fireEvent.click(screen.getByRole('button', { name: /Clear/i }));
    expect(input).toHaveValue('');
  });
});
