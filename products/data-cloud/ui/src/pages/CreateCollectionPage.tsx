import React, { useState } from 'react';
import { useNavigate } from 'react-router';
import { dataCloudApi } from '../lib/api/data-cloud-api';
import { CollectionForm } from '../features/collection/components/CollectionForm';
import type { CollectionFormData } from '../features/collection/components/CollectionForm';
import { toast } from 'sonner';

export function CreateCollectionPage() {
  const navigate = useNavigate();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (data: CollectionFormData) => {
    setIsSubmitting(true);
    try {
      // Create collection via API
      await dataCloudApi.createCollection({
        name: data.name,
        description: data.description,
        schemaType: 'entity',
        schema: data.schema,
      });

      toast.success('Collection created successfully');
      navigate('/collections');
    } catch (error) {
      console.error('Error creating collection:', error);
      toast.error('Failed to create collection');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCancel = () => {
    navigate('/collections');
  };

  return (
    <div className="container mx-auto py-8">
      <div className="mb-6">
        <h1 className="text-2xl font-bold">Create New Collection</h1>
        <p className="text-gray-600">Define a new collection and its schema</p>
      </div>

      <div className="bg-white rounded-lg shadow p-6">
        <CollectionForm
          onSubmit={handleSubmit}
          onCancel={handleCancel}
          isSubmitting={isSubmitting}
        />
      </div>
    </div>
  );
}

export default CreateCollectionPage;
