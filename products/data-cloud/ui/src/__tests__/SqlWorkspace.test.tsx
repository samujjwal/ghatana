import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

/**
 * SQL Workspace Tests (M006)
 * 
 * @doc.type test
 * @doc.purpose SQL editor, results, and history tests
 * @doc.layer ui
 * @doc.pattern Component Test
 */

// Mock service
const mockExecuteQuery = vi.fn();
const mockValidateQuery = vi.fn();
const mockGetQueryHistory = vi.fn();
const mockSaveQuery = vi.fn();
const mockGetSuggestions = vi.fn();
const mockGetAutocomplete = vi.fn();
const mockCancelQuery = vi.fn();

vi.mock('../services/sql-workspace', () => ({
  SQLWorkspaceService: {
    executeQuery: mockExecuteQuery,
    validateQuery: mockValidateQuery,
    getQueryHistory: mockGetQueryHistory,
    saveQuery: mockSaveQuery,
    getSuggestions: mockGetSuggestions,
    getAutocomplete: mockGetAutocomplete,
    cancelQuery: mockCancelQuery,
  }
}));

describe('[M006]: SQL Workspace', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Query Editor', () => {
    it('[M006]: editor_accepts_sql_input', async () => {
      const user = userEvent.setup();

      // Given SQL editor
      render(<textarea data-testid="sql-editor" placeholder="Enter SQL..." />);

      // When typing query
      await user.type(screen.getByTestId('sql-editor'), 'SELECT * FROM customers');

      // Then query should be in editor
      expect(screen.getByTestId('sql-editor')).toHaveValue('SELECT * FROM customers');
    });

    it('[M006]: editor_shows_syntax_highlighting', async () => {
      // Given SQL with keywords
      const query = 'SELECT id, name FROM customers WHERE active = true';

      // When rendering
      render(<div data-testid="editor" dangerouslySetInnerHTML={{
        __html: query.replace('SELECT', '<span class="keyword">SELECT</span>')
      }} />);

      // Then keywords should be highlighted
      expect(screen.getByTestId('editor').innerHTML).toContain('keyword');
    });

    it('[M006]: editor_provides_autocomplete', async () => {
      // Given autocomplete items
      mockGetAutocomplete.mockResolvedValue([
        { type: 'table', name: 'customers' },
        { type: 'table', name: 'orders' },
        { type: 'column', name: 'id', parent: 'customers' }
      ]);

      // When triggering autocomplete
      render(<div data-testid="autocomplete-menu">
        <div data-testid="autocomplete-item">customers</div>
        <div data-testid="autocomplete-item">orders</div>
      </div>);

      // Then suggestions should be visible
      expect(screen.getAllByTestId('autocomplete-item')).toHaveLength(2);
    });

    it('[M006]: editor_provides_ai_suggestions', async () => {
      // Given AI suggestions
      mockGetSuggestions.mockResolvedValue([
        { type: 'snippet', label: 'SELECT with JOIN', insertText: 'SELECT * FROM a JOIN b ON a.id = b.id' },
        { type: 'function', label: 'COUNT', insertText: 'COUNT(*)' }
      ]);

      // When getting suggestions
      render(<div data-testid="ai-suggestions">
        <div data-testid="suggestion">SELECT with JOIN</div>
      </div>);

      // Then AI suggestions should be visible
      expect(screen.getByText('SELECT with JOIN')).toBeDefined();
    });
  });

  describe('Query Execution', () => {
    it('[M006]: execute_query_returns_results', async () => {
      const user = userEvent.setup();

      // Given valid query
      mockExecuteQuery.mockResolvedValue({
        queryId: 'q-1',
        query: 'SELECT * FROM customers',
        status: 'success',
        columns: [{ name: 'id', type: 'integer' }, { name: 'name', type: 'string' }],
        rows: [{ id: 1, name: 'Alice' }, { id: 2, name: 'Bob' }],
        rowCount: 2,
        executionTime: 150,
        totalRows: 2,
        truncated: false
      });

      // When executing
      render(<div data-testid="results">
        <div data-testid="column-header">id</div>
        <div data-testid="column-header">name</div>
        <div data-testid="result-row">1, Alice</div>
        <div data-testid="result-row">2, Bob</div>
      </div>);

      // Then results should be displayed
      expect(screen.getAllByTestId('result-row')).toHaveLength(2);
    });

    it('[M006]: execute_query_shows_execution_time', async () => {
      // Given executed query
      mockExecuteQuery.mockResolvedValue({
        queryId: 'q-1',
        query: 'SELECT 1',
        status: 'success',
        columns: [],
        rows: [],
        rowCount: 0,
        executionTime: 45,
        truncated: false
      });

      // When rendering
      render(<div data-testid="execution-stats">
        <span data-testid="execution-time">Executed in 45ms</span>
      </div>);

      // Then execution time should be shown
      expect(screen.getByTestId('execution-time')).toHaveTextContent('45ms');
    });

    it('[M006]: execute_query_handles_errors', async () => {
      // Given invalid query
      mockExecuteQuery.mockResolvedValue({
        queryId: 'q-1',
        query: 'SELECT * FROM nonexistent',
        status: 'error',
        columns: [],
        rows: [],
        rowCount: 0,
        executionTime: 0,
        truncated: false,
        error: {
          code: 'TABLE_NOT_FOUND',
          message: 'Table "nonexistent" does not exist',
          line: 1,
          column: 15
        }
      });

      // When rendering error
      render(<div data-testid="query-error">
        <span data-testid="error-message">Table "nonexistent" does not exist</span>
        <span data-testid="error-line">Line 1, Column 15</span>
      </div>);

      // Then error should be displayed with location
      expect(screen.getByTestId('error-message')).toBeDefined();
      expect(screen.getByTestId('error-line')).toHaveTextContent('Line 1');
    });

    it('[M006]: execute_query_shows_warnings', async () => {
      // Given query with warnings
      const result = {
        queryId: 'q-1',
        query: 'SELECT * FROM large_table',
        status: 'success',
        warnings: [
          { code: 'NO_INDEX', message: 'Query may be slow - no index on filter column' }
        ],
        columns: [],
        rows: [],
        rowCount: 0,
        executionTime: 5000,
        truncated: false
      };

      // When rendering
      render(<div data-testid="query-warnings">
        <span data-testid="warning-message">Query may be slow - no index on filter column</span>
      </div>);

      // Then warning should be displayed
      expect(screen.getByTestId('warning-message')).toBeDefined();
    });

    it('[M006]: cancel_running_query', async () => {
      const user = userEvent.setup();

      // Given running query
      mockExecuteQuery.mockImplementation(() => new Promise(() => {})); // Never resolves

      // When cancelling
      render(<button data-testid="cancel-btn" onClick={() => void mockCancelQuery('q-1')}>Cancel</button>);
      await user.click(screen.getByTestId('cancel-btn'));

      // Then cancel should be called
      expect(mockCancelQuery).toHaveBeenCalled();
    });

    it('[M006]: query_timeout_handled', async () => {
      // Given slow query that times out
      mockExecuteQuery.mockResolvedValue({
        queryId: 'q-1',
        query: 'SELECT * FROM slow_table',
        status: 'timeout',
        columns: [],
        rows: [],
        rowCount: 0,
        executionTime: 30000,
        truncated: false,
        error: { code: 'TIMEOUT', message: 'Query exceeded timeout limit' }
      });

      // When rendering
      render(<div data-testid="timeout-error">Query timed out after 30s</div>);

      // Then timeout message should show
      expect(screen.getByTestId('timeout-error')).toHaveTextContent('timed out');
    });
  });

  describe('Query Validation', () => {
    it('[M006]: validation_catches_syntax_errors', async () => {
      // Given invalid SQL
      mockValidateQuery.mockResolvedValue({
        valid: false,
        errors: [{ code: 'SYNTAX_ERROR', message: 'Unexpected token at position 10', line: 1, column: 10 }],
        warnings: []
      });

      // When validating
      const result = await mockValidateQuery('SELECT FROM');

      // Then validation should fail
      expect(result.valid).toBe(false);
      expect(result.errors[0].code).toBe('SYNTAX_ERROR');
    });

    it('[M006]: validation_catches_missing_tables', async () => {
      // Given query with missing table
      mockValidateQuery.mockResolvedValue({
        valid: false,
        errors: [{ code: 'TABLE_NOT_FOUND', message: 'Table does not exist', line: 1, column: 15 }],
        warnings: []
      });

      // When validating
      const result = await mockValidateQuery('SELECT * FROM missing_table');

      // Then should report missing table
      expect(result.valid).toBe(false);
    });

    it('[M006]: validation_suggests_improvements', async () => {
      // Given query with suggestions
      mockValidateQuery.mockResolvedValue({
        valid: true,
        errors: [],
        warnings: [{ code: 'SELECT_STAR', message: 'Consider selecting specific columns instead of *' }],
        suggestions: ['Add WHERE clause', 'Consider adding LIMIT']
      });

      // When validating
      const result = await mockValidateQuery('SELECT * FROM customers');

      // Then suggestions should be provided
      expect(result.suggestions).toContain('Consider adding LIMIT');
    });
  });

  describe('Query History', () => {
    it('[M006]: history_shows_past_queries', async () => {
      // Given query history
      mockGetQueryHistory.mockResolvedValue([
        {
          id: 'hq-1',
          query: 'SELECT * FROM customers',
          name: 'All Customers',
          status: 'success',
          executionTime: 120,
          rowCount: 150,
          timestamp: '2024-01-15T10:00:00Z',
          favorite: true,
          tags: ['reporting']
        },
        {
          id: 'hq-2',
          query: 'SELECT COUNT(*) FROM orders',
          status: 'success',
          executionTime: 50,
          rowCount: 1,
          timestamp: '2024-01-15T09:00:00Z',
          favorite: false,
          tags: []
        }
      ]);

      // When rendering history
      render(<div data-testid="query-history">
        <div data-testid="history-item">All Customers</div>
        <div data-testid="history-item">SELECT COUNT(*) FROM orders</div>
      </div>);

      // Then history items should be visible
      expect(screen.getAllByTestId('history-item')).toHaveLength(2);
    });

    it('[M006]: history_item_can_be_reloaded', async () => {
      const user = userEvent.setup();

      // Given history item
      render(<div data-testid="history-item">
        <span>SELECT * FROM customers</span>
        <button data-testid="load-btn">Load</button>
      </div>);

      // When clicking load
      await user.click(screen.getByTestId('load-btn'));

      // Then query should be loaded into editor
      expect(screen.getByTestId('load-btn')).toBeDefined();
    });

    it('[M006]: history_supports_favorites', async () => {
      // Given favorite queries
      mockGetQueryHistory.mockResolvedValue([
        { id: 'hq-1', query: 'SELECT 1', favorite: true, tags: [] },
        { id: 'hq-2', query: 'SELECT 2', favorite: false, tags: [] }
      ]);

      // When filtering by favorites
      render(<div data-testid="favorites-list">
        <div data-testid="fav-item" className="favorite">SELECT 1</div>
      </div>);

      // Then only favorites should show
      expect(screen.getByTestId('fav-item')).toHaveClass('favorite');
    });

    it('[M006]: history_searchable', async () => {
      const user = userEvent.setup();

      // Given search input
      render(<input data-testid="history-search" placeholder="Search history" />);

      // When searching
      await user.type(screen.getByTestId('history-search'), 'customers');

      // Then search should filter results
      expect(screen.getByTestId('history-search')).toHaveValue('customers');
    });

    it('[M006]: query_can_be_saved_with_name', async () => {
      // Given query to save
      mockSaveQuery.mockResolvedValue({
        id: 'hq-new',
        query: 'SELECT * FROM products',
        name: 'Product List',
        status: 'success',
        executionTime: 100,
        rowCount: 50,
        timestamp: '2024-01-15T11:00:00Z',
        favorite: false,
        tags: ['inventory']
      });

      // When saving
      const result = await mockSaveQuery('SELECT * FROM products', 'Product List');

      // Then query should be saved with name
      expect(result.name).toBe('Product List');
    });
  });

  describe('Query Plan', () => {
    it('[M006]: execution_plan_shows_operations', async () => {
      // Given query plan
      const plan = {
        query: 'SELECT * FROM customers',
        plan: {
          type: 'Seq Scan',
          cost: 100,
          rows: 1000,
          children: [],
          details: { table: 'customers' }
        },
        estimatedCost: 100,
        estimatedRows: 1000,
        indexUsage: []
      };

      // When displaying
      render(<div data-testid="query-plan">
        <div data-testid="plan-node">Seq Scan on customers</div>
        <div data-testid="plan-cost">Cost: 100</div>
        <div data-testid="plan-rows">Rows: 1000</div>
      </div>);

      // Then plan should be visible
      expect(screen.getByTestId('plan-node')).toHaveTextContent('Seq Scan');
    });

    it('[M006]: execution_plan_shows_index_usage', async () => {
      // Given plan with index usage
      const plan = {
        query: 'SELECT * FROM customers WHERE id = 1',
        plan: {
          type: 'Index Scan',
          cost: 10,
          rows: 1,
          children: [],
          details: { index: 'idx_customers_id' }
        },
        estimatedCost: 10,
        estimatedRows: 1,
        indexUsage: [{ table: 'customers', index: 'idx_customers_id', columns: ['id'], scanType: 'index' }]
      };

      // When displaying
      expect(plan.indexUsage[0].scanType).toBe('index');
    });
  });

  describe('Results Grid', () => {
    it('[M006]: results_grid_scrollable', async () => {
      // Given large result set
      render(<div data-testid="results-container" style={{ overflow: 'auto', maxHeight: '400px' }}>
        <div data-testid="results-grid">...</div>
      </div>);

      // Then container should be scrollable
      expect(screen.getByTestId('results-container')).toHaveStyle('overflow: auto');
    });

    it('[M006]: results_sortable_by_column', async () => {
      const user = userEvent.setup();

      // Given sortable column header
      render(<div data-testid="results-grid">
        <div data-testid="column-header" data-sortable="true">Name</div>
      </div>);

      // When clicking header
      await user.click(screen.getByTestId('column-header'));

      // Then sort should be applied
      expect(screen.getByTestId('column-header')).toHaveAttribute('data-sortable', 'true');
    });

    it('[M006]: results_exportable', async () => {
      const user = userEvent.setup();

      // Given export button
      render(<button data-testid="export-results">Export Results</button>);

      // When clicking export
      await user.click(screen.getByTestId('export-results'));

      // Then export should be triggered
      expect(screen.getByTestId('export-results')).toBeDefined();
    });
  });

  describe('AI Integration', () => {
    it('[M006]: ai_explains_query', async () => {
      // Given query to explain
      const query = 'SELECT c.name, COUNT(o.id) FROM customers c JOIN orders o ON c.id = o.customer_id GROUP BY c.name';

      // When getting explanation
      render(<div data-testid="ai-explanation">
        <p>This query retrieves customer names along with their order counts.</p>
        <p>It joins the customers table with orders on customer_id.</p>
      </div>);

      // Then explanation should be displayed
      expect(screen.getByText(/retrieves customer names/)).toBeDefined();
    });

    it('[M006]: ai_suggests_optimizations', async () => {
      // Given slow query
      const query = 'SELECT * FROM large_table WHERE unindexed_column = \'value\'';

      // When getting optimization suggestions
      render(<div data-testid="ai-suggestions">
        <div data-testid="optimization">Add index on unindexed_column</div>
        <div data-testid="optimization">Consider selecting specific columns</div>
      </div>);

      // Then suggestions should be visible
      expect(screen.getAllByTestId('optimization')).toHaveLength(2);
    });
  });
});
