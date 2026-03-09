/**
 * Collections Page
 *
 * Handles both the collections list view and individual collection detail view.
 *
 * @doc.type page
 * @doc.purpose Display and manage collections
 * @doc.layer frontend
 */

import React, { useEffect, useState } from 'react';
import { Link, useParams, useNavigate } from 'react-router';
import { dataCloudApi } from '../lib/api/data-cloud-api';
import type { Collection } from '../lib/api/collections';
import { ArrowLeft, Database, Edit, Plus, XCircle, ArrowRight } from 'lucide-react';

// Collection Detail View Component
const CollectionDetail = ({ collection, onBack }: { collection: Collection; onBack: () => void }) => (
  <div className="space-y-6">
    <div className="flex items-center justify-between">
      <button
        onClick={onBack}
        className="flex items-center text-sm text-gray-600 hover:text-gray-900"
      >
        <ArrowLeft className="h-4 w-4 mr-1" /> Back to Collections
      </button>
      <div className="flex space-x-3">
        <Link
          to={`/collections/${collection.id}/edit`}
          className="inline-flex items-center px-3 py-2 border border-gray-300 shadow-sm text-sm leading-4 font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500"
        >
          <Edit className="h-4 w-4 mr-2" /> Edit
        </Link>
      </div>
    </div>

    <div className="bg-white shadow overflow-hidden sm:rounded-lg">
      <div className="px-4 py-5 sm:px-6 border-b border-gray-200">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-2xl font-bold text-gray-900">{collection.name}</h2>
            <p className="mt-1 max-w-2xl text-sm text-gray-500">
              {collection.description || 'No description provided'}
            </p>
          </div>
          <span className={`inline-flex items-center px-3 py-0.5 rounded-full text-sm font-medium ${collection.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
            }`}>
            {collection.isActive ? 'Active' : 'Inactive'}
          </span>
        </div>
      </div>
      <div className="border-t border-gray-200 px-4 py-5 sm:p-0">
        <dl className="sm:divide-y sm:divide-gray-200">
          <div className="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
            <dt className="text-sm font-medium text-gray-500">ID</dt>
            <dd className="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">
              {collection.id}
            </dd>
          </div>
          <div className="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
            <dt className="text-sm font-medium text-gray-500">Entity Count</dt>
            <dd className="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">
              {collection.entityCount.toLocaleString()}
            </dd>
          </div>
          <div className="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
            <dt className="text-sm font-medium text-gray-500">Schema Fields</dt>
            <dd className="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">
              <div className="flex flex-wrap gap-2">
                {collection.schema.fields.map((field) => (
                  <span
                    key={field.id}
                    className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800"
                  >
                    {field.name} ({field.type})
                  </span>
                ))}
              </div>
            </dd>
          </div>
          <div className="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
            <dt className="text-sm font-medium text-gray-500">Created At</dt>
            <dd className="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">
              {new Date(collection.createdAt).toLocaleString()}
            </dd>
          </div>
          <div className="py-4 sm:py-5 sm:grid sm:grid-cols-3 sm:gap-4 sm:px-6">
            <dt className="text-sm font-medium text-gray-500">Last Updated</dt>
            <dd className="mt-1 text-sm text-gray-900 sm:mt-0 sm:col-span-2">
              {new Date(collection.updatedAt).toLocaleString()}
            </dd>
          </div>
        </dl>
      </div>
    </div>
  </div>
);

