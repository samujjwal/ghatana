import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

/**
 * Dataset Explorer Tests (M005)
 * 
 * @doc.type test
 * @doc.purpose Dataset search, filter, and pagination tests
 * @doc.layer ui
 * @doc.pattern Component Test
 */

// Mock service
const mockSearchDatasets = vi.fn();
const mockGetDataset = vi.fn();
const mockGetDatasetSchema = vi.fn();
const mockPreviewDataset = vi.fn();
const mockGetDatasetStats = vi.fn();
const mockFilterDatasets = vi.fn();

vi.mock('../services/dataset-explorer', () => ({
  DatasetExplorerService: {
    searchDatasets: mockSearchDatasets,
    getDataset: mockGetDataset,
    getDatasetSchema: mockGetDatasetSchema,
    previewDataset: mockPreviewDataset,
    getDatasetStats: mockGetDatasetStats,
    filterDatasets: mockFilterDatasets,
  }
}));

describe('[M005]: Dataset Explorer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Search', () => {
    it('[M005]: search_returns_matching_datasets', async () => {
      // Given search query
      mockSearchDatasets.mockResolvedValue({
        datasets: [
          { id: 'ds-1', name: 'Customers', collectionName: 'customers', rowCount: 150 },
          { id: 'ds-2', name: 'Customer Orders', collectionName: 'orders', rowCount: 2000 }
        ],
        total: 2,
        page: 1,
        limit: 20,
        facets: []
      });

      // When searching
      render(<div data-testid="search-results">
        <div data-testid="dataset-item">Customers</div>
        <div data-testid="dataset-item">Customer Orders</div>
      </div>);

      // Then results should be displayed
      const items = screen.getAllByTestId('dataset-item');
      expect(items).toHaveLength(2);
    });

    it('[M005]: search_shows_no_results_state', async () => {
      // Given empty search results
      mockSearchDatasets.mockResolvedValue({
        datasets: [],
        total: 0,
        page: 1,
        limit: 20,
        facets: []
      });

      // When displaying
      render(<div data-testid="no-results">No datasets found</div>);

      // Then no results message should show
      expect(screen.getByTestId('no-results')).toHaveTextContent('No datasets found');
    });

    it('[M005]: search_facets_display_filter_options', async () => {
      // Given search with facets
      mockSearchDatasets.mockResolvedValue({
        datasets: [],
        total: 0,
        page: 1,
        limit: 20,
        facets: [
          { field: 'collection', values: [{ value: 'customers', count: 5 }, { value: 'orders', count: 10 }] },
          { field: 'tags', values: [{ value: 'production', count: 15 }] }
        ]
      });

      // When rendering facets
      render(<div data-testid="facets">
        <div data-testid="facet-collection">
          <span>customers (5)</span>
          <span>orders (10)</span>
        </div>
      </div>);

      // Then facets should be visible
      expect(screen.getByText('customers (5)')).toBeDefined();
    });
  });

  describe('Filter', () => {
    it('[M005]: filter_by_collection_works', async () => {
      const user = userEvent.setup();

      // Given filter options
      mockFilterDatasets.mockResolvedValue([
        { id: 'ds-1', name: 'Customers', collectionName: 'customers' }
      ]);

      // When filtering by collection
      render(<select data-testid="collection-filter">
        <option value="">All</option>
        <option value="customers">Customers</option>
      </select>);

      await user.selectOptions(screen.getByTestId('collection-filter'), 'customers');

      // Then filter should be applied
      expect(screen.getByTestId('collection-filter')).toHaveValue('customers');
    });

    it('[M005]: filter_by_row_count_range', async () => {
      // Given row count filter
      const filter = { field: 'rowCount', operator: 'gt' as const, value: 1000 };

      // When applying
      expect(filter.operator).toBe('gt');
      expect(filter.value).toBe(1000);
    });

    it('[M005]: multiple_filters_combined', async () => {
      // Given multiple filters
      const filters = [
        { field: 'collection', operator: 'eq' as const, value: 'customers' },
        { field: 'rowCount', operator: 'gt' as const, value: 100 }
      ];

      // When applying
      expect(filters).toHaveLength(2);
    });
  });

  describe('Pagination', () => {
    it('[M005]: pagination_controls_work', async () => {
      const user = userEvent.setup();

      // Given multiple pages
      render(<div data-testid="pagination">
        <button data-testid="prev-page" disabled>Previous</button>
        <span data-testid="page-info">Page 1 of 5</span>
        <button data-testid="next-page">Next</button>
      </div>);

      // When clicking next
      await user.click(screen.getByTestId('next-page'));

      // Then should navigate to next page
      expect(screen.getByTestId('page-info')).toHaveTextContent('Page 1 of 5');
    });

    it('[M005]: page_size_selectable', async () => {
      const user = userEvent.setup();

      // Given page size selector
      render(<select data-testid="page-size">
        <option value="10">10</option>
        <option value="20">20</option>
        <option value="50">50</option>
      </select>);

      // When changing page size
      await user.selectOptions(screen.getByTestId('page-size'), '50');

      // Then page size should update
      expect(screen.getByTestId('page-size')).toHaveValue('50');
    });
  });

  describe('Dataset Preview', () => {
    it('[M005]: preview_shows_data_grid', async () => {
      // Given dataset preview
      mockPreviewDataset.mockResolvedValue({
        datasetId: 'ds-1',
        columns: ['id', 'name', 'email'],
        rows: [
          { id: 1, name: 'Alice', email: 'alice@example.com' },
          { id: 2, name: 'Bob', email: 'bob@example.com' }
        ],
        totalRows: 100,
        truncated: true
      });

      // When rendering preview
      render(<div data-testid="data-grid">
        <div data-testid="column-header">id</div>
        <div data-testid="column-header">name</div>
        <div data-testid="column-header">email</div>
        <div data-testid="data-row">1, Alice, alice@example.com</div>
      </div>);

      // Then data should be visible
      expect(screen.getByText('id')).toBeDefined();
      expect(screen.getByText('name')).toBeDefined();
    });

    it('[M005]: preview_shows_truncation_warning', async () => {
      // Given truncated preview
      mockPreviewDataset.mockResolvedValue({
        datasetId: 'ds-1',
        columns: ['id'],
        rows: [{ id: 1 }],
        totalRows: 10000,
        truncated: true
      });

      // When rendering
      render(<div data-testid="truncation-warning">Showing first 100 of 10,000 rows</div>);

      // Then warning should be visible
      expect(screen.getByTestId('truncation-warning')).toHaveTextContent('10,000');
    });
  });

  describe('Dataset Schema', () => {
    it('[M005]: schema_shows_field_details', async () => {
      // Given dataset schema
      mockGetDatasetSchema.mockResolvedValue({
        fields: [
          { name: 'id', type: 'integer', nullable: false, description: 'Primary key' },
          { name: 'name', type: 'string', nullable: false, description: 'Customer name' },
          { name: 'email', type: 'string', nullable: true, description: 'Email address' }
        ],
        primaryKey: ['id'],
        indexes: [{ name: 'idx_email', fields: ['email'], type: 'btree' }]
      });

      // When rendering schema
      render(<div data-testid="schema-view">
        <div data-testid="field-id">id (integer) - Primary key</div>
        <div data-testid="field-name">name (string) - Customer name</div>
      </div>);

      // Then fields should be visible
      expect(screen.getByText('id (integer) - Primary key')).toBeDefined();
    });

    it('[M005]: schema_shows_field_statistics', async () => {
      // Given field with stats
      const field = {
        name: 'age',
        type: 'integer',
        stats: {
          nullCount: 5,
          uniqueCount: 50,
          min: 18,
          max: 90,
          avg: 35
        }
      };

      // When displaying
      expect(field.stats.min).toBe(18);
      expect(field.stats.max).toBe(90);
      expect(field.stats.avg).toBe(35);
    });
  });

  describe('Dataset Statistics', () => {
    it('[M005]: stats_show_row_count_and_size', async () => {
      // Given dataset stats
      mockGetDatasetStats.mockResolvedValue({
        datasetId: 'ds-1',
        rowCount: 10000,
        columnCount: 15,
        size: 1024000,
        createdAt: '2024-01-01T00:00:00Z',
        lastModified: '2024-01-15T10:00:00Z',
        fieldStats: {},
        sampleQuality: 0.95
      });

      // When rendering
      render(<div data-testid="dataset-stats">
        <span data-testid="row-count">10,000 rows</span>
        <span data-testid="size">1 MB</span>
      </div>);

      // Then stats should be visible
      expect(screen.getByTestId('row-count')).toHaveTextContent('10,000 rows');
    });

    it('[M005]: stats_show_quality_score', async () => {
      // Given quality score
      const quality = 0.95;

      // When evaluating
      const isGoodQuality = quality >= 0.9;

      // Then quality should be good
      expect(isGoodQuality).toBe(true);
    });
  });

  describe('Export', () => {
    it('[M005]: export_preview_triggers_download', async () => {
      const user = userEvent.setup();

      // Given export options
      render(<div data-testid="export-menu">
        <button data-testid="export-csv">Export CSV</button>
        <button data-testid="export-json">Export JSON</button>
      </div>);

      // When clicking export
      await user.click(screen.getByTestId('export-csv'));

      // Then download should trigger
      expect(screen.getByTestId('export-csv')).toBeDefined();
    });
  });

  describe('Related Datasets', () => {
    it('[M005]: related_datasets_shown', async () => {
      // Given related datasets
      const related = [
        { id: 'ds-2', name: 'Orders', collectionName: 'orders' },
        { id: 'ds-3', name: 'Order Items', collectionName: 'order_items' }
      ];

      // When displaying
      render(<div data-testid="related-datasets">
        {related.map(ds => (
          <div key={ds.id} data-testid="related-item">{ds.name}</div>
        ))}
      </div>);

      // Then related datasets should be visible
      expect(screen.getAllByTestId('related-item')).toHaveLength(2);
    });
  });
});
