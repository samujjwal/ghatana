import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router';
import { dataCloudApi } from '../lib/api/data-cloud-api';
import type { Collection } from '../lib/api/collections';
import { CollectionForm } from '../features/collection/components/CollectionForm';
import type { CollectionFormData } from '../features/collection/components/CollectionForm';
import { toast } from 'sonner';

function EditCollectionPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [collection, setCollection] = useState<Collection | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    const loadCollection = async () => {
      if (!id) return;

      try {
        const response = await dataCloudApi.getCollectionById(id);
        setCollection(response.data);
      } catch (error) {
        console.error('Error loading collection:', error);
        toast.error('Failed to load collection');
        navigate('/collections');
      } finally {
        setIsLoading(false);
      }
    };

    loadCollection();
  }, [id, navigate]);

  const handleSubmit = async (data: CollectionFormData) => {
    if (!id || !collection) return;

    setIsSubmitting(true);
    try {
      const existingFields = collection.schema.fields ?? [];
      const updatedCollection = {
        ...collection,
        ...data,
        updatedAt: new Date().toISOString(),
        schema: {
          ...collection.schema,
          ...data.schema,
          fields: data.schema.fields.map((field) => ({
            ...field,
            // Keep the original field ID if it exists
            id: existingFields.find((f) => f.name === field.name)?.id || `field-${Date.now()}`,
          })),
        },
      };

      // Update via API
      await dataCloudApi.updateCollection(id, {
        name: updatedCollection.name,
        description: updatedCollection.description,
        schema: updatedCollection.schema,
      });

      toast.success('Collection updated successfully');
      navigate(`/collections/${id}`);
    } catch (error) {
      console.error('Error updating collection:', error);
      toast.error('Failed to update collection');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCancel = () => {
    if (id) {
      navigate(`/collections/${id}`);
    } else {
      navigate('/collections');
    }
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-primary-500"></div>
      </div>
    );
  }

  if (!collection) {
    return (
      <div className="text-center py-12">
        <h2 className="text-xl font-medium text-gray-900">Collection not found</h2>
        <p className="mt-2 text-gray-600">The requested collection could not be found.</p>
        <button
          onClick={() => navigate('/collections')}
          className="mt-4 px-4 py-2 bg-primary-600 text-white rounded-md hover:bg-primary-700"
        >
          Back to Collections
        </button>
      </div>
    );
  }

  return (
    <div className="container mx-auto py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold">Edit Collection</h1>
        <p className="text-gray-600">Update the collection details and schema</p>
      </div>

      <div className="bg-white rounded-lg shadow p-6">
        <CollectionForm
          initialData={collection}
          onSubmit={handleSubmit}
          onCancel={handleCancel}
          isSubmitting={isSubmitting}
        />
      </div>
    </div>
  );
}

// Named export to match the import in App.tsx
export { EditCollectionPage };
