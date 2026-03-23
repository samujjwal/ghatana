import { render, screen } from '@testing-library/react';
import { StatsDashboard, type DashboardStatCardConfig, type BarChartConfig, type InsightItem } from '../StatsDashboard';

interface TestItem {
  id: string;
  value: number;
  category: string;
}

const mockItems: TestItem[] = [
  { id: '1', value: 100, category: 'A' },
  { id: '2', value: 200, category: 'B' },
  { id: '3', value: 150, category: 'A' },
];

describe('StatsDashboard', () => {
  describe('Basic Rendering', () => {
    it('renders with minimal props', () => {
      render(<StatsDashboard items={mockItems} />);
      expect(screen.getByText('Analytics Dashboard')).toBeInTheDocument();
    });

    it('renders custom title when provided', () => {
      render(<StatsDashboard items={mockItems} title="Custom Dashboard" />);
      expect(screen.getByText('Custom Dashboard')).toBeInTheDocument();
    });

    it('renders empty state when no items', () => {
      render(<StatsDashboard items={[]} emptyMessage="No data found" />);
      expect(screen.getByText('No data found')).toBeInTheDocument();
    });

    it('renders loading state', () => {
      render(<StatsDashboard items={[]} loading loadingMessage="Loading data..." />);
      expect(screen.getByText('Loading data...')).toBeInTheDocument();
    });
  });

  describe('Statistics Cards', () => {
    it('renders statistics cards with calculated values', () => {
      const statsCards: DashboardStatCardConfig<TestItem>[] = [
        { title: 'Total Items', calculate: (items) => items.length },
        { title: 'Total Value', calculate: (items) => items.reduce((sum, i) => sum + i.value, 0) },
        { title: 'Average', calculate: (items) => Math.round(items.reduce((sum, i) => sum + i.value, 0) / items.length) },
      ];

      render(<StatsDashboard items={mockItems} statsCards={statsCards} />);

      expect(screen.getByText('Total Items')).toBeInTheDocument();
      expect(screen.getByText('3')).toBeInTheDocument();
      expect(screen.getByText('Total Value')).toBeInTheDocument();
      expect(screen.getByText('450')).toBeInTheDocument();
      expect(screen.getByText('Average')).toBeInTheDocument();
      expect(screen.getByText('150')).toBeInTheDocument();
    });

    it('renders statistics cards with icons', () => {
      const statsCards: DashboardStatCardConfig<TestItem>[] = [
        { title: 'Count', calculate: (items) => items.length, icon: '📊' },
      ];

      render(<StatsDashboard items={mockItems} statsCards={statsCards} />);
      expect(screen.getByText('📊')).toBeInTheDocument();
    });

    it('renders statistics cards with subtitles', () => {
      const statsCards: DashboardStatCardConfig<TestItem>[] = [
        { title: 'Count', calculate: (items) => items.length, subtitle: 'Total records' },
      ];

      render(<StatsDashboard items={mockItems} statsCards={statsCards} />);
      expect(screen.getByText('Total records')).toBeInTheDocument();
    });
  });

  describe('Bar Charts', () => {
    it('renders bar chart sections', () => {
      const barCharts: BarChartConfig[] = [
        {
          title: 'Items by Category',
          items: [
            { label: 'Category A', value: 250 },
            { label: 'Category B', value: 200 },
          ],
        },
      ];

      render(<StatsDashboard items={mockItems} barCharts={barCharts} />);
      expect(screen.getByText('Items by Category')).toBeInTheDocument();
      expect(screen.getByText('Category A')).toBeInTheDocument();
      expect(screen.getByText('Category B')).toBeInTheDocument();
    });

    it('shows empty message when bar chart has no items', () => {
      const barCharts: BarChartConfig[] = [
        {
          title: 'Empty Chart',
          items: [],
          emptyMessage: 'No chart data',
        },
      ];

      render(<StatsDashboard items={mockItems} barCharts={barCharts} />);
      expect(screen.getByText('Empty Chart')).toBeInTheDocument();
      expect(screen.getByText('No chart data')).toBeInTheDocument();
    });

    it('renders bar chart with numbering', () => {
      const barCharts: BarChartConfig[] = [
        {
          title: 'Top Items',
          items: [
            { label: 'Item 1', value: 100 },
            { label: 'Item 2', value: 90 },
          ],
          showNumbering: true,
        },
      ];

      render(<StatsDashboard items={mockItems} barCharts={barCharts} />);
      expect(screen.getByText('1.')).toBeInTheDocument();
      expect(screen.getByText('2.')).toBeInTheDocument();
    });

    it('limits bar chart items with maxItems', () => {
      const barCharts: BarChartConfig[] = [
        {
          title: 'Top 2 Items',
          items: [
            { label: 'Item 1', value: 100 },
            { label: 'Item 2', value: 90 },
            { label: 'Item 3', value: 80 },
          ],
          maxItems: 2,
        },
      ];

      render(<StatsDashboard items={mockItems} barCharts={barCharts} />);
      expect(screen.getByText('Item 1')).toBeInTheDocument();
      expect(screen.getByText('Item 2')).toBeInTheDocument();
      expect(screen.queryByText('Item 3')).not.toBeInTheDocument();
    });
  });

  describe('Time Range Filtering', () => {
    it('renders time range selector', () => {
      const timeRangeConfig = {
        value: '7d',
        onChange: jest.fn(),
        options: [
          { label: 'Last 7 Days', value: '7d' },
          { label: 'Last 30 Days', value: '30d' },
        ],
      };

      render(<StatsDashboard items={mockItems} timeRangeConfig={timeRangeConfig} />);
      expect(screen.getByDisplayValue('Last 7 Days')).toBeInTheDocument();
    });
  });

  describe('Export Functionality', () => {
    it('renders export button', () => {
      const exportConfig = {
        buttonLabel: 'Export',
        options: [
          { label: 'Export CSV', onClick: jest.fn() },
          { label: 'Export PDF', onClick: jest.fn() },
        ],
      };

      render(<StatsDashboard items={mockItems} exportConfig={exportConfig} />);
      expect(screen.getByText(/Export/)).toBeInTheDocument();
    });
  });

  describe('Insights Section', () => {
    it('renders insights', () => {
      const insights: InsightItem[] = [
        { text: 'Total items increased by 20%', icon: '📈' },
        { text: 'Peak usage at 2 PM', icon: '⏰' },
      ];

      render(<StatsDashboard items={mockItems} insights={insights} />);
      expect(screen.getByText(/Total items increased by 20%/)).toBeInTheDocument();
      expect(screen.getByText(/Peak usage at 2 PM/)).toBeInTheDocument();
    });

    it('renders custom insights title', () => {
      const insights: InsightItem[] = [
        { text: 'Some insight' },
      ];

      render(<StatsDashboard items={mockItems} insights={insights} insightsTitle="🎯 Key Insights" />);
      expect(screen.getByText('🎯 Key Insights')).toBeInTheDocument();
    });
  });

  describe('Accessibility', () => {
    it('has proper heading hierarchy', () => {
      const statsCards: DashboardStatCardConfig<TestItem>[] = [
        { title: 'Count', calculate: (items) => items.length },
      ];

      render(<StatsDashboard items={mockItems} title="Dashboard" statsCards={statsCards} />);
      
      const heading = screen.getByRole('heading', { name: 'Dashboard' });
      expect(heading.tagName).toBe('H2');
    });
  });
});
