import type React from 'react';
import { useTranslation } from '@ghatana/i18n';

import { ProductFamilyControlPlanePage } from '../../pages/product-family/ProductFamilyControlPlanePage';
import { useCapabilityGate } from '../../hooks/useCapabilityGate';
import { YappcPageShell } from '../../components/layout/YappcPageShell';

function ProductFamilyGate({ children }: { children: React.ReactNode }) {
  const { granted, reason } = useCapabilityGate('product-family:control-plane');
  const { t } = useTranslation('common');

  if (!granted) {
    return (
      <div className="flex min-h-[60vh] items-center justify-center" data-testid="product-family-unavailable">
        <div className="space-y-2 text-center">
          <p className="text-sm text-fg-muted">
            {reason === 'insufficient-role'
              ? t('productFamily.denied.permission')
              : reason === 'unauthenticated'
                ? t('productFamily.denied.login')
                : t('productFamily.denied.unavailable')}
          </p>
        </div>
      </div>
    );
  }

  return <>{children}</>;
}

export function Component(): React.ReactElement {
  return (
    <ProductFamilyGate>
      <YappcPageShell
        title="Product Family"
        description="Release readiness and reusable asset control plane across product surfaces."
        testId="product-family-shell"
      >
        <ProductFamilyControlPlanePage />
      </YappcPageShell>
    </ProductFamilyGate>
  );
}

export default Component;
