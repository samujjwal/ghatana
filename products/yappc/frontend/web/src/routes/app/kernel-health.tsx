/**
 * Kernel Health Dashboard Route
 *
 * Renders the Kernel Health Dashboard in list view (no specific ProductUnit selected).
 * Access is restricted to OWNER/ADMIN roles via capability gating.
 */

import { KernelHealthDashboardPage } from '../../pages/kernel-health/KernelHealthDashboardPage';
import { KernelHealthCapabilityGate } from './KernelHealthCapabilityGate';

export default function KernelHealthRoute() {
  return (
    <KernelHealthCapabilityGate>
      <KernelHealthDashboardPage />
    </KernelHealthCapabilityGate>
  );
}
