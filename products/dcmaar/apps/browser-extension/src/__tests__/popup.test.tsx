import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@ghatana/dcmaar-browser-extension-core', () => import('../test/mocks/browserCoreMock'));

vi.mock('webextension-polyfill', () => {
  const api = {
    tabs: {
      create: vi.fn(),
      query: vi.fn().mockResolvedValue([{ url: 'https://example.com' }]),
    },
    runtime: {
      getURL: vi.fn(() => 'chrome-extension://dashboard/index.html'),
      sendMessage: vi.fn(),
    },
  };
  return {
    default: api,
    ...api,
  };
});

import browser from 'webextension-polyfill';
import { BrowserMessageRouter } from '@ghatana/dcmaar-browser-extension-core';
import { Popup } from '../popup/Popup';

describe('Popup', () => {
  const summary = {
    totalUsageRecords: 4,
    totalEvents: 2,
    webUsage: {
      last24h: 1,
      last7d: 4,
      allTime: 4,
    },
    timeSpent: {
      last24h: 1000,
      last7d: 2000,
      allTime: 2000,
    },
    topDomains: [],
    state: {
      metricsCollecting: true,
      eventsCapturing: true,
    },
  };

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(BrowserMessageRouter.prototype, 'sendToBackground').mockImplementation(
      async (message: { type: string }) => {
        if (message.type === 'GET_ANALYTICS') {
          return {
            success: true,
            data: summary,
          };
        }
        if (message.type === 'EVALUATE_POLICY') {
          return {
            success: true,
            data: {
              decision: 'allow',
            },
          };
        }
        return { success: false, error: 'Unknown message type' };
      },
    );
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders analytics summary from background', async () => {
    render(<Popup />);

    await waitFor(() => expect(screen.getByText('Guardian')).toBeInTheDocument());
    expect(screen.getByText('Monitoring Active')).toBeInTheDocument();
    expect(screen.getByText('4 tracked')).toBeInTheDocument();
    expect(screen.getByText('View Detailed Report')).toBeInTheDocument();
  });

  it('opens dashboard tab when button clicked', async () => {
    const closeSpy = vi.spyOn(window, 'close').mockImplementation(() => undefined);

    render(<Popup />);
    await waitFor(() => expect(screen.getByText('View Detailed Report')).toBeInTheDocument());
    fireEvent.click(screen.getByText('View Detailed Report'));

    expect(closeSpy).toHaveBeenCalled();
    expect(browser.tabs.create).toHaveBeenCalledWith({ url: 'chrome-extension://dashboard/index.html' });
  });
});
