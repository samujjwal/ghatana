import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { VideoRecorderScreen } from '../../screens/VideoRecorderScreen';
import * as Camera from 'expo-camera';

// Mock navigation
const mockNavigation = {
  navigate: jest.fn(),
  goBack: jest.fn(),
  setOptions: jest.fn(),
};

// Mock Expo modules
jest.mock('expo-camera', () => ({
  Camera: {
    requestCameraPermissionsAsync: jest.fn(),
    requestMicrophonePermissionsAsync: jest.fn(),
  },
  CameraView: 'CameraView',
}));

jest.mock('../../components/VideoRecordingControls', () => ({
  VideoRecordingControls: 'VideoRecordingControls',
}));

jest.mock('../../components/VideoPreview', () => ({
  VideoPreview: 'VideoPreview',
}));

describe('VideoRecorderScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should render permission prompt initially', () => {
    const { getByText } = render(
      <VideoRecorderScreen navigation={mockNavigation as any} route={{} as any} />
    );

    expect(getByText(/Camera & Microphone Access Required/i)).toBeTruthy();
  });

  it('should request both camera and microphone permissions', async () => {
    (Camera.Camera.requestCameraPermissionsAsync as jest.Mock).mockResolvedValue({
      status: 'granted',
    });
    (Camera.Camera.requestMicrophonePermissionsAsync as jest.Mock).mockResolvedValue({
      status: 'granted',
    });

    const { getByText } = render(
      <VideoRecorderScreen navigation={mockNavigation as any} route={{} as any} />
    );

    const grantButton = getByText(/Grant Permissions/i);
    fireEvent.press(grantButton);

    await waitFor(() => {
      expect(Camera.Camera.requestCameraPermissionsAsync).toHaveBeenCalled();
      expect(Camera.Camera.requestMicrophonePermissionsAsync).toHaveBeenCalled();
    });
  });

  it('should show camera interface after permissions granted', async () => {
    (Camera.Camera.requestCameraPermissionsAsync as jest.Mock).mockResolvedValue({
      status: 'granted',
    });
    (Camera.Camera.requestMicrophonePermissionsAsync as jest.Mock).mockResolvedValue({
      status: 'granted',
    });

    const { getByText, queryByText } = render(
      <VideoRecorderScreen navigation={mockNavigation as any} route={{} as any} />
    );

    const grantButton = getByText(/Grant Permissions/i);
    fireEvent.press(grantButton);

    await waitFor(() => {
      expect(queryByText(/Camera & Microphone Access Required/i)).toBeNull();
    });
  });

  it('should handle camera permission denied', async () => {
    (Camera.Camera.requestCameraPermissionsAsync as jest.Mock).mockResolvedValue({
      status: 'denied',
    });
    (Camera.Camera.requestMicrophonePermissionsAsync as jest.Mock).mockResolvedValue({
      status: 'granted',
    });

    const { getByText } = render(
      <VideoRecorderScreen navigation={mockNavigation as any} route={{} as any} />
    );

    const grantButton = getByText(/Grant Permissions/i);
    fireEvent.press(grantButton);

    await waitFor(() => {
      expect(getByText(/Permission denied/i)).toBeTruthy();
    });
  });

  it('should handle microphone permission denied', async () => {
    (Camera.Camera.requestCameraPermissionsAsync as jest.Mock).mockResolvedValue({
      status: 'granted',
    });
    (Camera.Camera.requestMicrophonePermissionsAsync as jest.Mock).mockResolvedValue({
      status: 'denied',
    });

    const { getByText } = render(
      <VideoRecorderScreen navigation={mockNavigation as any} route={{} as any} />
    );

    const grantButton = getByText(/Grant Permissions/i);
    fireEvent.press(grantButton);

    await waitFor(() => {
      expect(getByText(/Permission denied/i)).toBeTruthy();
    });
  });

  it('should show recording timer when recording', () => {
    // This test would require simulating a recording state
    // Implementation depends on how recording state is managed
  });

  it('should enforce 2-minute recording limit', () => {
    // This test would require timer simulation
    // Implementation depends on recording timer logic
  });

  it('should show REC indicator during recording', () => {
    // This test would require recording state simulation
  });

  it('should navigate to Capture screen with video URI', async () => {
    // This test would require video save simulation
    // Implementation depends on save functionality
  });
});
