/**
 * Capability registry compatibility shim.
 *
 * Canonical implementation lives in `surfaces.service.ts`.
 * Keep this module for backward compatibility while first-party imports
 * continue migrating to surface-first naming.
 *
 * @doc.type service
 * @doc.purpose Backward-compatible export surface for capability registry consumers
 * @doc.layer frontend
 */

export type {
  CapabilityRegistrySnapshot,
  CapabilitySignal,
  CapabilityStatus,
} from './surfaces.service';

export {
  fetchCapabilityRegistry,
  useCapabilityRegistry,
  getCapabilitySignal,
} from './surfaces.service';