import { describe, it, expect, beforeEach, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { Analytics } from '../components/Analytics';
import { renderWithDashboardProviders } from './utils/renderWithProviders';
import { blockEventsAtom, usageEventsAtom } from '../stores/eventsStore';
import type { BlockEvent, UsageEvent } from '../services/websocket.service';

// NOTE: Do NOT mock Jotai - use renderWithDashboardProviders which properly wraps in JotaiProvider

describe('Analytics Component', () => {
  const now = new Date().toISOString();
  const usageEvents: UsageEvent[] = [
    {
      usageSession: {
        id: 'usage-1',
        device_id: 'device-1',
        item_name: 'Learning App',
        session_type: 'app',
        duration_seconds: 3_600,
        timestamp: now,
      },
      device: {
        id: 'device-1',
        name: 'School iPad',
        type: 'tablet',
      },
    },
  ];
  const blockEvents: BlockEvent[] = [
    {
      blockEvent: {
        id: 'block-1',
        device_id: 'device-1',
        blocked_item: 'Arcade App',
        event_type: 'app',
        reason: 'policy',
        timestamp: now,
      },
      device: {
        id: 'device-1',
        name: 'School iPad',
        type: 'tablet',
      },
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
  });

  function renderAnalyticsWithEvents(): void {
    renderWithDashboardProviders(<Analytics />, {
      withRouter: false,
      initializeStore: (store) => {
        store.set(usageEventsAtom, usageEvents);
        store.set(blockEventsAtom, blockEvents);
      },
    });
  }

  it('should render analytics title', () => {
    renderWithDashboardProviders(<Analytics />, { withRouter: false });
    expect(screen.getByText('Analytics & Insights')).toBeInTheDocument();
  });

  it('should display time range selector', () => {
    renderWithDashboardProviders(<Analytics />, { withRouter: false });
    const select = screen.getByRole('combobox');
    expect(select).toBeInTheDocument();
  });

  it('should display usage overview section', () => {
    renderAnalyticsWithEvents();
    expect(screen.getByText('Total Usage')).toBeInTheDocument();
  });

  it('should display three usage statistics cards', () => {
    renderAnalyticsWithEvents();
    expect(screen.getByText('Total Usage')).toBeInTheDocument();
    expect(screen.getByText('Active Devices')).toBeInTheDocument();
    expect(screen.getByText('Avg. Per Device')).toBeInTheDocument();
  });

  it('should display block activity section', () => {
    renderAnalyticsWithEvents();
    expect(screen.getByText('Total Blocks')).toBeInTheDocument();
  });

  it('should display two block statistics cards', () => {
    renderAnalyticsWithEvents();
    expect(screen.getByText('Total Blocks')).toBeInTheDocument();
    expect(screen.getByText('Unique Items Blocked')).toBeInTheDocument();
  });

  it('should display top apps section', () => {
    renderAnalyticsWithEvents();
    expect(screen.getByText('Top Apps by Usage')).toBeInTheDocument();
  });

  it('should display most blocked items section', () => {
    renderAnalyticsWithEvents();
    expect(screen.getByText('Most Blocked Items')).toBeInTheDocument();
  });

  it('should display block reasons section', () => {
    renderAnalyticsWithEvents();
    expect(screen.getByText('Block Reasons')).toBeInTheDocument();
  });

  it('should display summary insights section', () => {
    renderAnalyticsWithEvents();
    expect(screen.getByText('📊 Summary Insights')).toBeInTheDocument();
  });
});
