/**
 * Kernel Health Dashboard Route
 *
 * Renders the Kernel Health Dashboard in list view (no specific ProductUnit selected).
 * Access is restricted to OWNER/ADMIN roles via capability gating.
 */

import { KernelHealthDashboardPage } from '../../pages/kernel-health/KernelHealthDashboardPage';
import { YappcPageShell } from '../../components/layout/YappcPageShell';
import { KernelHealthCapabilityGate } from './KernelHealthCapabilityGate';

export default function KernelHealthRoute() {
  return (
    <KernelHealthCapabilityGate>
      <YappcPageShell
        title="Kernel Health"
        description="Track product-unit lifecycle status, gate health, and recovery actions."
        testId="kernel-health-shell"
      >
        <KernelHealthDashboardPage />
      </YappcPageShell>
    </KernelHealthCapabilityGate>
  );
}