// Collections List View Component
const CollectionsList = ({ collections, loading, error }: { collections: Collection[]; loading: boolean; error: string | null }) => {
  if (loading) {
    return (
      <div className="p-8 text-center">
        <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
        <p className="mt-2 text-gray-600">Loading collections...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-4 bg-red-50 rounded-md">
        <div className="flex">
          <div className="flex-shrink-0">
            <XCircle className="h-5 w-5 text-red-400" aria-hidden="true" />
          </div>
          <div className="ml-3">
            <h3 className="text-sm font-medium text-red-800">Error loading collections</h3>
            <div className="mt-2 text-sm text-red-700">
              <p>{error}</p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (collections.length === 0) {
    return (
      <div className="text-center py-12">
        <Database className="mx-auto h-12 w-12 text-gray-400" />
        <h3 className="mt-2 text-sm font-medium text-gray-900">No collections</h3>
        <p className="mt-1 text-sm text-gray-500">Get started by creating a new collection.</p>
        <div className="mt-6">
          <Link
            to="/collections/new"
            className="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500"
          >
            <Plus className="-ml-1 mr-2 h-5 w-5" />
            New Collection
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex justify-end mb-4">
        <Link
          to="/collections/new"
          className="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500"
        >
          <Plus className="-ml-1 mr-2 h-5 w-5" />
          Create Collection
        </Link>
      </div>
      {collections.map((collection) => (
        <Link
          key={collection.id}
          to={`/collections/${collection.id}`}
          className="block hover:shadow-md transition-shadow duration-200"
        >
          <div className="bg-white p-6 rounded-lg border border-gray-200">
            <div className="flex items-start justify-between">
              <div className="flex-1">
                <div className="flex items-center">
                  <Database className="h-5 w-5 text-indigo-600 mr-3" />
                  <h3 className="text-lg font-medium text-gray-900">{collection.name}</h3>
                  <span className={`ml-3 inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${collection.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                    }`}>
                    {collection.isActive ? 'Active' : 'Inactive'}
                  </span>
                </div>
                {collection.description && (
                  <p className="mt-1 text-sm text-gray-500">{collection.description}</p>
                )}
                <div className="mt-3 flex items-center text-sm text-gray-500">
                  <span className="font-medium text-gray-900">
                    {collection.entityCount.toLocaleString()}
                  </span>
                  <span className="mx-1">entities</span>
                  <span>•</span>
                  <span className="ml-1">
                    Updated {new Date(collection.updatedAt).toLocaleDateString()}
                  </span>
                </div>
              </div>
              <ArrowRight className="h-5 w-5 text-gray-400" />
            </div>
          </div>
        </Link>
      ))}
    </div>
  );
};

export function CollectionsPage(): React.ReactElement {
  const { id } = useParams<{ id?: string }>();
  const navigate = useNavigate();
  const [collections, setCollections] = useState<Collection[]>([]);
  const [collection, setCollection] = useState<Collection | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Load collections or specific collection based on route
  useEffect(() => {
    const loadData = async () => {
      try {
        setLoading(true);

        if (id) {
          // Load specific collection
          const res = await dataCloudApi.getCollectionById(id);
          setCollection(res.data);
        } else {
          // Load all collections
          const res = await dataCloudApi.getCollections();
          setCollections(res.data);
        }
        setError(null);
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load data');
        console.error('Error loading data:', err);
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, [id]);

  const handleBack = () => {
    navigate('/collections');
  };

  // If we have a specific collection ID, show the detail view
  if (id) {
    if (loading) {
      return (
        <div className="max-w-5xl mx-auto p-8">
          <div className="p-8 text-center">
            <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-primary-600"></div>
            <p className="mt-2 text-gray-600">Loading collection details...</p>
          </div>
        </div>
      );
    }

    if (error || !collection) {
      return (
        <div className="max-w-5xl mx-auto p-8">
          <div className="p-4 bg-red-50 rounded-md">
            <div className="flex">
              <div className="flex-shrink-0">
                <XCircle className="h-5 w-5 text-red-400" aria-hidden="true" />
              </div>
              <div className="ml-3">
                <h3 className="text-sm font-medium text-red-800">Error loading collection</h3>
                <div className="mt-2 text-sm text-red-700">
                  <p>{error || 'Collection not found'}</p>
                </div>
                <div className="mt-4">
                  <button
                    type="button"
                    onClick={handleBack}
                    className="inline-flex items-center px-3 py-2 border border-gray-300 shadow-sm text-sm leading-4 font-medium rounded-md text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500"
                  >
                    <ArrowLeft className="-ml-0.5 mr-2 h-4 w-4" />
                    Back to Collections
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      );
    }

    return (
      <div className="max-w-5xl mx-auto p-6">
        <CollectionDetail collection={collection} onBack={handleBack} />
      </div>
    );
  }

  // Show the list view
  return (
    <div className="max-w-5xl mx-auto p-6">
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Collections</h1>
        <Link
          to="/collections/new"
          className="inline-flex items-center px-4 py-2 border border-transparent shadow-sm text-sm font-medium rounded-md text-white bg-primary-600 hover:bg-primary-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500"
        >
          <Plus className="-ml-1 mr-2 h-5 w-5" />
          New Collection
        </Link>
      </div>

      <CollectionsList collections={collections} loading={loading} error={error} />
    </div>
  );
}

export default CollectionsPage;
