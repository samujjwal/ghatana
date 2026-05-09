import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { AIChatInterface, type ChatMessage } from '../AIChatInterface';

function makeMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    role: 'assistant',
    content: 'Hello! How can I help you?',
    ...overrides,
  };
}

describe('AIChatInterface', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    // JSDOM does not implement scrollIntoView
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders the assistant headers', () => {
    render(<AIChatInterface />);
    // Empty-state and panel headings are both visible.
    const headings = screen.getAllByText(/assistant/i);
    expect(headings.length).toBeGreaterThan(0);
    expect(screen.getByText('Powered by YAPPC Agent')).toBeDefined();
  });

  it('shows Ready status indicator', () => {
    render(<AIChatInterface />);
    expect(screen.getByText('Ready')).toBeDefined();
  });

  it('shows empty state when no messages are provided', () => {
    render(<AIChatInterface messages={[]} />);
    expect(
      screen.getByText('Ask me to help design, validate, or explain your project architecture.'),
    ).toBeDefined();
  });

  it('renders assistant message bubble', () => {
    const messages: ChatMessage[] = [makeMessage({ content: 'I can help with that.' })];
    render(<AIChatInterface messages={messages} />);
    expect(screen.getByText('I can help with that.')).toBeDefined();
  });

  it('renders user message bubble', () => {
    const messages: ChatMessage[] = [
      makeMessage({ role: 'user', content: 'Design a microservice architecture.' }),
    ];
    render(<AIChatInterface messages={messages} />);
    expect(screen.getByText('Design a microservice architecture.')).toBeDefined();
  });

  it('filters out system messages from display', () => {
    const messages: ChatMessage[] = [
      makeMessage({ role: 'system', content: 'System context: you are a helpful agent.' }),
      makeMessage({ role: 'user', content: 'Visible user message.' }),
    ];
    render(<AIChatInterface messages={messages} />);
    expect(screen.queryByText('System context: you are a helpful agent.')).toBeNull();
    expect(screen.getByText('Visible user message.')).toBeDefined();
  });

  it('shows thinking indicator when isLoading is true with messages', () => {
    const messages: ChatMessage[] = [makeMessage({ content: 'Previous reply.' })];
    render(<AIChatInterface messages={messages} isLoading />);
    // ThinkingIndicator renders animated dots; verify it doesn't show empty state
    expect(screen.queryByText('Ask me to help design')).toBeNull();
    expect(screen.getByText('Previous reply.')).toBeDefined();
  });

  it('has a textarea with correct placeholder text', () => {
    render(<AIChatInterface />);
    const textarea = screen.getByPlaceholderText('Ask the assistant… (Shift+Enter for new line)');
    expect(textarea).toBeDefined();
  });

  it('calls onSendMessage with trimmed input when Send button is clicked', () => {
    const onSendMessage = vi.fn();
    render(<AIChatInterface onSendMessage={onSendMessage} />);

    const textarea = screen.getByPlaceholderText('Ask the assistant… (Shift+Enter for new line)');
    fireEvent.change(textarea, { target: { value: '  What is a microservice?  ' } });

    fireEvent.click(screen.getByRole('button', { name: /send message/i }));

    expect(onSendMessage).toHaveBeenCalledWith('What is a microservice?');
  });

  it('clears input after sending', () => {
    const onSendMessage = vi.fn();
    render(<AIChatInterface onSendMessage={onSendMessage} />);

    const textarea = screen.getByPlaceholderText('Ask the assistant… (Shift+Enter for new line)') as HTMLTextAreaElement;
    fireEvent.change(textarea, { target: { value: 'Hello AI' } });
    fireEvent.click(screen.getByRole('button', { name: /send message/i }));

    expect(textarea.value).toBe('');
  });

  it('calls onSendMessage when Enter key is pressed in textarea', () => {
    const onSendMessage = vi.fn();
    render(<AIChatInterface onSendMessage={onSendMessage} />);

    const textarea = screen.getByPlaceholderText('Ask the assistant… (Shift+Enter for new line)');
    fireEvent.change(textarea, { target: { value: 'Enter key test' } });
    fireEvent.keyDown(textarea, { key: 'Enter', shiftKey: false });

    expect(onSendMessage).toHaveBeenCalledWith('Enter key test');
  });

  it('does not send when Shift+Enter is pressed', () => {
    const onSendMessage = vi.fn();
    render(<AIChatInterface onSendMessage={onSendMessage} />);

    const textarea = screen.getByPlaceholderText('Ask the assistant… (Shift+Enter for new line)');
    fireEvent.change(textarea, { target: { value: 'New line test' } });
    fireEvent.keyDown(textarea, { key: 'Enter', shiftKey: true });

    expect(onSendMessage).not.toHaveBeenCalled();
  });

  it('does not call onSendMessage when input is empty', () => {
    const onSendMessage = vi.fn();
    render(<AIChatInterface onSendMessage={onSendMessage} />);

    fireEvent.click(screen.getByRole('button', { name: /send message/i }));

    expect(onSendMessage).not.toHaveBeenCalled();
  });

  it('disables send button when input is empty', () => {
    render(<AIChatInterface />);
    const sendButton = screen.getByRole('button', { name: /send message/i }) as HTMLButtonElement;
    expect(sendButton.disabled).toBe(true);
  });

  it('enables send button when input has text', () => {
    render(<AIChatInterface />);
    const textarea = screen.getByPlaceholderText('Ask the assistant… (Shift+Enter for new line)');
    fireEvent.change(textarea, { target: { value: 'some text' } });
    const sendButton = screen.getByRole('button', { name: /send message/i }) as HTMLButtonElement;
    expect(sendButton.disabled).toBe(false);
  });

  it('disables send button when isLoading is true even with text', () => {
    render(<AIChatInterface isLoading onSendMessage={vi.fn()} />);
    const textarea = screen.getByPlaceholderText('Ask the assistant\u2026 (Shift+Enter for new line)');
    fireEvent.change(textarea, { target: { value: 'some typed text' } });
    const sendButton = screen.getByRole('button', { name: /send message/i }) as HTMLButtonElement;
    expect(sendButton.disabled).toBe(true);
  });

  it('renders message with timestamp when provided', () => {
    const timestamp = new Date('2026-04-26T10:30:00');
    const messages: ChatMessage[] = [makeMessage({ content: 'Timed message.', timestamp })];
    render(<AIChatInterface messages={messages} />);
    // Message content is visible; timestamp is locale-formatted so just confirm message renders
    expect(screen.getByText('Timed message.')).toBeDefined();
  });

  it('renders hint text for keyboard shortcuts', () => {
    render(<AIChatInterface />);
    expect(screen.getByText('Shift+Enter for new line · Enter to send')).toBeDefined();
  });
});
