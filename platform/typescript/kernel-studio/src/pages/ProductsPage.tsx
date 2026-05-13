/**
 * ProductsPage
 * 
 * Lists all products in the registry with their lifecycle status.
 * 
 * @doc.type component
 * @doc.purpose Display list of products
 * @doc.layer platform
 */

import React from 'react';

interface ProductSummary {
  readonly id: string;
  readonly name: string;
  readonly kind: string;
  readonly lifecycleEnabled: boolean;
}

interface ProductsPageProps {
  readonly products: readonly ProductSummary[];
  readonly onSelectProduct?: (productId: string) => void;
}

export function ProductsPage({ products, onSelectProduct }: ProductsPageProps): React.ReactElement {
  return (
    <div>
      <h2 className="text-2xl font-bold mb-6">Products</h2>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {products.map((product) => (
          <div
            key={product.id}
            onClick={() => onSelectProduct?.(product.id)}
            className="p-4 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 cursor-pointer"
          >
            <div className="flex items-center justify-between mb-2">
              <h3 className="text-lg font-semibold">{product.name}</h3>
              <span className={`text-xs px-2 py-1 rounded-full ${product.lifecycleEnabled ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}`}>
                {product.lifecycleEnabled ? 'Lifecycle' : 'Manual'}
              </span>
            </div>
            <div className="text-sm text-gray-600">{product.kind}</div>
          </div>
        ))}
      </div>
    </div>
  );
}
