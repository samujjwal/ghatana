import { useState } from "react";
import { useNavigate } from "react-router";
import { toast } from "sonner";
import { emitDataCloudDiagnostic } from "../diagnostics";
import type { CollectionFormData } from "../features/collection/components/CollectionForm";
import { CollectionForm } from "../features/collection/components/CollectionForm";
import { collectionsApi } from "../lib/api/collections";

export function CreateCollectionPage() {
  const navigate = useNavigate();
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (data: CollectionFormData) => {
    setIsSubmitting(true);
    try {
      await collectionsApi.create({
        name: data.name,
        description: data.description,
        schemaType: "entity",
        schema: data.schema,
        // Trust signals wired in UI (TRUST-002) — backend contract update tracked in ARCH-003
      } as Parameters<typeof collectionsApi.create>[0]);

      toast.success("Collection created successfully");
      navigate("/data");
    } catch (error) {
      emitDataCloudDiagnostic(
        "CreateCollectionPage",
        "error",
        "Error creating collection",
        {
          name: data.name,
          error,
        },
      );
      toast.error("Failed to create collection");
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCancel = () => {
    navigate("/data");
  };

  return (
    <section
      className="container mx-auto py-8"
      aria-label="Create new collection"
    >
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
          Create New Collection
        </h1>
        <p className="text-gray-600 dark:text-gray-400">
          Define a new collection and its schema
        </p>
      </div>

      <div className="bg-white dark:bg-gray-800 rounded-lg shadow p-6">
        <CollectionForm
          onSubmit={handleSubmit}
          onCancel={handleCancel}
          isSubmitting={isSubmitting}
        />
      </div>
    </section>
  );
}

export default CreateCollectionPage;
