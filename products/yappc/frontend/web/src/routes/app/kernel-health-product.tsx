/**
 * Kernel Health Product Detail Route
 *
 * Renders the Kernel Health Dashboard in detail view for a specific ProductUnit.
 * The :productUnitId param is forwarded to the page via React Router's useParams.
 * Access is restricted to OWNER/ADMIN roles via capability gating.
 */

import { KernelHealthDashboardPage } from '../../pages/kernel-health/KernelHealthDashboardPage';
import { YappcPageShell } from '../../components/layout/YappcPageShell';
import { KernelHealthCapabilityGate } from './KernelHealthCapabilityGate';

export default function KernelHealthProductRoute() {
  return (
    <KernelHealthCapabilityGate>
      <YappcPageShell
        title="Kernel Health"
        description="Inspect lifecycle truth and health status for a selected product unit."
        testId="kernel-health-product-shell"
      >
        <KernelHealthDashboardPage />
      </YappcPageShell>
    </KernelHealthCapabilityGate>
  );
}
