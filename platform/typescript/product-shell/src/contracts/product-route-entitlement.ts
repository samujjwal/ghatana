/**
 * Kernel-owned typed contract for product route entitlement payloads.
 * Re-exported from the public product-shell type model so generated, hook, and
 * evaluator contracts cannot drift.
 */

export type {
  ProductEntitledAction as ActionEntitlement,
  ProductEntitledCard as CardEntitlement,
  ProductRouteCapability as RouteEntitlement,
  ProductRouteEntitlement,
} from '../types';
