/**
 * VoiceInputButton tests
 *
 * Tests for the VoiceInputButton and VoiceInputIndicator components.
 * The useVoiceInput hook is fully mocked so tests are pure unit tests
 * with no Web Speech API dependency.
 */

import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { VoiceInputButton, VoiceInputIndicator } from '../VoiceInputButton';

// --------------------------------------------------------------------------
// Mock useVoiceInput so the component stays isolated.
// vi.hoisted ensures the fn is available when vi.mock is hoisted.
// --------------------------------------------------------------------------
const useVoiceInputMock = vi.hoisted(() => vi.fn());

vi.mock('../../../hooks/useVoiceInput', () => ({
  default: useVoiceInputMock,
  useVoiceInput: useVoiceInputMock,
}));

const defaultHookReturn = {
  isSupported: true,
  isListening: false,
  status: 'idle' as const,
  interimTranscript: '',
  toggleListening: vi.fn(),
  transcript: '',
  confidence: 0,
  startListening: vi.fn(),
  stopListening: vi.fn(),
  clearTranscript: vi.fn(),
};

// --------------------------------------------------------------------------
// Helpers
// --------------------------------------------------------------------------
function renderButton(props: React.ComponentProps<typeof VoiceInputButton> = {}) {
  return render(<VoiceInputButton {...props} />);
}

// --------------------------------------------------------------------------
// Tests
// --------------------------------------------------------------------------
describe('VoiceInputButton', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useVoiceInputMock.mockReturnValue({ ...defaultHookReturn, toggleListening: vi.fn() });
  });

  it('renders the microphone button when voice is supported', () => {
    renderButton();
    expect(screen.getByRole('button')).toBeInTheDocument();
  });

  it('returns null when voice input is not supported', () => {
    useVoiceInputMock.mockReturnValue({ ...defaultHookReturn, isSupported: false });
    const { container } = renderButton();
    expect(container.firstChild).toBeNull();
  });

  it('has accessible label for start voice input', () => {
    renderButton();
    expect(screen.getByRole('button', { name: /start voice input/i })).toBeInTheDocument();
  });

  it('has accessible label for stop voice input when listening', () => {
    useVoiceInputMock.mockReturnValue({ ...defaultHookReturn, isListening: true, status: 'listening' });
    renderButton();
    expect(screen.getByRole('button', { name: /stop voice input/i })).toBeInTheDocument();
  });

  it('calls toggleListening when button is clicked', () => {
    const toggleListening = vi.fn();
    useVoiceInputMock.mockReturnValue({ ...defaultHookReturn, toggleListening });
    renderButton();
    fireEvent.click(screen.getByRole('button'));
    expect(toggleListening).toHaveBeenCalledTimes(1);
  });

  it('button is disabled when disabled prop is true', () => {
    renderButton({ disabled: true });
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('button is disabled when status is error', () => {
    useVoiceInputMock.mockReturnValue({ ...defaultHookReturn, status: 'error' as 'idle' });
    renderButton();
    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('shows listening animation when isListening is true', () => {
    useVoiceInputMock.mockReturnValue({ ...defaultHookReturn, isListening: true, status: 'listening' });
    const { container } = renderButton();
    const pingSpan = container.querySelector('.animate-ping');
    expect(pingSpan).toBeInTheDocument();
  });

  it('does not show listening animation when idle', () => {
    const { container } = renderButton();
    expect(container.querySelector('.animate-ping')).toBeNull();
  });

  it('shows interim transcript tooltip when listening with interim text', () => {
    useVoiceInputMock.mockReturnValue({
      ...defaultHookReturn,
      isListening: true,
      status: 'listening',
      interimTranscript: 'hello world',
    });
    renderButton();
    // The transcript is rendered inside quotes: "hello world"
    expect(screen.getByText(/hello world/)).toBeInTheDocument();
  });

  it('does not show interim transcript tooltip when not listening', () => {
    useVoiceInputMock.mockReturnValue({ ...defaultHookReturn, interimTranscript: 'hello world' });
    renderButton();
    expect(screen.queryByText(/hello world/)).toBeNull();
  });

  it('applies small size classes', () => {
    const { container } = renderButton({ size: 'small' });
    expect(container.querySelector('.w-8.h-8')).toBeInTheDocument();
  });

  it('applies large size classes', () => {
    const { container } = renderButton({ size: 'large' });
    expect(container.querySelector('.w-12.h-12')).toBeInTheDocument();
  });

  it('applies medium size class by default', () => {
    const { container } = renderButton();
    expect(container.querySelector('.w-10.h-10')).toBeInTheDocument();
  });

  it('forwards custom className to wrapper', () => {
    const { container } = renderButton({ className: 'my-custom-class' });
    expect(container.firstChild).toHaveClass('my-custom-class');
  });
});

// --------------------------------------------------------------------------
// VoiceInputIndicator tests
// --------------------------------------------------------------------------
describe('VoiceInputIndicator', () => {
  it('returns null when not listening', () => {
    const { container } = render(<VoiceInputIndicator isListening={false} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders an indicator element when listening', () => {
    const { container } = render(<VoiceInputIndicator isListening={true} />);
    expect(container.firstChild).not.toBeNull();
  });

  it('applies custom className', () => {
    const { container } = render(
      <VoiceInputIndicator isListening={true} className="indicator-class" />,
    );
    // The top-level element should carry the custom class
    expect(container.firstChild).toHaveClass('indicator-class');
  });
});
