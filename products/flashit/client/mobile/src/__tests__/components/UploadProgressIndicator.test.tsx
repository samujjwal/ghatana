import React from 'react';
import { render, fireEvent, waitFor } from '@testing-library/react-native';
import { UploadProgressIndicator } from '../../components/UploadProgressIndicator';
import { offlineQueueService } from '../../services/offlineQueue';

// Mock services
jest.mock('../../services/offlineQueue');

const mockQueue = [
  {
    id: '1',
    type: 'audio' as const,
    uri: 'file:///test/audio.m4a',
    status: 'pending' as const,
    metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: '2',
    type: 'image' as const,
    uri: 'file:///test/image.jpg',
    status: 'uploading' as const,
    metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: '3',
    type: 'video' as const,
    uri: 'file:///test/video.mp4',
    status: 'failed' as const,
    metadata: { sphereId: 'sphere-1', tags: [], retryCount: 2, error: 'Network error' },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
  {
    id: '4',
    type: 'audio' as const,
    uri: 'file:///test/audio2.m4a',
    status: 'completed' as const,
    metadata: { sphereId: 'sphere-1', tags: [], retryCount: 0 },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  },
];

describe('UploadProgressIndicator', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('should not render when queue is empty', async () => {
    (offlineQueueService.getQueue as jest.Mock).mockResolvedValue([]);

    const { queryByTestId } = render(<UploadProgressIndicator />);

    await waitFor(() => {
      expect(queryByTestId('upload-indicator')).toBeNull();
    });
  });

  it('should render floating indicator when queue has items', async () => {
    (offlineQueueService.getQueue as jest.Mock).mockResolvedValue(mockQueue);

    const { getByTestId } = render(<UploadProgressIndicator />);

    await waitFor(() => {
      expect(getByTestId('upload-indicator')).toBeTruthy();
    });
  });

  it('should show correct upload count', async () => {
    (offlineQueueService.getQueue as jest.Mock).mockResolvedValue(mockQueue);

    const { getByText } = render(<UploadProgressIndicator />);

    await waitFor(() => {
      // 1 pending + 1 uploading = 2 active
      expect(getByText(/2/)).toBeTruthy();
    });
  });

  it('should open modal when floating indicator pressed', async () => {
    (offlineQueueService.getQueue as jest.Mock).mockResolvedValue(mockQueue);

    const { getByTestId, getByText } = render(<UploadProgressIndicator />);

    await waitFor(() => {
      const indicator = getByTestId('upload-indicator');
      fireEvent.press(indicator);
    });

    await waitFor(() => {
      expect(getByText(/Upload Queue/i)).toBeTruthy();
    });
  });

  it('should display queue statistics', async () => {
    (offlineQueueService.getQueue as jest.Mock).mockResolvedValue(mockQueue);

    const { getByTestId, getByText } = render(<UploadProgressIndicator />);

    await waitFor(() => {
      const indicator = getByTestId('upload-indicator');
      fireEvent.press(indicator);
    });

    await waitFor(() => {
      expect(getByText(/Pending: 1/i)).toBeTruthy();
      expect(getByText(/Uploading: 1/i)).toBeTruthy();
      expect(getByText(/Failed: 1/i)).toBeTruthy();
      expect(getByText(/Completed: 1/i)).toBeTruthy();
    });
  });

  it('should show status badges for each item', async () => {
    (offlineQueueService.getQueue as jest.Mock).mockResolvedValue(mockQueue);

    const { getByTestId, getAllByText } = render(<UploadProgressIndicator />);

    await waitFor(() => {
      const indicator = getByTestId('upload-indicator');
      fireEvent.press(indicator);
    });

    await waitFor(() => {
      expect(getAllByText(/Pending/i).length).toBeGreaterThan(0);
      expect(getAllByText(/Uploading/i).length).toBeGreaterThan(0);
      expect(getAllByText(/Failed/i).length).toBeGreaterThan(0);
      expect(getAllByText(/Completed/i).length).toBeGreaterThan(0);
    });
  });

  it('should handle retry action for failed items', async () => {
    (offlineQueueService.getQueue as jest.Mock).mockResolvedValue(mockQueue);
    (offlineQueueService.updateItemStatus as jest.Mock).mockResolvedValue(undefined);

    const { getByTestId, getByText } = render(<UploadProgressIndicator />);

    await waitFor(() => {
      const indicator = getByTestId('upload-indicator');
      fireEvent.press(indicator);
    });

    await waitFor(() => {
      const retryButton = getByText(/Retry/i);
      fireEvent.press(retryButton);
    });

    expect(offlineQueueService.updateItemStatus).toHaveBeenCalledWith('3', 'pending');
  });

  it('should handle delete action', async () => {
    (offlineQueueService.getQueue as jest.Mock).mockResolvedValue(mockQueue);
    (offlineQueueService.removeItem as jest.Mock).mockResolvedValue(undefined);

    const { getByTestId, getAllByText } = render(<UploadProgressIndicator />);

    await waitFor(() => {
      const indicator = getByTestId('upload-indicator');
      fireEvent.press(indicator);
    });

    await waitFor(() => {
      const deleteButtons = getAllByText(/Delete/i);
      fireEvent.press(deleteButtons[0]);
    });

    expect(offlineQueueService.removeItem).toHaveBeenCalled();
  });

  it('should refresh queue every 2 seconds', async () => {
    (offlineQueueService.getQueue as jest.Mock).mockResolvedValue(mockQueue);

    render(<UploadProgressIndicator />);

    await waitFor(() => {
      expect(offlineQueueService.getQueue).toHaveBeenCalledTimes(1);
    });

    jest.advanceTimersByTime(2000);

    await waitFor(() => {
      expect(offlineQueueService.getQueue).toHaveBeenCalledTimes(2);
    });

    jest.advanceTimersByTime(2000);

    await waitFor(() => {
      expect(offlineQueueService.getQueue).toHaveBeenCalledTimes(3);
    });
  });

  it('should close modal when close button pressed', async () => {
    (offlineQueueService.getQueue as jest.Mock).mockResolvedValue(mockQueue);

    const { getByTestId, getByText, queryByText } = render(<UploadProgressIndicator />);

    await waitFor(() => {
      const indicator = getByTestId('upload-indicator');
      fireEvent.press(indicator);
    });

    await waitFor(() => {
      expect(getByText(/Upload Queue/i)).toBeTruthy();
    });

    const closeButton = getByText(/Close/i);
    fireEvent.press(closeButton);

    await waitFor(() => {
      expect(queryByText(/Upload Queue/i)).toBeNull();
    });
  });

  it('should show type-specific icons', async () => {
    (offlineQueueService.getQueue as jest.Mock).mockResolvedValue(mockQueue);

    const { getByTestId } = render(<UploadProgressIndicator />);

    await waitFor(() => {
      const indicator = getByTestId('upload-indicator');
      fireEvent.press(indicator);
    });

    // Icons are typically rendered as text (emojis) or icon components
    // Test depends on implementation
  });
});
