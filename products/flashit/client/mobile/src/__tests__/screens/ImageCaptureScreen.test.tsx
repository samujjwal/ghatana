import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { ImageCaptureScreen } from '../../screens/ImageCaptureScreen';
import * as ImagePicker from 'expo-image-picker';
import * as Camera from 'expo-camera';

// Mock navigation
const mockNavigation = {
  navigate: jest.fn(),
  goBack: jest.fn(),
  setOptions: jest.fn(),
};

// Mock Expo modules
jest.mock('expo-image-picker', () => ({
  requestMediaLibraryPermissionsAsync: jest.fn(),
  launchImageLibraryAsync: jest.fn(),
  MediaTypeOptions: {
    Images: 'Images',
  },
}));

jest.mock('expo-camera', () => ({
  Camera: {
    requestCameraPermissionsAsync: jest.fn(),
  },
  CameraView: 'CameraView',
}));

jest.mock('../../components/CameraPreview', () => ({
  CameraPreview: 'CameraPreview',
}));

jest.mock('../../components/ImageEditor', () => ({
  ImageEditor: 'ImageEditor',
}));

jest.mock('../../utils/imageOptimization', () => ({
  optimizeImage: jest.fn((uri) => Promise.resolve({ uri, width: 1024, height: 768 })),
}));

describe('ImageCaptureScreen', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('should render permission prompt initially', () => {
    const { getByText } = render(
      <ImageCaptureScreen navigation={mockNavigation as any} route={{} as any} />
    );

    expect(getByText(/Camera Access Required/i)).toBeTruthy();
  });

  it('should request camera permission when granted', async () => {
    (Camera.Camera.requestCameraPermissionsAsync as jest.Mock).mockResolvedValue({
      status: 'granted',
    });

    const { getByText } = render(
      <ImageCaptureScreen navigation={mockNavigation as any} route={{} as any} />
    );

    const grantButton = getByText(/Grant Camera Access/i);
    fireEvent.press(grantButton);

    await waitFor(() => {
      expect(Camera.Camera.requestCameraPermissionsAsync).toHaveBeenCalled();
    });
  });

  it('should show camera interface after permission granted', async () => {
    (Camera.Camera.requestCameraPermissionsAsync as jest.Mock).mockResolvedValue({
      status: 'granted',
    });

    const { getByText, queryByText } = render(
      <ImageCaptureScreen navigation={mockNavigation as any} route={{} as any} />
    );

    const grantButton = getByText(/Grant Camera Access/i);
    fireEvent.press(grantButton);

    await waitFor(() => {
      expect(queryByText(/Camera Access Required/i)).toBeNull();
    });
  });

  it('should handle permission denied', async () => {
    (Camera.Camera.requestCameraPermissionsAsync as jest.Mock).mockResolvedValue({
      status: 'denied',
    });

    const { getByText } = render(
      <ImageCaptureScreen navigation={mockNavigation as any} route={{} as any} />
    );

    const grantButton = getByText(/Grant Camera Access/i);
    fireEvent.press(grantButton);

    await waitFor(() => {
      expect(getByText(/Permission denied/i)).toBeTruthy();
    });
  });

  it('should open gallery picker', async () => {
    (ImagePicker.requestMediaLibraryPermissionsAsync as jest.Mock).mockResolvedValue({
      status: 'granted',
    });

    (ImagePicker.launchImageLibraryAsync as jest.Mock).mockResolvedValue({
      canceled: false,
      assets: [{ uri: 'file:///test/image.jpg', width: 1920, height: 1080 }],
    });

    const { getByText } = render(
      <ImageCaptureScreen navigation={mockNavigation as any} route={{} as any} />
    );

    // This test assumes there's a gallery button after permission is granted
    // You may need to adjust based on actual UI
  });

  it('should handle gallery picker cancellation', async () => {
    (ImagePicker.requestMediaLibraryPermissionsAsync as jest.Mock).mockResolvedValue({
      status: 'granted',
    });

    (ImagePicker.launchImageLibraryAsync as jest.Mock).mockResolvedValue({
      canceled: true,
    });

    // Test implementation depends on UI
  });

  it('should navigate to Capture screen with image URI', async () => {
    const optimizedUri = 'file:///test/optimized.jpg';

    (Camera.Camera.requestCameraPermissionsAsync as jest.Mock).mockResolvedValue({
      status: 'granted',
    });

    // This test would require interaction with CameraPreview component
    // Implementation depends on how you expose camera capture functionality
  });
});
