/**
 * Simulation Runtime Service - Module exports.
 *
 * @doc.type module
 * @doc.purpose Barrel exports for sim-runtime service
 * @doc.layer product
 * @doc.pattern Module
 */

// Core service
export { SimulationRuntimeService, createRuntimeService } from './service';
export type { SessionAnalytics } from './service';

// Kernel registry
export { KernelRegistry } from './kernel-registry';
export type { KernelRegistryEntry, KernelFactory } from './kernel-registry';

// Individual kernels
export { createDiscreteKernel } from './discrete-kernel';
export { createPhysicsKernel } from './physics-kernel';
export { createSystemDynamicsKernel } from './system-dynamics-kernel';
export { createChemistryKernel } from './chemistry-kernel';
export { createBiologyKernel } from './biology-kernel';
export { createMedicineKernel } from './medicine-kernel';

// Easing utilities
export { getEasingFunction } from './easing';
