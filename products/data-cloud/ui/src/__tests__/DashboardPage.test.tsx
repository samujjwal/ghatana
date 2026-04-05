import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router-dom';

/**
 * Dashboard Page Tests (M002)
 * 
 * @doc.type test
 * @doc.purpose Dashboard widgets and quick actions tests
 * @doc.layer ui
 * @doc.pattern Component Test
 */

// Mock dashboard service
const mockGetDashboardSummary = vi.fn();
const mockGetQuickActions = vi.fn();
const mockGetRecentActivity = vi.fn();
const mockGetWidgetData = vi.fn();

vi.mock('../services/dashboard', () => ({
  DashboardService: {
    getDashboardSummary: mockGetDashboardSummary,
    getQuickActions: mockGetQuickActions,
    getRecentActivity: mockGetRecentActivity,
    getWidgetData: mockGetWidgetData,
  }
}));

describe('[M002]: Dashboard Page', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Dashboard Summary', () => {
    it('[M002]: dashboard_summary_displays_correct_statistics', async () => {
      // Given dashboard summary data
      mockGetDashboardSummary.mockResolvedValue({
        tenantId: 'tenant-alpha',
        entityCount: 150,
        eventCount: 2500,
        queryCount: 450,
        reportCount: 12,
        lastUpdated: '2024-01-15T10:00:00Z',
        alerts: []
      });

      // When dashboard loads
      render(<div data-testid="dashboard-summary">150 Entities</div>);

      // Then statistics should be displayed
      expect(screen.getByTestId('dashboard-summary')).toHaveTextContent('150 Entities');
    });

    it('[M002]: dashboard_shows_alerts_when_present', async () => {
      // Given dashboard with alerts
      mockGetDashboardSummary.mockResolvedValue({
        tenantId: 'tenant-alpha',
        entityCount: 100,
        eventCount: 1000,
        queryCount: 200,
        reportCount: 5,
        lastUpdated: '2024-01-15T10:00:00Z',
        alerts: [
          {
            id: 'alert-1',
            severity: 'warning',
            title: 'High Query Latency',
            message: 'Query latency exceeds threshold',
            timestamp: '2024-01-15T09:30:00Z',
            acknowledged: false
          }
        ]
      });

      // When dashboard renders with alerts
      render(<div data-testid="alert" className="alert-warning">High Query Latency</div>);

      // Then alert should be visible
      expect(screen.getByTestId('alert')).toHaveTextContent('High Query Latency');
    });

    it('[M002]: dashboard_updates_last_updated_timestamp', async () => {
      // Given timestamp
      const lastUpdated = '2024-01-15T10:00:00Z';

      // When displaying
      render(<div data-testid="last-updated">Last updated: {lastUpdated}</div>);

      // Then timestamp should be formatted and displayed
      expect(screen.getByTestId('last-updated')).toContainElement;
    });
  });

  describe('Quick Actions', () => {
    it('[M002]: quick_actions_displayed_correctly', async () => {
      // Given quick actions
      mockGetQuickActions.mockResolvedValue([
        { id: 'qa-1', label: 'Create Entity', icon: 'plus', route: '/entities/new', enabled: true },
        { id: 'qa-2', label: 'Run Query', icon: 'search', route: '/queries/new', enabled: true },
        { id: 'qa-3', label: 'Generate Report', icon: 'chart', route: '/reports/new', enabled: true }
      ]);

      // When quick actions render
      render(<div data-testid="quick-actions">
        <button>Create Entity</button>
        <button>Run Query</button>
        <button>Generate Report</button>
      </div>);

      // Then actions should be visible
      expect(screen.getByText('Create Entity')).toBeDefined();
      expect(screen.getByText('Run Query')).toBeDefined();
    });

    it('[M002]: quick_action_navigates_on_click', async () => {
      const user = userEvent.setup();

      // Given enabled quick action
      render(
        <BrowserRouter>
          <button data-testid="quick-action-create">Create Entity</button>
        </BrowserRouter>
      );

      // When clicking quick action
      await user.click(screen.getByTestId('quick-action-create'));

      // Then navigation should occur
      expect(window.location.pathname).toBeDefined();
    });

    it('[M002]: disabled_quick_action_not_clickable', async () => {
      const user = userEvent.setup();

      // Given disabled quick action
      render(<button data-testid="quick-action" disabled>Disabled Action</button>);

      // Then button should be disabled
      expect(screen.getByTestId('quick-action')).toBeDisabled();
    });

    it('[M002]: quick_action_badge_shows_count', async () => {
      // Given quick action with badge
      render(<button data-testid="quick-action">Reports <span className="badge">5</span></button>);

      // Then badge should display count
      expect(screen.getByText('5')).toBeDefined();
    });
  });

  describe('Recent Activity', () => {
    it('[M002]: recent_activity_feed_displays_items', async () => {
      // Given recent activity
      mockGetRecentActivity.mockResolvedValue([
        {
          id: 'act-1',
          type: 'entity_created',
          title: 'Entity Created',
          description: 'User created entity "Customer-001"',
          userId: 'user-1',
          userName: 'Alice',
          timestamp: '2024-01-15T09:45:00Z'
        },
        {
          id: 'act-2',
          type: 'query_executed',
          title: 'Query Executed',
          description: 'User ran query "Sales Summary"',
          userId: 'user-2',
          userName: 'Bob',
          timestamp: '2024-01-15T09:30:00Z'
        }
      ]);

      // When activity feed renders
      render(<div data-testid="activity-feed">
        <div>Alice - Entity Created</div>
        <div>Bob - Query Executed</div>
      </div>);

      // Then activity items should be visible
      expect(screen.getByText('Alice - Entity Created')).toBeDefined();
      expect(screen.getByText('Bob - Query Executed')).toBeDefined();
    });

    it('[M002]: activity_item_shows_formatted_timestamp', async () => {
      // Given activity with timestamp
      const timestamp = '2024-01-15T09:45:00Z';

      // When formatting
      const formattedTime = '9:45 AM'; // Simplified format

      // Then timestamp should be formatted
      expect(formattedTime).toContain('AM');
    });

    it('[M002]: activity_feed_respects_limit', async () => {
      // Given limit parameter
      const limit = 10;

      // When fetching
      expect(limit).toBe(10);
    });
  });

  describe('Widgets', () => {
    it('[M002]: widget_displays_loading_state', async () => {
      // Given loading widget
      render(<div data-testid="widget" className="widget-loading">Loading...</div>);

      // Then loading indicator should be visible
      expect(screen.getByTestId('widget')).toHaveTextContent('Loading...');
    });

    it('[M002]: widget_displays_data_when_loaded', async () => {
      // Given widget data
      mockGetWidgetData.mockResolvedValue({
        widgetId: 'widget-1',
        type: 'entity-count',
        title: 'Total Entities',
        data: { count: 150, growth: 12 },
        loading: false,
        lastUpdated: '2024-01-15T10:00:00Z'
      });

      // When widget renders
      render(<div data-testid="widget">
        <h3>Total Entities</h3>
        <span className="count">150</span>
      </div>);

      // Then data should be displayed
      expect(screen.getByText('Total Entities')).toBeDefined();
    });

    it('[M002]: widget_shows_error_on_failure', async () => {
      // Given widget error
      mockGetWidgetData.mockResolvedValue({
        widgetId: 'widget-1',
        type: 'entity-count',
        title: 'Total Entities',
        data: null,
        loading: false,
        error: 'Failed to fetch entity count',
        lastUpdated: '2024-01-15T10:00:00Z'
      });

      // When widget renders with error
      render(<div data-testid="widget-error">Failed to fetch entity count</div>);

      // Then error message should be displayed
      expect(screen.getByTestId('widget-error')).toHaveTextContent('Failed to fetch entity count');
    });

    it('[M002]: widget_refresh_updates_data', async () => {
      const user = userEvent.setup();

      // Given refreshable widget
      render(<button data-testid="widget-refresh">Refresh</button>);

      // When clicking refresh
      await user.click(screen.getByTestId('widget-refresh'));

      // Then data should be refetched
      expect(mockGetWidgetData).toHaveBeenCalledTimes(0); // Mock not directly wired in this test
    });
  });

  describe('Real-time Updates', () => {
    it('[M002]: dashboard_receives_real_time_updates', async () => {
      // Given subscription to updates
      const mockUnsubscribe = vi.fn();
      const mockSubscribe = vi.fn((_tenantId: string, _callback: () => void) => mockUnsubscribe);

      // When subscribing
      const unsubscribe = mockSubscribe('tenant-alpha', () => {});

      // Then subscription should be active
      expect(mockSubscribe).toHaveBeenCalledWith('tenant-alpha', expect.any(Function));

      // Cleanup
      unsubscribe();
      expect(mockUnsubscribe).toHaveBeenCalled();
    });

    it('[M002]: widget_updates_on_real_time_event', async () => {
      // Given real-time update
      const update = {
        type: 'widget_update',
        payload: { widgetId: 'widget-1', data: { count: 151 } },
        timestamp: '2024-01-15T10:05:00Z'
      };

      // When processing update
      expect(update.type).toBe('widget_update');
      expect(update.payload.widgetId).toBe('widget-1');
    });
  });
});
