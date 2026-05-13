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
import { PHR_ROLE_ORDER, phrRouteContracts } from '../routeManifest';

const roleLabels = {
  patient: 'Patient',
  caregiver: 'Caregiver',
  clinician: 'Clinician',
  admin: 'Admin',
} satisfies NonNullable<ProductShellConfig['roleLabels']>;

const roleDescriptions = {
  patient: 'Self-service view with personal health workflows.',
  caregiver: 'Delegated support view for family and after-hours review.',
  clinician: 'Clinical operations view with emergency workflows.',
  admin: 'Administrative and governance view.',
} satisfies NonNullable<ProductShellConfig['roleDescriptions']>;

const availableRoles = ['patient', 'caregiver', 'clinician', 'admin'] as const;

const sidebarFooter = (
  <ProductShellFooter description="Navigation, role visibility, and layout come from shared product-shell contracts." />
);

const roleSelectorConfig = createProductRoleSelectorConfig({
  roleOrder: PHR_ROLE_ORDER,
  roleLabels,
  roleDescriptions,
  roleSelectorTitle: 'Persona',
  roleSelectorLabel: 'Persona visibility menu',
  roleSelectorDisclosureNote:
    'Navigation visibility comes from route metadata. Backend and route guards still enforce access.',
  availableRoles,
});

function labelForRole(role: string): string {
  return Object.prototype.hasOwnProperty.call(roleLabels, role)
    ? roleLabels[role as keyof typeof roleLabels]
    : 'Signed in';
}

export function PhrProductShell(): React.ReactElement {
  const { role, setRole } = usePhrAccess();
  const navigate = useNavigate();
  const entitlementEndpoint = `${API_BASE_URL}/route-entitlements?role=${encodeURIComponent(role)}`;
  const entitlementRequestInit = React.useMemo<RequestInit>(
    () => ({
      headers: {
        Accept: 'application/json',
        'X-Role': role,
        'X-Persona': role,
        'X-Tier': role === 'clinician' || role === 'admin' ? 'clinical' : 'core',
      },
    }),
    [role],
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
          <Button onClick={() => navigate('/emergency')}>Emergency Access Review</Button>
          <ProductHeaderUserMenu fallbackLabel={labelForRole(role)} />
        </div>
      )
    : <ProductHeaderUserMenu fallbackLabel={labelForRole(role)} />;

  const config = useProductShellConfig({
    productName: 'PHR Nepal',
    currentRole: role,
    ...roleSelectorConfig,
    onRoleChange: (nextRole: string) => setRole(nextRole as typeof role),
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
