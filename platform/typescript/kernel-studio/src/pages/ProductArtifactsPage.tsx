/**
 * ProductArtifactsPage
 * 
 * Displays artifacts for a product version.
 * 
 * @doc.type component
 * @doc.purpose Display product artifacts
 * @doc.layer platform
 */

import React from 'react';

interface ProductArtifactsPageProps {
  readonly productId: string;
}

export function ProductArtifactsPage({ productId }: ProductArtifactsPageProps): React.ReactElement {
  return (
    <div>
      <h2 className="text-2xl font-bold mb-6">Artifacts: {productId}</h2>
      <div className="p-4 bg-white border border-gray-200 rounded-lg">
        <h3 className="text-lg font-semibold mb-2">Artifact List</h3>
        <p className="text-sm text-gray-600">View and download product artifacts.</p>
      </div>
    </div>
  );
}
