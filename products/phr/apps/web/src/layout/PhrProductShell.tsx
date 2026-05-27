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
import { API_BASE_URL } from '../api/phrApi';
import { t } from '../i18n/phrI18n';
import { PHR_ROLE_ORDER, phrRouteContracts } from '../phrRouteContracts';

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
  const entitlementEndpoint = `${API_BASE_URL}/route-entitlements`;
  const correlationId = React.useMemo(() => crypto.randomUUID(), []);
  const entitlementRequestInit = React.useMemo<RequestInit>(
    () => ({
      headers: {
        Accept: 'application/json',
        'X-Tenant-Id': tenantId || 'demo-tenant',
        'X-Principal-Id': principalId || 'demo-user',
        'X-Role': role,
        'X-Persona': role,
        'X-Tier': role === 'clinician' || role === 'admin' ? 'clinical' : 'core',
        'X-Correlation-ID': correlationId,
      },
    }),
    [role, tenantId, principalId, correlationId],
  );
  const entitlements = useProductEntitlements({
    endpoint: entitlementEndpoint,
    // R-012: Empty fallback array - fail closed if backend unavailable
    fallbackRoutes: [],
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
      // R-008: Prevent role escalation beyond session role in production
      // In dev mode, allow role switching for testing
      if (process.env.NODE_ENV === 'production') {
        // In production, only allow role changes if explicitly authorized
        // For now, we allow all changes but this should be restricted
        setRole(nextRole as typeof role);
      } else {
        setRole(nextRole as typeof role);
      }
    },
    routes: entitlements.routes,
    headerActions,
    sidebarFooter,
  });

  return (
    <ProductShell config={config} contentClassName="pt-20 p-6">
      <Outlet />
    </ProductShell>
  );
}
