import React from 'react';
import { Button } from '@ghatana/design-system';
import { ProductShell, type ProductShellConfig } from '@ghatana/product-shell';
import { usePhrAccess } from '../auth/PhrAccessContext';
import { phrRouteManifest } from '../routeManifest';

export function PhrProductShell(): React.ReactElement {
  const { role, setRole } = usePhrAccess();

  const config: ProductShellConfig = {
    productName: 'PHR Nepal',
    currentRole: role,
    roleOrder: {
      patient: 0,
      caregiver: 1,
      clinician: 2,
      admin: 3,
    },
    roleLabels: {
      patient: 'Patient',
      caregiver: 'Caregiver',
      clinician: 'Clinician',
      admin: 'Admin',
    },
    roleDescriptions: {
      patient: 'Self-service view with personal health workflows.',
      caregiver: 'Delegated support view for family and after-hours review.',
      clinician: 'Clinical operations view with emergency workflows.',
      admin: 'Administrative and governance view.',
    },
    roleSelectorTitle: 'Persona',
    roleSelectorLabel: 'Persona visibility menu',
    roleSelectorDisclosureNote:
      'Navigation visibility comes from route metadata. Backend and route guards still enforce access.',
    availableRoles: ['patient', 'caregiver', 'clinician', 'admin'],
    onRoleChange: (nextRole: string) => setRole(nextRole as typeof role),
    routes: phrRouteManifest,
    headerActions: <Button>Emergency Access Review</Button>,
    sidebarFooter: (
      <div className="rounded-2xl bg-slate-900/80 p-4 text-sm text-slate-100">
        <p className="eyebrow">Kernel shell</p>
        <p className="m-0">Navigation, role visibility, and layout come from shared product-shell contracts.</p>
      </div>
    ),
  };

  return <ProductShell config={config} contentClassName="pt-20 p-6" />;
}
