import React from 'react';
import { Button } from '@ghatana/design-system';
import {
  ProductHeaderUserMenu,
  ProductShell,
  ProductShellFooter,
  createProductRoleSelectorConfig,
  useProductEntitlements,
  useProductShellConfig,
  type ProductRouteCapability,
  type ProductShellConfig,
} from '@ghatana/product-shell';
import { Outlet, useNavigate } from 'react-router-dom';
import { usePhrAccess } from '../auth/PhrAccessContext';
import { API_BASE_URL } from '../api/requestApi';
import { t } from '../i18n/phrI18n';
import { PHR_ROLE_ORDER, phrRouteContracts, getRouteLabelI18nKey, getRouteDescriptionI18nKey, type PhrRouteContract } from '../phrRouteContracts';
import { ForbiddenPage } from '../pages/ForbiddenPage';
import { SafeError } from '../components/SafeError';
import { LoadingState } from '../components/PageStates';

const roleLabels = {
  patient: t('role.patient.label'),
  caregiver: t('role.caregiver.label'),
  clinician: t('role.clinician.label'),
  admin: t('role.admin.label'),
  fchv: t('role.fchv.label'),
} satisfies NonNullable<ProductShellConfig['roleLabels']>;

const roleDescriptions = {
  patient: t('role.patient.description'),
  caregiver: t('role.caregiver.description'),
  clinician: t('role.clinician.description'),
  admin: t('role.admin.description'),
  fchv: t('role.fchv.description'),
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

function isPhrRouteContract(route: ProductRouteCapability): route is PhrRouteContract {
  const candidate = route as Partial<PhrRouteContract>;
  return typeof candidate.i18nKey === 'string' && typeof candidate.descriptionI18nKey === 'string';
}

function localizeRoute(route: ProductRouteCapability): ProductRouteCapability {
  if (!isPhrRouteContract(route)) {
    return route;
  }
  return {
    ...route,
    label: t(getRouteLabelI18nKey(route)),
    description: t(getRouteDescriptionI18nKey(route)),
  };
}

export function PhrProductShell(): React.ReactElement {
  const { role } = usePhrAccess();
  const navigate = useNavigate();
  const correlationId = React.useMemo(() => crypto.randomUUID(), []);
  const entitlementEndpoint = React.useMemo(() => {
    // Identity resolved server-side from Kernel-authenticated session
    // No identity query params - only safe headers
    return `${API_BASE_URL}/route-entitlements`;
  }, []);
  const entitlementRequestInit = React.useMemo<RequestInit>(
    () => ({
      headers: {
        Accept: 'application/json',
        'X-Correlation-ID': correlationId,
      },
      credentials: 'include',
    }),
    [correlationId],
  );
  const entitlements = useProductEntitlements({
    endpoint: entitlementEndpoint,
    // ENT-05: No fallback routes in production - fail closed if backend entitlements unavailable
    fallbackRoutes: process.env.NODE_ENV === 'production' ? [] : phrRouteContracts,
    requestInit: entitlementRequestInit,
  });

  const localizedRoutes = React.useMemo(() => {
    return entitlements.routes.map(localizeRoute);
  }, [entitlements.routes]);
  const emergencyReviewAction = entitlements.entitlement?.actions?.find((action) => action.id === 'break-glass-review');
  const canReviewEmergencyAccess = Boolean(emergencyReviewAction);
  const emergencyReviewRoute = emergencyReviewAction?.routePath ?? '/emergency/reviews';
  const headerActions = canReviewEmergencyAccess
    ? (
        <div className="flex items-center gap-3">
          <Button onClick={() => navigate(emergencyReviewRoute)}>{t('shell.emergencyReview')}</Button>
          <ProductHeaderUserMenu fallbackLabel={labelForRole(role)} />
        </div>
      )
    : <ProductHeaderUserMenu fallbackLabel={labelForRole(role)} />;

  const config = useProductShellConfig({
    productName: t('app.productName'),
    currentRole: role,
    // Role selector disabled - identity is read-only from Kernel session
    ...roleSelectorConfig,
    onRoleChange: undefined,
    routes: localizedRoutes,
    headerActions,
    sidebarFooter,
  });

  const shellContent = entitlements.status === 'loading' || entitlements.status === 'idle'
    ? <LoadingState message={t('shell.entitlements.loading')} />
    : entitlements.status === 'denied'
      ? <ForbiddenPage />
      : entitlements.status === 'error'
        ? <SafeError title={t('shell.entitlements.errorTitle')} message={t('shell.entitlements.error')} onDismiss={entitlements.refresh} />
        : <Outlet />;

  return (
    <ProductShell
      config={config}
      contentClassName="pt-20 px-4 py-8 sm:px-6 lg:px-8"
      mainContentId="main-content"
      mainContentTabIndex={-1}
      mainContentRole="main"
    >
      {shellContent}
    </ProductShell>
  );
}
