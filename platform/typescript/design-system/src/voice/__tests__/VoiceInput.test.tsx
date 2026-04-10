/**
 * Tests for VoiceInput component
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { VoiceInput, useBrowserSpeechRecognition } from '../VoiceInput';

// Minimal mock types for SpeechRecognition
interface MockSpeechRecognitionEvent {
  resultIndex: number;
  results: Array<{ isFinal: boolean; 0: { transcript: string } }>;
}

interface MockSpeechRecognitionErrorEvent {
  error: string;
}

// Mock SpeechRecognition API — must use a regular 'function', not an arrow function,
// so vitest can call it with `new`.
const mockRecognition = {
  start: vi.fn(),
  stop: vi.fn(),
  abort: vi.fn(),
  continuous: true,
  interimResults: true,
  lang: 'en-US',
  onresult: null as ((event: MockSpeechRecognitionEvent) => void) | null,
  onerror: null as ((event: MockSpeechRecognitionErrorEvent) => void) | null,
  onend: null as (() => void) | null,
};

const MockSpeechRecognitionCtor = vi.fn(function MockSpeechRecognitionCtor() {
  return mockRecognition;
});

Object.defineProperty(window, 'SpeechRecognition', {
  configurable: true,
  writable: true,
  value: MockSpeechRecognitionCtor,
});
Object.defineProperty(window, 'webkitSpeechRecognition', {
  configurable: true,
  writable: true,
  value: MockSpeechRecognitionCtor,
});

// Mock consent — path relative to THIS test file (src/voice/__tests__/)
vi.mock('../../privacy', () => ({
  useConsent: () => ({ consentGranted: true }),
}));

describe('useBrowserSpeechRecognition', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('initializes with correct state', () => {
    const TestComponent = (): React.ReactElement => {
      const { isListening, transcript, error, startListening, stopListening, isSupported } =
        useBrowserSpeechRecognition();

      return (
        <div>
          <span data-testid="is-supported">{isSupported ? 'supported' : 'not-supported'}</span>
          <span data-testid="is-listening">{isListening ? 'listening' : 'not-listening'}</span>
          <span data-testid="transcript">{transcript}</span>
          <span data-testid="error">{error ?? 'no-error'}</span>
          <button onClick={startListening}>Start</button>
          <button onClick={stopListening}>Stop</button>
        </div>
      );
    };

    render(<TestComponent />);

    expect(screen.getByTestId('is-supported')).toHaveTextContent('supported');
    expect(screen.getByTestId('is-listening')).toHaveTextContent('not-listening');
    expect(screen.getByTestId('transcript')).toHaveTextContent('');
    expect(screen.getByTestId('error')).toHaveTextContent('no-error');
  });

  it('starts listening when startListening is called', () => {
    const TestComponent = (): React.ReactElement => {
      const { startListening } = useBrowserSpeechRecognition();
      return <button onClick={startListening}>Start</button>;
    };

    render(<TestComponent />);
    fireEvent.click(screen.getByText('Start'));

    expect(mockRecognition.start).toHaveBeenCalled();
  });

  it('stops listening when stopListening is called', () => {
    const TestComponent = (): React.ReactElement => {
      const { startListening, stopListening } = useBrowserSpeechRecognition();
      return (
        <div>
          <button onClick={startListening}>Start</button>
          <button onClick={stopListening}>Stop</button>
        </div>
      );
    };

    render(<TestComponent />);
    // must start before stop so the guard `isListening` is true
    fireEvent.click(screen.getByText('Start'));
    fireEvent.click(screen.getByText('Stop'));

    expect(mockRecognition.stop).toHaveBeenCalled();
  });

  it('handles speech recognition results', async () => {
    const TestComponent = (): React.ReactElement => {
      const { transcript } = useBrowserSpeechRecognition();
      return <span data-testid="transcript">{transcript}</span>;
    };

    render(<TestComponent />);

    const mockEvent: MockSpeechRecognitionEvent = {
      resultIndex: 0,
      results: [{ isFinal: true, 0: { transcript: 'Hello world' } }],
    };

    await act(async () => {
      mockRecognition.onresult?.(mockEvent);
    });

    expect(screen.getByTestId('transcript')).toHaveTextContent('Hello world');
  });

  it('handles speech recognition errors', async () => {
    const TestComponent = (): React.ReactElement => {
      const { error } = useBrowserSpeechRecognition();
      return <span data-testid="error">{error ?? 'no-error'}</span>;
    };

    render(<TestComponent />);

    const mockError: MockSpeechRecognitionErrorEvent = { error: 'network-error' };
    await act(async () => {
      mockRecognition.onerror?.(mockError);
    });

    expect(screen.getByTestId('error')).toHaveTextContent('network-error');
  });
});

describe('VoiceInput component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders voice input button', () => {
    const onTranscript = vi.fn();
    render(<VoiceInput onTranscript={onTranscript} />);
    expect(screen.getByRole('button')).toBeInTheDocument();
  });

  it('calls onTranscript when speech is recognized', async () => {
    const onTranscript = vi.fn();
    render(<VoiceInput onTranscript={onTranscript} />);

    const mockEvent: MockSpeechRecognitionEvent = {
      resultIndex: 0,
      results: [{ isFinal: true, 0: { transcript: 'Test transcript' } }],
    };

    await act(async () => {
      mockRecognition.onresult?.(mockEvent);
    });

    await waitFor(() => {
      expect(onTranscript).toHaveBeenCalledWith('Test transcript');
    });
  });

  it('shows listening state when active', () => {
    const onTranscript = vi.fn();
    render(<VoiceInput onTranscript={onTranscript} />);

    fireEvent.click(screen.getByRole('button'));

    expect(screen.getByText('Listening...')).toBeInTheDocument();
  });

  it('shows placeholder when not listening', () => {
    const onTranscript = vi.fn();
    render(<VoiceInput onTranscript={onTranscript} placeholder="Speak now" />);

    expect(screen.getByText('Speak now')).toBeInTheDocument();
  });

  it('is disabled when disabled prop is true', () => {
    const onTranscript = vi.fn();
    render(<VoiceInput onTranscript={onTranscript} disabled={true} />);

    expect(screen.getByRole('button')).toBeDisabled();
  });

  it('applies custom className', () => {
    const onTranscript = vi.fn();
    render(<VoiceInput onTranscript={onTranscript} className="custom-class" />);

    expect(screen.getByRole('button').parentElement).toHaveClass('custom-class');
  });

  it('applies custom buttonClassName', () => {
    const onTranscript = vi.fn();
    render(<VoiceInput onTranscript={onTranscript} buttonClassName="custom-button" />);

    expect(screen.getByRole('button')).toHaveClass('custom-button');
  });
});
