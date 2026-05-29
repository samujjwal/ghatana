import type React from 'react';
import { Suspense } from 'react';
import { useTranslation } from '@ghatana/i18n';

import { ProductFamilyControlPlanePage } from '../../pages/product-family/ProductFamilyControlPlanePage';
import { YappcPageShell } from '../../components/layout/YappcPageShell';
import { RouteLoadingSpinner } from '../../components/route/LoadingSpinner';
import { RouteErrorBoundary } from '../../components/route/ErrorBoundary';
import { AdminRouteGate } from './admin/AdminRouteGate';

export function Component(): React.ReactElement {
  return (
    <AdminRouteGate capability="product-family:control-plane" deniedTestId="product-family-unavailable">
      <YappcPageShell
        title="Product Family"
        description="Release readiness and reusable asset control plane across product surfaces."
        testId="product-family-shell"
      >
        <Suspense fallback={<RouteLoadingSpinner />}>
          <ProductFamilyControlPlanePage />
        </Suspense>
      </YappcPageShell>
    </AdminRouteGate>
  );
}

export const ErrorBoundary = RouteErrorBoundary;

export default Component;
