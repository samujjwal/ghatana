/**
 * VoiceCommandBar tests
 *
 * AEP-P2-008: Tests for voice command palette functionality
 *
 * @doc.type test
 * @doc.purpose Test voice command bar component and intent parsing
 * @doc.layer frontend
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { VoiceCommandBar, type VoiceIntent } from '@/components/voice/VoiceCommandBar';

describe('VoiceCommandBar', () => {
  const onCommandMock = vi.fn();

  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders closed state with Cmd+K button', () => {
    render(<VoiceCommandBar onCommand={onCommandMock} />);

    expect(screen.getByText('Cmd+K')).toBeInTheDocument();
    expect(screen.getByLabelText(/open voice commands/i)).toBeInTheDocument();
  });

  it('opens when button is clicked', async () => {
    render(<VoiceCommandBar onCommand={onCommandMock} />);

    const button = screen.getByLabelText(/open voice commands/i);
    await userEvent.click(button);

    expect(screen.getByPlaceholderText(/Say "Go to monitoring"/i)).toBeInTheDocument();
    expect(screen.getByText(/Available commands:/i)).toBeInTheDocument();
  });

  it('opens when Cmd+K is pressed', async () => {
    render(<VoiceCommandBar onCommand={onCommandMock} enableShortcut />);

    fireEvent.keyDown(window, { key: 'k', metaKey: true });

    expect(screen.getByPlaceholderText(/Say "Go to monitoring"/i)).toBeInTheDocument();
  });

  it('closes when Escape is pressed', async () => {
    render(<VoiceCommandBar onCommand={onCommandMock} enableShortcut />);

    fireEvent.keyDown(window, { key: 'k', metaKey: true });
    expect(screen.getByPlaceholderText(/Say "Go to monitoring"/i)).toBeInTheDocument();

    fireEvent.keyDown(window, { key: 'Escape' });
    expect(screen.queryByPlaceholderText(/Say "Go to monitoring"/i)).not.toBeInTheDocument();
  });

  it('closes when close button is clicked', async () => {
    render(<VoiceCommandBar onCommand={onCommandMock} />);

    const openButton = screen.getByLabelText(/open voice commands/i);
    await userEvent.click(openButton);

    const closeButton = screen.getByLabelText(/Close/i);
    await userEvent.click(closeButton);

    expect(screen.queryByPlaceholderText(/Say "Go to monitoring"/i)).not.toBeInTheDocument();
  });

  it('parses navigate command correctly', async () => {
    render(<VoiceCommandBar onCommand={onCommandMock} />);

    await userEvent.click(screen.getByLabelText(/open voice commands/i));
    const input = screen.getByPlaceholderText(/Say "Go to monitoring"/i);

    await userEvent.type(input, 'Go to monitoring');
    fireEvent.change(input, { target: { value: 'Go to monitoring' } });

    expect(onCommandMock).toHaveBeenCalledWith({
      action: 'navigate',
      target: 'monitoring',
    });
  });

  it('parses trigger command correctly', async () => {
    render(<VoiceCommandBar onCommand={onCommandMock} />);

    await userEvent.click(screen.getByLabelText(/open voice commands/i));
    const input = screen.getByPlaceholderText(/Say "Go to monitoring"/i);

    fireEvent.change(input, { target: { value: 'Trigger pipeline-123' } });

    expect(onCommandMock).toHaveBeenCalledWith({
      action: 'trigger',
      target: 'pipeline-123',
    });
  });

  it('parses approve command correctly', async () => {
    render(<VoiceCommandBar onCommand={onCommandMock} />);

    await userEvent.click(screen.getByLabelText(/open voice commands/i));
    const input = screen.getByPlaceholderText(/Say "Go to monitoring"/i);

    fireEvent.change(input, { target: { value: 'Approve review-item-456' } });

    expect(onCommandMock).toHaveBeenCalledWith({
      action: 'approve',
      target: 'review-item-456',
    });
  });

  it('parses reject command correctly', async () => {
    render(<VoiceCommandBar onCommand={onCommandMock} />);

    await userEvent.click(screen.getByLabelText(/open voice commands/i));
    const input = screen.getByPlaceholderText(/Say "Go to monitoring"/i);

    fireEvent.change(input, { target: { value: 'Reject review-item-789' } });

    expect(onCommandMock).toHaveBeenCalledWith({
      action: 'reject',
      target: 'review-item-789',
    });
  });

  it('parses search command correctly', async () => {
    render(<VoiceCommandBar onCommand={onCommandMock} />);

    await userEvent.click(screen.getByLabelText(/open voice commands/i));
    const input = screen.getByPlaceholderText(/Say "Go to monitoring"/i);

    fireEvent.change(input, { target: { value: 'Search error logs' } });

    expect(onCommandMock).toHaveBeenCalledWith({
      action: 'search',
      target: 'error logs',
    });
  });

  it('parses cancel command correctly', async () => {
    render(<VoiceCommandBar onCommand={onCommandMock} />);

    await userEvent.click(screen.getByLabelText(/open voice commands/i));
    const input = screen.getByPlaceholderText(/Say "Go to monitoring"/i);

    fireEvent.change(input, { target: { value: 'Cancel' } });

    expect(onCommandMock).toHaveBeenCalledWith({
      action: 'cancel',
    });
  });

  it('does not trigger on unrecognized command', async () => {
    render(<VoiceCommandBar onCommand={onCommandMock} />);

    await userEvent.click(screen.getByLabelText(/open voice commands/i));
    const input = screen.getByPlaceholderText(/Say "Go to monitoring"/i);

    fireEvent.change(input, { target: { value: 'This is not a valid command' } });

    expect(onCommandMock).not.toHaveBeenCalled();
  });

  it('uses custom placeholder when provided', () => {
    render(
      <VoiceCommandBar
        onCommand={onCommandMock}
        placeholder="Custom placeholder text"
      />
    );

    const openButton = screen.getByLabelText(/open voice commands/i);
    await userEvent.click(openButton);

    expect(screen.getByPlaceholderText('Custom placeholder text')).toBeInTheDocument();
  });

  it('does not respond to keyboard shortcut when disabled', () => {
    render(<VoiceCommandBar onCommand={onCommandMock} enableShortcut={false} />);

    fireEvent.keyDown(window, { key: 'k', metaKey: true });

    expect(screen.queryByPlaceholderText(/Say "Go to monitoring"/i)).not.toBeInTheDocument();
  });

  it('displays available commands list when open', async () => {
    render(<VoiceCommandBar onCommand={onCommandMock} />);

    await userEvent.click(screen.getByLabelText(/open voice commands/i));

    expect(screen.getByText(/Available commands:/i)).toBeInTheDocument();
    expect(screen.getByText(/"Go to monitoring"/i)).toBeInTheDocument();
    expect(screen.getByText(/"Trigger pipeline"/i)).toBeInTheDocument();
    expect(screen.getByText(/"Approve item"/i)).toBeInTheDocument();
    expect(screen.getByText(/"Reject item"/i)).toBeInTheDocument();
    expect(screen.getByText(/"Search \[query\]"/i)).toBeInTheDocument();
    expect(screen.getByText(/"Cancel"/i)).toBeInTheDocument();
  });

  it('applies custom className to button', () => {
    render(
      <VoiceCommandBar onCommand={onCommandMock} className="custom-class" />
    );

    const button = screen.getByLabelText(/open voice commands/i);
    expect(button).toHaveClass('custom-class');
  });
});
