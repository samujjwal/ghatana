/**
 * ProductDetailPage
 * 
 * Displays detailed information about a product including lifecycle, artifacts, deployments, and conformance.
 * 
 * @doc.type component
 * @doc.purpose Display product details
 * @doc.layer platform
 */

import React from 'react';

interface ProductDetail {
  readonly id: string;
  readonly name: string;
  readonly kind: string;
  readonly lifecycleEnabled: boolean;
  readonly description?: string;
}

interface ProductDetailPageProps {
  readonly product: ProductDetail;
}

export function ProductDetailPage({ product }: ProductDetailPageProps): React.ReactElement {
  return (
    <div>
      <div className="mb-6">
        <h2 className="text-2xl font-bold">{product.name}</h2>
        <div className="flex items-center gap-2 mt-2">
          <span className="text-sm text-gray-600">{product.kind}</span>
          <span className={`text-xs px-2 py-1 rounded-full ${product.lifecycleEnabled ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}`}>
            {product.lifecycleEnabled ? 'Lifecycle Enabled' : 'Manual'}
          </span>
        </div>
        {product.description && (
          <p className="text-gray-600 mt-2">{product.description}</p>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="p-4 bg-white border border-gray-200 rounded-lg">
          <h3 className="text-lg font-semibold mb-2">Lifecycle</h3>
          <p className="text-sm text-gray-600">
            {product.lifecycleEnabled ? 'Lifecycle management is enabled for this product.' : 'Lifecycle management is not enabled.'}
          </p>
        </div>
        <div className="p-4 bg-white border border-gray-200 rounded-lg">
          <h3 className="text-lg font-semibold mb-2">Conformance</h3>
          <p className="text-sm text-gray-600">View conformance status and checks.</p>
        </div>
      </div>
    </div>
  );
}
