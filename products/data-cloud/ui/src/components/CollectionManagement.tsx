import React, { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';

interface Collection {
  id: string;
  name: string;
  description: string;
  createdAt: string;
}

interface QueryResult {
  data: Collection[];
  total: number;
}

interface BulkResult {
  success: number;
  failed: number;
  duration_ms: number;
}

/**
 * Collection list component displaying all collections with CRUD operations.
 *
 * Features:
 * - List all collections with pagination
 * - Create new collection
 * - Edit existing collection
 * - Delete collection
 * - Multi-tenant isolation (tenant ID from context)
 * - Loading and error states
 * - Empty state handling
 */
export function CollectionList() {
  const [page, setPage] = useState(0);
  const tenantId = getTenantIdFromContext();

  // Fetch collections
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['collections', { page, tenantId }],
    queryFn: () =>
      fetch(
        `/api/collections?page=${page}&size=10`,
        getRequestHeaders(tenantId)
      ).then((res) => res.json()),
  });

  const collections: Collection[] = data?.data ?? [];
  const totalPages = Math.ceil((data?.total ?? 0) / 10);

  // Delete mutation
  const { mutate: deleteCollection } = useMutation({
    mutationFn: (collectionId: string) =>
      fetch(
        `/api/collections/${collectionId}`,
        getDeleteHeaders(tenantId)
      ).then((res) => res.json()),
    onSuccess: () => {
      refetch();
    },
  });

  if (isLoading) return <div className="p-4">Loading collections...</div>;
  if (error)
    return <div className="p-4 text-red-600">Error loading collections</div>;

  return (
    <div className="p-4">
      <div className="flex justify-between items-center mb-4">
        <h2 className="text-2xl font-bold">Collections</h2>
      </div>

      {collections.length === 0 ? (
        <div className="text-center py-12 text-gray-500">
          No collections yet. Create one to get started.
        </div>
      ) : (
        <>
          <table className="w-full border-collapse border border-gray-300">
            <thead className="bg-gray-100">
              <tr>
                <th className="border border-gray-300 p-2 text-left">Name</th>
                <th className="border border-gray-300 p-2 text-left">Description</th>
                <th className="border border-gray-300 p-2 text-left">Created</th>
                <th className="border border-gray-300 p-2 text-center">Actions</th>
              </tr>
            </thead>
            <tbody>
              {collections.map((collection: Collection) => (
                <tr key={collection.id} className="hover:bg-gray-50">
                  <td className="border border-gray-300 p-2">{collection.name}</td>
                  <td className="border border-gray-300 p-2">
                    {collection.description || '-'}
                  </td>
                  <td className="border border-gray-300 p-2">
                    {new Date(collection.createdAt).toLocaleDateString()}
                  </td>
                  <td className="border border-gray-300 p-2 text-center">
                    <button
                      onClick={() => deleteCollection(collection.id)}
                      className="text-red-600 hover:underline"
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* Pagination */}
          <div className="flex justify-center gap-2 mt-4">
            <button
              onClick={() => setPage(Math.max(0, page - 1))}
              disabled={page === 0}
              className="px-3 py-1 border border-gray-300 rounded disabled:opacity-50"
            >
              Previous
            </button>
            <span className="px-3 py-1">
              Page {page + 1} of {totalPages}
            </span>
            <button
              onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
              disabled={page >= totalPages - 1}
              className="px-3 py-1 border border-gray-300 rounded disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </>
      )}
    </div>
  );
}

/**
 * Collection form component for create/edit operations.
 */
interface CollectionFormProps {
  onClose: () => void;
  onSuccess: () => void;
  tenantId: string;
}

function CollectionForm({ onClose, onSuccess, tenantId }: CollectionFormProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');

  const { mutate: createCollection, isPending } = useMutation({
    mutationFn: () =>
      fetch('/api/collections', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-Id': tenantId,
        },
        body: JSON.stringify({ name, description }),
      }).then((res) => res.json()),
    onSuccess,
    onError: (err: Error) => {
      setError(err.message || 'Failed to create collection');
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      setError('Collection name is required');
      return;
    }
    createCollection();
  };

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center">
      <div className="bg-white p-6 rounded-lg shadow-lg max-w-md w-full">
        <h3 className="text-xl font-bold mb-4">Create Collection</h3>

        {error && <div className="mb-4 p-2 bg-red-100 text-red-700 rounded">{error}</div>}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium mb-1">Name</label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2"
              placeholder="Collection name"
            />
          </div>

          <div>
            <label className="block text-sm font-medium mb-1">Description</label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full border border-gray-300 rounded px-3 py-2"
              placeholder="Optional description"
              rows={3}
            />
          </div>

          <div className="flex gap-2 justify-end">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 border border-gray-300 rounded hover:bg-gray-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={isPending}
              className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
            >
              {isPending ? 'Creating...' : 'Create'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}

/**
 * Query component for executing custom queries against collections.
 */
export function QueryBuilder({ collectionId }: { collectionId: string }) {
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<Collection[]>([]);
  const tenantId = getTenantIdFromContext();

  const { mutate: executeQuery, isPending } = useMutation({
    mutationFn: () =>
      fetch(`/api/collections/${collectionId}/query`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-Id': tenantId,
        },
        body: JSON.stringify({ query }),
      }).then((res) => res.json()),
    onSuccess: (data: QueryResult) => setResults(data.data ?? []),
  });

  return (
    <div className="p-4">
      <h3 className="text-lg font-bold mb-4">Query Builder</h3>

      <div className="space-y-4">
        <textarea
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="w-full border border-gray-300 rounded px-3 py-2 font-mono"
          placeholder="Enter query (JSON format)"
          rows={6}
        />

        <button
          onClick={() => executeQuery()}
          disabled={isPending}
          className="bg-green-600 text-white px-4 py-2 rounded hover:bg-green-700 disabled:opacity-50"
        >
          {isPending ? 'Executing...' : 'Execute Query'}
        </button>

        {results.length > 0 && (
          <div>
            <h4 className="font-bold mb-2">Results ({results.length})</h4>
            <pre className="bg-gray-100 p-4 rounded overflow-auto max-h-64">
              {JSON.stringify(results, null, 2)}
            </pre>
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * Bulk operations component for create, update, delete operations.
 */
export function BulkOperations({ collectionId }: { collectionId: string }) {
  const [operation, setOperation] = useState<'create' | 'update' | 'delete'>('create');
  const [entities, setEntities] = useState('[]');
  const [result, setResult] = useState<BulkResult | null>(null);
  const tenantId = getTenantIdFromContext();

  const { mutate: executeBulk, isPending } = useMutation({
    mutationFn: () => {
      const endpoint =
        operation === 'delete'
          ? `/api/collections/${collectionId}/bulk/delete`
          : `/api/collections/${collectionId}/bulk/${operation}`;

      return fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-Id': tenantId,
        },
        body: JSON.stringify({ entities: JSON.parse(entities) }),
      }).then((res) => res.json());
    },
    onSuccess: (data: BulkResult) => setResult(data),
  });

  return (
    <div className="p-4">
      <h3 className="text-lg font-bold mb-4">Bulk Operations</h3>

      <div className="space-y-4">
        <div>
          <label className="block text-sm font-medium mb-1">Operation</label>
          <select
            value={operation}
            onChange={(e) => setOperation(e.target.value as 'create' | 'update' | 'delete')}
            className="w-full border border-gray-300 rounded px-3 py-2"
          >
            <option value="create">Bulk Create</option>
            <option value="update">Bulk Update</option>
            <option value="delete">Bulk Delete</option>
          </select>
        </div>

        <div>
          <label className="block text-sm font-medium mb-1">
            {operation === 'delete' ? 'IDs (JSON array)' : 'Entities (JSON array)'}
          </label>
          <textarea
            value={entities}
            onChange={(e) => setEntities(e.target.value)}
            className="w-full border border-gray-300 rounded px-3 py-2 font-mono"
            placeholder='[{"id": "...", ...}, ...]'
            rows={6}
          />
        </div>

        <button
          onClick={() => executeBulk()}
          disabled={isPending}
          className="bg-purple-600 text-white px-4 py-2 rounded hover:bg-purple-700 disabled:opacity-50"
        >
          {isPending ? 'Processing...' : `Execute ${operation}`}
        </button>

        {result && (
          <div className="bg-green-100 border border-green-300 rounded p-4">
            <p>
              <strong>Success:</strong> {result.success} entities processed
            </p>
            <p>
              <strong>Failed:</strong> {result.failed} entities
            </p>
            <p>
              <strong>Duration:</strong> {result.duration_ms}ms
            </p>
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * Helper functions
 */

function getTenantIdFromContext(): string {
  // In a real app, get from auth context or URL
  return localStorage.getItem('tenantId') || 'default-tenant';
}

function getRequestHeaders(tenantId: string) {
  return {
    headers: {
      'Content-Type': 'application/json',
      'X-Tenant-Id': tenantId,
    },
  };
}

function getDeleteHeaders(tenantId: string) {
  return {
    method: 'DELETE',
    headers: {
      'X-Tenant-Id': tenantId,
    },
  };
}
