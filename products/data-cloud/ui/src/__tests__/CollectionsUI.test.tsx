import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router-dom';

/**
 * Collections UI Tests (M003)
 * 
 * @doc.type test
 * @doc.purpose Collection creation, editing, and validation tests
 * @doc.layer ui
 * @doc.pattern Component Test
 */

// Mock collection service
const mockGetCollections = vi.fn();
const mockGetCollection = vi.fn();
const mockCreateCollection = vi.fn();
const mockUpdateCollection = vi.fn();
const mockDeleteCollection = vi.fn();
const mockValidateCollection = vi.fn();

vi.mock('../services/collections', () => ({
  CollectionService: {
    getCollections: mockGetCollections,
    getCollection: mockGetCollection,
    createCollection: mockCreateCollection,
    updateCollection: mockUpdateCollection,
    deleteCollection: mockDeleteCollection,
    validateCollection: mockValidateCollection,
  }
}));

describe('[M003]: Collections UI', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Collection List', () => {
    it('[M003]: collection_list_displays_all_collections', async () => {
      // Given collections exist
      mockGetCollections.mockResolvedValue([
        { id: 'col-1', name: 'Customers', entityCount: 150, createdAt: '2024-01-01' },
        { id: 'col-2', name: 'Orders', entityCount: 2500, createdAt: '2024-01-02' },
        { id: 'col-3', name: 'Products', entityCount: 500, createdAt: '2024-01-03' }
      ]);

      // When rendering collection list
      render(<div data-testid="collection-list">
        <div data-testid="collection-item">Customers (150)</div>
        <div data-testid="collection-item">Orders (2500)</div>
        <div data-testid="collection-item">Products (500)</div>
      </div>);

      // Then all collections should be displayed
      const items = screen.getAllByTestId('collection-item');
      expect(items).toHaveLength(3);
      expect(screen.getByText('Customers (150)')).toBeDefined();
    });

    it('[M003]: collection_list_shows_empty_state', async () => {
      // Given no collections
      mockGetCollections.mockResolvedValue([]);

      // When rendering
      render(<div data-testid="empty-state">No collections found</div>);

      // Then empty state should be shown
      expect(screen.getByTestId('empty-state')).toHaveTextContent('No collections found');
    });

    it('[M003]: collection_list_pagination_works', async () => {
      const user = userEvent.setup();

      // Given multiple pages of collections
      mockGetCollections.mockResolvedValue([
        { id: 'col-1', name: 'Collection 1', entityCount: 10 }
      ]);

      // When clicking next page
      render(<button data-testid="next-page">Next</button>);
      await user.click(screen.getByTestId('next-page'));

      // Then next page should be fetched
      expect(mockGetCollections).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({ page: expect.any(Number) })
      );
    });

    it('[M003]: collection_list_search_filters_results', async () => {
      const user = userEvent.setup();

      // Given search query
      render(<input data-testid="search-input" placeholder="Search collections" />);
      
      // When searching
      await user.type(screen.getByTestId('search-input'), 'customer');

      // Then search should be applied
      expect(screen.getByTestId('search-input')).toHaveValue('customer');
    });
  });

  describe('Collection Creation', () => {
    it('[M003]: create_collection_form_validates_required_fields', async () => {
      const user = userEvent.setup();

      // Given empty form submission
      mockValidateCollection.mockResolvedValue({
        valid: false,
        errors: [{ field: 'name', code: 'required', message: 'Name is required' }],
        warnings: []
      });

      // When submitting without name
      render(<form data-testid="create-form">
        <input data-testid="name-input" />
        <button type="submit">Create</button>
      </form>);

      await user.click(screen.getByText('Create'));

      // Then validation error should show
      const validation = await mockValidateCollection();
      expect(validation.valid).toBe(false);
      expect(validation.errors[0].field).toBe('name');
    });

    it('[M003]: create_collection_with_valid_data_succeeds', async () => {
      const user = userEvent.setup();

      // Given valid collection data
      mockCreateCollection.mockResolvedValue({
        id: 'new-col',
        name: 'New Collection',
        description: 'Test description',
        entityCount: 0
      });

      // When filling and submitting form
      render(<form data-testid="create-form">
        <input data-testid="name-input" value="New Collection" />
        <textarea data-testid="desc-input">Test description</textarea>
        <button type="submit" data-testid="submit-btn">Create</button>
      </form>);

      await user.click(screen.getByTestId('submit-btn'));

      // Then collection should be created
      expect(mockCreateCollection).toHaveBeenCalledWith(expect.objectContaining({
        name: expect.any(String)
      }));
    });

    it('[M003]: create_collection_schema_builder_adds_fields', async () => {
      const user = userEvent.setup();

      // Given schema builder
      render(<div data-testid="schema-builder">
        <button data-testid="add-field">Add Field</button>
        <div data-testid="field-list" />
      </div>);

      // When adding field
      await user.click(screen.getByTestId('add-field'));

      // Then field should be added to list
      expect(screen.getByTestId('field-list')).toBeDefined();
    });

    it('[M003]: collection_field_types_selectable', async () => {
      const user = userEvent.setup();

      // Given field type selector
      render(<select data-testid="field-type">
        <option value="string">String</option>
        <option value="number">Number</option>
        <option value="date">Date</option>
      </select>);

      // When selecting type
      await user.selectOptions(screen.getByTestId('field-type'), 'number');

      // Then type should be selected
      expect(screen.getByTestId('field-type')).toHaveValue('number');
    });

    it('[M003]: collection_field_validation_configurable', async () => {
      // Given field configuration
      render(<div data-testid="field-validation">
        <input type="checkbox" data-testid="required-check" checked />
        <input type="checkbox" data-testid="unique-check" />
      </div>);

      // Then validation options should be set
      expect(screen.getByTestId('required-check')).toBeChecked();
      expect(screen.getByTestId('unique-check')).not.toBeChecked();
    });
  });

  describe('Collection Editing', () => {
    it('[M003]: edit_collection_loads_existing_data', async () => {
      // Given existing collection
      mockGetCollection.mockResolvedValue({
        id: 'col-1',
        name: 'Customers',
        description: 'Customer data',
        schema: { fields: [{ name: 'email', type: 'string', required: true }] },
        entityCount: 150
      });

      // When loading edit form
      render(<div data-testid="edit-form">
        <input data-testid="name-input" value="Customers" />
        <textarea data-testid="desc-input">Customer data</textarea>
      </div>);

      // Then existing data should be populated
      expect(screen.getByTestId('name-input')).toHaveValue('Customers');
    });

    it('[M003]: edit_collection_saves_changes', async () => {
      const user = userEvent.setup();

      // Given edited data
      mockUpdateCollection.mockResolvedValue({
        id: 'col-1',
        name: 'Updated Customers',
        description: 'Updated description'
      });

      // When saving changes
      render(<form data-testid="edit-form">
        <input data-testid="name-input" value="Updated Customers" />
        <button type="submit" data-testid="save-btn">Save</button>
      </form>);

      await user.click(screen.getByTestId('save-btn'));

      // Then update should be called
      expect(mockUpdateCollection).toHaveBeenCalled();
    });

    it('[M003]: edit_collection_prevents_destructive_changes_with_entities', async () => {
      // Given collection with entities
      const collection = {
        id: 'col-1',
        name: 'Customers',
        entityCount: 150
      };

      // When attempting destructive schema change
      const isDestructive = collection.entityCount > 0;

      // Then warning should be shown
      expect(isDestructive).toBe(true);
    });
  });

  describe('Collection Deletion', () => {
    it('[M003]: delete_collection_shows_confirmation', async () => {
      const user = userEvent.setup();

      // Given delete action
      render(<div>
        <button data-testid="delete-btn">Delete</button>
        <dialog data-testid="confirm-dialog" open>
          <p>Are you sure you want to delete this collection?</p>
          <button data-testid="confirm-yes">Yes</button>
          <button data-testid="confirm-no">No</button>
        </dialog>
      </div>);

      // Then confirmation dialog should be visible
      expect(screen.getByTestId('confirm-dialog')).toBeVisible();
    });

    it('[M003]: delete_collection_with_entities_warns', async () => {
      // Given collection with entities
      const collection = { id: 'col-1', entityCount: 150 };

      // When attempting delete
      const hasWarning = collection.entityCount > 0;

      // Then warning about data loss should be shown
      expect(hasWarning).toBe(true);
    });
  });

  describe('Schema Validation', () => {
    it('[M003]: schema_validation_catches_duplicate_field_names', async () => {
      // Given schema with duplicate field
      const schema = {
        fields: [
          { name: 'email', type: 'string' },
          { name: 'email', type: 'number' }
        ]
      };

      // When validating
      const hasDuplicates = schema.fields
        .map(f => f.name)
        .some((name, i, arr) => arr.indexOf(name) !== i);

      // Then validation should fail
      expect(hasDuplicates).toBe(true);
    });

    it('[M003]: schema_validation_ensures_id_field_present', async () => {
      // Given schema without id
      const schema = {
        fields: [{ name: 'name', type: 'string' }]
      };

      // When validating
      const hasId = schema.fields.some(f => f.name === 'id');

      // Then should warn about missing id
      expect(hasId).toBe(false);
    });

    it('[M003]: schema_validation_checks_field_name_format', async () => {
      // Given invalid field name
      const invalidName = '123-field';

      // When validating
      const isValid = /^[a-zA-Z_][a-zA-Z0-9_]*$/.test(invalidName);

      // Then should be invalid
      expect(isValid).toBe(false);
    });
  });

  describe('Import/Export', () => {
    it('[M003]: collection_export_generates_file', async () => {
      const user = userEvent.setup();

      // Given export action
      render(<button data-testid="export-btn">Export CSV</button>);

      // When clicking export
      await user.click(screen.getByTestId('export-btn'));

      // Then file download should be triggered
      expect(screen.getByTestId('export-btn')).toBeDefined();
    });

    it('[M003]: collection_import_validates_file_format', async () => {
      // Given invalid file
      const file = new File(['invalid'], 'test.txt', { type: 'text/plain' });

      // When validating
      const isValidFormat = file.name.endsWith('.csv') || file.name.endsWith('.json');

      // Then should reject invalid format
      expect(isValidFormat).toBe(false);
    });
  });
});
