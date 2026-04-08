/**
 * Tests for VoiceInput component
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { VoiceInput, useBrowserSpeechRecognition } from '../VoiceInput';

// Mock SpeechRecognition API
const mockRecognition = {
  start: vi.fn(),
  stop: vi.fn(),
  abort: vi.fn(),
  continuous: true,
  interimResults: true,
  lang: 'en-US',
  onresult: null as any,
  onerror: null as any,
  onend: null as any,
};

global.SpeechRecognition = vi.fn(() => mockRecognition) as any;
global.webkitSpeechRecognition = global.SpeechRecognition;

// Mock consent
vi.mock('@ghatana/privacy-ui', () => ({
  useConsent: () => ({ consentGranted: true }),
}));

describe('useBrowserSpeechRecognition', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('initializes with correct state', () => {
    const TestComponent = () => {
      const { isListening, transcript, error, startListening, stopListening, isSupported } = useBrowserSpeechRecognition();
      
      return (
        <div>
          <span data-testid="is-supported">{isSupported ? 'supported' : 'not-supported'}</span>
          <span data-testid="is-listening">{isListening ? 'listening' : 'not-listening'}</span>
          <span data-testid="transcript">{transcript}</span>
          <span data-testid="error">{error || 'no-error'}</span>
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
    const TestComponent = () => {
      const { startListening } = useBrowserSpeechRecognition();
      return <button onClick={startListening}>Start</button>;
    };

    render(<TestComponent />);
    
    fireEvent.click(screen.getByText('Start'));
    
    expect(mockRecognition.start).toHaveBeenCalled();
  });

  it('stops listening when stopListening is called', () => {
    const TestComponent = () => {
      const { stopListening } = useBrowserSpeechRecognition();
      return <button onClick={stopListening}>Stop</button>;
    };

    render(<TestComponent />);
    
    fireEvent.click(screen.getByText('Stop'));
    
    expect(mockRecognition.stop).toHaveBeenCalled();
  });

  it('handles speech recognition results', () => {
    const TestComponent = () => {
      const { transcript } = useBrowserSpeechRecognition();
      return <span data-testid="transcript">{transcript}</span>;
    };

    render(<TestComponent />);

    // Simulate speech recognition result
    const mockEvent = {
      resultIndex: 0,
      results: [
        {
          isFinal: true,
          0: {
            transcript: 'Hello world',
          },
        },
      ],
    };

    if (mockRecognition.onresult) {
      mockRecognition.onresult(mockEvent);
    }

    expect(screen.getByTestId('transcript')).toHaveTextContent('Hello world');
  });

  it('handles speech recognition errors', () => {
    const TestComponent = () => {
      const { error } = useBrowserSpeechRecognition();
      return <span data-testid="error">{error || 'no-error'}</span>;
    };

    render(<TestComponent />);

    // Simulate speech recognition error
    const mockError = { error: 'network-error' };
    
    if (mockRecognition.onerror) {
      mockRecognition.onerror(mockError);
    }

    expect(screen.getByTestId('error')).toHaveTextContent('network-error');
  });
});

describe('VoiceInput component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders voice input button', () => {
    const onTranscript = vi.fn();
    
    render(
      <VoiceInput onTranscript={onTranscript} />
    );

    expect(screen.getByRole('button')).toBeInTheDocument();
  });

  it('calls onTranscript when speech is recognized', () => {
    const onTranscript = vi.fn();
    
    const TestComponent = () => {
      return <VoiceInput onTranscript={onTranscript} />;
    };

    render(<TestComponent />);

    // Simulate speech recognition result
    const mockEvent = {
      resultIndex: 0,
      results: [
        {
          isFinal: true,
          0: {
            transcript: 'Test transcript',
          },
        },
      ],
    };

    if (mockRecognition.onresult) {
      mockRecognition.onresult(mockEvent);
    }

    expect(onTranscript).toHaveBeenCalledWith('Test transcript');
  });

  it('shows listening state when active', () => {
    const onTranscript = vi.fn();
    
    const TestComponent = () => {
      return <VoiceInput onTranscript={onTranscript} />;
    };

    render(<TestComponent />);

    // Start listening
    fireEvent.click(screen.getByRole('button'));

    expect(screen.getByText('Listening...')).toBeInTheDocument();
  });

  it('shows placeholder when not listening', () => {
    const onTranscript = vi.fn();
    
    render(
      <VoiceInput onTranscript={onTranscript} placeholder="Speak now" />
    );

    expect(screen.getByText('Speak now')).toBeInTheDocument();
  });

  it('is disabled when disabled prop is true', () => {
    const onTranscript = vi.fn();
    
    render(
      <VoiceInput onTranscript={onTranscript} disabled={true} />
    );

    const button = screen.getByRole('button');
    expect(button).toBeDisabled();
  });

  it('applies custom className', () => {
    const onTranscript = vi.fn();
    
    render(
      <VoiceInput onTranscript={onTranscript} className="custom-class" />
    );

    expect(screen.getByRole('button').parentElement).toHaveClass('custom-class');
  });

  it('applies custom buttonClassName', () => {
    const onTranscript = vi.fn();
    
    render(
      <VoiceInput onTranscript={onTranscript} buttonClassName="custom-button" />
    );

    expect(screen.getByRole('button')).toHaveClass('custom-button');
  });
});
