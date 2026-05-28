import React from 'react';
import { Button } from '@ghatana/design-system';
import {
  ProductHeaderUserMenu,
  ProductShell,
  ProductShellFooter,
  createProductRoleSelectorConfig,
  useProductEntitlements,
  useProductShellConfig,
  type ProductShellConfig,
} from '@ghatana/product-shell';
import { Outlet, useNavigate } from 'react-router-dom';
import { usePhrAccess } from '../auth/PhrAccessContext';
import { API_BASE_URL } from '../api/requestApi';
import { t } from '../i18n/phrI18n';
import { PHR_ROLE_ORDER, phrRouteContracts } from '../phrRouteContracts';
import { ForbiddenPage } from '../pages/ForbiddenPage';

const roleLabels = {
  patient: t('role.patient.label'),
  caregiver: t('role.caregiver.label'),
  clinician: t('role.clinician.label'),
  admin: t('role.admin.label'),
  fchv: 'FCHV',
} satisfies NonNullable<ProductShellConfig['roleLabels']>;

const roleDescriptions = {
  patient: t('role.patient.description'),
  caregiver: t('role.caregiver.description'),
  clinician: t('role.clinician.description'),
  admin: t('role.admin.description'),
  fchv: 'Community Health Volunteer',
} satisfies NonNullable<ProductShellConfig['roleDescriptions']>;

const availableRoles = ['patient', 'caregiver', 'clinician', 'admin', 'fchv'] as const;

const sidebarFooter = (
  <ProductShellFooter description={t('shell.sidebarFooter')} />
);

const roleSelectorConfig = createProductRoleSelectorConfig({
  roleOrder: PHR_ROLE_ORDER,
  roleLabels,
  roleDescriptions,
  roleSelectorTitle: t('shell.roleSelector.title'),
  roleSelectorLabel: t('shell.roleSelector.label'),
  roleSelectorDisclosureNote: t('shell.roleSelector.note'),
  availableRoles,
});

function labelForRole(role: string): string {
  return Object.prototype.hasOwnProperty.call(roleLabels, role)
    ? roleLabels[role as keyof typeof roleLabels]
    : t('shell.signedIn');
}

export function PhrProductShell(): React.ReactElement {
  const { role, setRole, tenantId, principalId } = usePhrAccess();
  const navigate = useNavigate();
  const correlationId = React.useMemo(() => crypto.randomUUID(), []);
  const tier = role === 'clinician' || role === 'admin' ? 'clinical' : 'core';
  const entitlementEndpoint = React.useMemo(() => {
    const url = new URL(`${API_BASE_URL}/route-entitlements`);
    url.searchParams.set('tenantId', tenantId);
    url.searchParams.set('principalId', principalId);
    url.searchParams.set('role', role);
    url.searchParams.set('tier', tier);
    return url.toString();
  }, [principalId, role, tenantId, tier]);
  const entitlementRequestInit = React.useMemo<RequestInit>(
    () => ({
      headers: {
        Accept: 'application/json',
        'X-Tenant-Id': tenantId,
        'X-Principal-Id': principalId,
        'X-Role': role,
        'X-Persona': role,
        'X-Tier': tier,
        'X-Correlation-ID': correlationId,
      },
    }),
    [role, tenantId, principalId, tier, correlationId],
  );
  const entitlements = useProductEntitlements({
    endpoint: entitlementEndpoint,
    fallbackRoutes: phrRouteContracts,
    requestInit: entitlementRequestInit,
  });
  const canReviewEmergencyAccess = entitlements.entitlement?.actions?.some(
    (action) => action.id === 'break-glass-review',
  ) ?? false;
  const headerActions = canReviewEmergencyAccess
    ? (
        <div className="flex items-center gap-3">
          <Button onClick={() => navigate('/emergency')}>{t('shell.emergencyReview')}</Button>
          <ProductHeaderUserMenu fallbackLabel={labelForRole(role)} />
        </div>
      )
    : <ProductHeaderUserMenu fallbackLabel={labelForRole(role)} />;

  const config = useProductShellConfig({
    productName: 'PHR Nepal',
    currentRole: role,
    ...roleSelectorConfig,
    onRoleChange: (nextRole: string) => {
      setRole(nextRole as typeof role);
    },
    routes: entitlements.routes,
    headerActions,
    sidebarFooter,
  });

  if (!tenantId || !principalId) {
    return <ForbiddenPage />;
  }

  return (
    <ProductShell config={config} contentClassName="pt-20 p-6">
      <Outlet />
    </ProductShell>
  );
}
