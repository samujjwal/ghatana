import React from 'react';
import { Button } from '@ghatana/design-system';
import {
  ProductShell,
  useStableProductShellConfig,
  type ProductShellConfig,
} from '@ghatana/product-shell';
import { Outlet } from 'react-router-dom';
import { usePhrAccess } from '../auth/PhrAccessContext';
import { PHR_ROLE_ORDER, phrRouteManifest } from '../routeManifest';

const roleLabels: ProductShellConfig['roleLabels'] = {
  patient: 'Patient',
  caregiver: 'Caregiver',
  clinician: 'Clinician',
  admin: 'Admin',
};

const roleDescriptions: ProductShellConfig['roleDescriptions'] = {
  patient: 'Self-service view with personal health workflows.',
  caregiver: 'Delegated support view for family and after-hours review.',
  clinician: 'Clinical operations view with emergency workflows.',
  admin: 'Administrative and governance view.',
};

const availableRoles = ['patient', 'caregiver', 'clinician', 'admin'] as const;

const sidebarFooter = (
  <div className="rounded-lg bg-slate-900/80 p-4 text-sm text-slate-100">
    <p className="eyebrow">Kernel shell</p>
    <p className="m-0">Navigation, role visibility, and layout come from shared product-shell contracts.</p>
  </div>
);

export function PhrProductShell(): React.ReactElement {
  const { role, setRole } = usePhrAccess();

  const config = useStableProductShellConfig((): ProductShellConfig => ({
    productName: 'PHR Nepal',
    currentRole: role,
    roleOrder: PHR_ROLE_ORDER,
    roleLabels,
    roleDescriptions,
    roleSelectorTitle: 'Persona',
    roleSelectorLabel: 'Persona visibility menu',
    roleSelectorDisclosureNote:
      'Navigation visibility comes from route metadata. Backend and route guards still enforce access.',
    availableRoles,
    onRoleChange: (nextRole: string) => setRole(nextRole as typeof role),
    routes: phrRouteManifest,
    headerActions: <Button>Emergency Access Review</Button>,
    sidebarFooter,
  }), [role, setRole]);

  return (
    <ProductShell config={config} contentClassName="pt-20 p-6">
      <Outlet />
    </ProductShell>
  );
}
