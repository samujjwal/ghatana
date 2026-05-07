/**
 * Tests for VoiceInput component
 */

import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { VoiceInput, VoiceTextarea } from '../VoiceInput';
import { createAepTestWrapper } from '@/__tests__/test-utils/wrapper';

const wrapper = createAepTestWrapper();

type MockRecognitionInstance = {
  continuous: boolean;
  interimResults: boolean;
  lang: string;
  start: ReturnType<typeof vi.fn>;
  stop: ReturnType<typeof vi.fn>;
  onstart: (() => void) | null;
  onresult: ((event: unknown) => void) | null;
  onerror: ((event: unknown) => void) | null;
  onend: (() => void) | null;
};

let recognitionFactory: (() => MockRecognitionInstance) | null = null;

function MockSpeechRecognition(this: unknown): MockRecognitionInstance {
  if (!recognitionFactory) {
    throw new Error('SpeechRecognition factory not configured');
  }

  return recognitionFactory();
}

describe('VoiceInput', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    recognitionFactory = null;
    (window as unknown as { SpeechRecognition: typeof MockSpeechRecognition }).SpeechRecognition = MockSpeechRecognition;
    (window as unknown as { webkitSpeechRecognition: typeof MockSpeechRecognition }).webkitSpeechRecognition = MockSpeechRecognition;
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('renders input without voice when consent not granted', () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: false,
      timestamp: new Date().toISOString(),
    }));

    render(
      <VoiceInput
        value="test"
        onChange={() => {}}
        placeholder="Enter text"
      />,
      { wrapper }
    );

    const input = screen.getByPlaceholderText('Enter text');
    expect(input).toBeInTheDocument();
    expect(input).toHaveValue('test');
  });

  it('renders input with voice button when consent granted', () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    render(
      <VoiceInput
        value="test"
        onChange={() => {}}
        placeholder="Enter text"
      />,
      { wrapper }
    );

    const input = screen.getByPlaceholderText('Enter text');
    expect(input).toBeInTheDocument();
    
    // Voice button should be present
    const voiceButton = screen.getByLabelText(/Start voice input/i);
    expect(voiceButton).toBeInTheDocument();
  });

  it('starts voice recognition when button clicked', async () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    let recognitionInstance: MockRecognitionInstance | null = null;
    recognitionFactory = () => {
      recognitionInstance = {
        continuous: false,
        interimResults: true,
        lang: 'en-US',
        start: vi.fn(),
        stop: vi.fn(),
        onstart: null,
        onresult: null,
        onerror: null,
        onend: null,
      };
      return recognitionInstance;
    };

    const onChange = vi.fn();
    render(
      <VoiceInput
        value=""
        onChange={onChange}
        placeholder="Enter text"
      />,
      { wrapper }
    );

    const voiceButton = screen.getByLabelText(/Start voice input/i);
    fireEvent.click(voiceButton);

    await waitFor(() => {
      expect(recognitionInstance!.start).toHaveBeenCalled();
    });
  });

  it('updates value when transcript received', async () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    let recognitionInstance: MockRecognitionInstance | null = null;
    recognitionFactory = () => {
      recognitionInstance = {
        continuous: false,
        interimResults: true,
        lang: 'en-US',
        start: vi.fn(function(this: any) {
          // Simulate onstart
          if (this.onstart) this.onstart();
        }),
        stop: vi.fn(),
        onstart: null,
        onresult: null,
        onerror: null,
        onend: null,
      };
      return recognitionInstance;
    };

    const onChange = vi.fn();
    render(
      <VoiceInput
        value=""
        onChange={onChange}
        placeholder="Enter text"
      />,
      { wrapper }
    );

    const voiceButton = screen.getByLabelText(/Start voice input/i);
    fireEvent.click(voiceButton);

    await waitFor(() => {
      expect(recognitionInstance!.start).toHaveBeenCalled();
    });

    // Simulate transcript result
    if (recognitionInstance!.onresult) {
      recognitionInstance!.onresult({
        resultIndex: 0,
        results: [
          {
            isFinal: true,
            0: { transcript: 'hello world', confidence: 0.9 },
            length: 1,
            item: (index: number) => ({ transcript: 'hello world', confidence: 0.9 }),
          },
        ],
      } as any);
    }

    await waitFor(() => {
      expect(onChange).toHaveBeenCalledWith('hello world');
    });
  });

  it('shows listening indicator when recognition active', async () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    let recognitionInstance: MockRecognitionInstance | null = null;
    recognitionFactory = () => {
      recognitionInstance = {
        continuous: false,
        interimResults: true,
        lang: 'en-US',
        start: vi.fn(function(this: any) {
          if (this.onstart) this.onstart();
        }),
        stop: vi.fn(),
        onstart: null,
        onresult: null,
        onerror: null,
        onend: null,
      };
      return recognitionInstance;
    };

    render(
      <VoiceInput
        value=""
        onChange={() => {}}
        placeholder="Enter text"
      />,
      { wrapper }
    );

    const voiceButton = screen.getByLabelText(/Start voice input/i);
    fireEvent.click(voiceButton);

    await waitFor(() => {
      expect(screen.getByText('Listening...')).toBeInTheDocument();
    });
  });

  it('stops voice recognition when button clicked again', async () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    let recognitionInstance: MockRecognitionInstance | null = null;
    recognitionFactory = () => {
      recognitionInstance = {
        continuous: false,
        interimResults: true,
        lang: 'en-US',
        start: vi.fn(function(this: any) {
          if (this.onstart) this.onstart();
        }),
        stop: vi.fn(),
        onstart: null,
        onresult: null,
        onerror: null,
        onend: null,
      };
      return recognitionInstance;
    };

    render(
      <VoiceInput
        value=""
        onChange={() => {}}
        placeholder="Enter text"
      />,
      { wrapper }
    );

    const voiceButton = screen.getByLabelText(/Start voice input/i);
    fireEvent.click(voiceButton);

    await waitFor(() => {
      expect(recognitionInstance!.start).toHaveBeenCalled();
    });

    const stopButton = screen.getByLabelText(/Stop voice input/i);
    fireEvent.click(stopButton);

    await waitFor(() => {
      expect(recognitionInstance!.stop).toHaveBeenCalled();
    });
  });

  it('is disabled when disabled prop is true', () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    render(
      <VoiceInput
        value="test"
        onChange={() => {}}
        placeholder="Enter text"
        disabled
      />,
      { wrapper }
    );

    const input = screen.getByPlaceholderText('Enter text');
    expect(input).toBeDisabled();

    const voiceButton = screen.getByLabelText(/Start voice input/i);
    expect(voiceButton).toBeDisabled();
  });

  it('calls onListeningStart callback when recognition starts', async () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    let recognitionInstance: MockRecognitionInstance | null = null;
    recognitionFactory = () => {
      recognitionInstance = {
        continuous: false,
        interimResults: true,
        lang: 'en-US',
        start: vi.fn(function(this: any) {
          if (this.onstart) this.onstart();
        }),
        stop: vi.fn(),
        onstart: null,
        onresult: null,
        onerror: null,
        onend: null,
      };
      return recognitionInstance;
    };

    const onListeningStart = vi.fn();
    render(
      <VoiceInput
        value=""
        onChange={() => {}}
        placeholder="Enter text"
        onListeningStart={onListeningStart}
      />,
      { wrapper }
    );

    const voiceButton = screen.getByLabelText(/Start voice input/i);
    fireEvent.click(voiceButton);

    await waitFor(() => {
      expect(onListeningStart).toHaveBeenCalled();
    });
  });
});

describe('VoiceTextarea', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('renders textarea with voice support', () => {
    localStorage.setItem('consent_voice_processing', JSON.stringify({
      id: 'test-id',
      userId: 'current',
      purpose: 'voice_processing',
      granted: true,
      timestamp: new Date().toISOString(),
      expiresAt: new Date(Date.now() + 86400000).toISOString(),
    }));

    render(
      <VoiceTextarea
        value="test value"
        onChange={() => {}}
        placeholder="Enter text"
        rows={5}
      />,
      { wrapper }
    );

    const textarea = screen.getByPlaceholderText('Enter text');
    expect(textarea).toBeInTheDocument();
    expect(textarea).toHaveValue('test value');
    expect(textarea).toHaveAttribute('rows', '5');
  });
});
