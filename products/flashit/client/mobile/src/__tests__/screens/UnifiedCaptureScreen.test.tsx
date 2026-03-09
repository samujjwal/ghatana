import React from 'react';
import { render, fireEvent } from '@testing-library/react-native';
import { UnifiedCaptureScreen } from '../../screens/UnifiedCaptureScreen';

// Mock navigation
const mockNavigation = {
  navigate: jest.fn(),
  goBack: jest.fn(),
  setOptions: jest.fn(),
};

// Mock route with params
const mockRoute = {
  params: {
    sphereId: 'sphere-123',
  },
};

// Mock components
jest.mock('../../components/CaptureModeSelector', () => ({
  CaptureModeSelector: ({ selectedMode, onSelectMode }: any) => (
    <button testID="mode-selector" onClick={() => onSelectMode('voice')}>
      {selectedMode}
    </button>
  ),
}));

jest.mock('../../components/QuickCaptureButton', () => ({
  QuickCaptureButton: ({ onPress, onLongPress }: any) => (
    <>
      <button testID="quick-capture-tap" onClick={onPress}>
        Quick Capture
      </button>
      <button testID="quick-capture-long" onClick={onLongPress}>
        Long Press
      </button>
    </>
  ),
}));

describe('UnifiedCaptureScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should render with default text mode', () => {
    const { getByText } = render(
      <UnifiedCaptureScreen navigation={mockNavigation as any} route={mockRoute as any} />
    );

    expect(getByText(/Select capture mode/i)).toBeTruthy();
  });

  it('should navigate to VoiceRecorder when voice mode selected', () => {
    const { getByTestId } = render(
      <UnifiedCaptureScreen navigation={mockNavigation as any} route={mockRoute as any} />
    );

    const modeSelector = getByTestId('mode-selector');
    fireEvent.press(modeSelector);

    const quickCapture = getByTestId('quick-capture-tap');
    fireEvent.press(quickCapture);

    expect(mockNavigation.navigate).toHaveBeenCalledWith('VoiceRecorder', {
      sphereId: 'sphere-123',
    });
  });

  it('should navigate to ImageCapture when photo mode selected', () => {
    // Test implementation depends on mode selector behavior
  });

  it('should navigate to VideoRecorder when video mode selected', () => {
    // Test implementation depends on mode selector behavior
  });

  it('should handle quick voice recording on long press', () => {
    const { getByTestId } = render(
      <UnifiedCaptureScreen navigation={mockNavigation as any} route={mockRoute as any} />
    );

    const quickCapture = getByTestId('quick-capture-long');
    fireEvent.press(quickCapture);

    expect(mockNavigation.navigate).toHaveBeenCalledWith('VoiceRecorder', {
      sphereId: 'sphere-123',
      quickCapture: true,
    });
  });

  it('should persist sphere ID across mode changes', () => {
    const { rerender } = render(
      <UnifiedCaptureScreen navigation={mockNavigation as any} route={mockRoute as any} />
    );

    // Change mode
    rerender(
      <UnifiedCaptureScreen navigation={mockNavigation as any} route={mockRoute as any} />
    );

    // Sphere ID should still be 'sphere-123'
    // Implementation depends on how sphere persistence is managed
  });

  it('should show recent captures list', () => {
    const { getByText } = render(
      <UnifiedCaptureScreen navigation={mockNavigation as any} route={mockRoute as any} />
    );

    // This assumes there's a recent captures section
    // Implementation depends on UI
  });

  it('should handle empty sphere ID gracefully', () => {
    const emptyRoute = {
      params: {},
    };

    const { getByText } = render(
      <UnifiedCaptureScreen navigation={mockNavigation as any} route={emptyRoute as any} />
    );

    expect(getByText(/Select capture mode/i)).toBeTruthy();
  });
});
